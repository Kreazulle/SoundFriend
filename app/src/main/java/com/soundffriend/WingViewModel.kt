package com.soundffriend

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.NetworkInterface

data class WingMixer(val name: String, val ip: String)

enum class NotificationType { ALERT, INFO }
data class Notification(val text: String, val type: NotificationType)

class WingViewModel : ViewModel() {
    private val _discoveredMixers = MutableStateFlow<List<WingMixer>>(emptyList())
    val discoveredMixers = _discoveredMixers.asStateFlow()

    private val _selectedMixer = MutableStateFlow<WingMixer?>(null)
    val selectedMixer = _selectedMixer.asStateFlow()

    private val _bpm = MutableStateFlow(120f)
    val bpm = _bpm.asStateFlow()

    private val _notification = MutableStateFlow<Notification?>(null)
    val notification = _notification.asStateFlow()

    fun dismissAlert() {
        _notification.value = null
    }



    private val _isScanning = MutableStateFlow(value = false)
    val isScanning = _isScanning.asStateFlow()

    private var discoveryJob: kotlinx.coroutines.Job? = null

    fun stopDiscovery() {
        _isScanning.value = false
        discoveryJob?.cancel()
    }

    fun startDiscovery() {
        if (_isScanning.value) return
        _isScanning.value = true
        _discoveredMixers.value = emptyList()

        discoveryJob?.cancel()
        discoveryJob = viewModelScope.launch(Dispatchers.IO) {
            while (isActive && _isScanning.value) {
                performDiscoveryCycle()
                if (_discoveredMixers.value.isNotEmpty()) {
                    _isScanning.value = false
                    break
                }
                delay(2000)
            }
        }
    }

    private suspend fun performDiscoveryCycle() {
        val socket = DatagramSocket()
        socket.broadcast = true
        socket.soTimeout = 1000
        
        val messageNative = "WING?".toByteArray()
        val messageNativeV3 = "WING?72".toByteArray()
        val messageOsc = "/xinfo\u0000\u0000".toByteArray()
        
        val broadcastAddresses = getBroadcastAddresses().toMutableList()
        try {
            broadcastAddresses.add(InetAddress.getByName("255.255.255.255"))
        } catch (_: Exception) {}
        
        for (address in broadcastAddresses) {
            try {
                socket.send(DatagramPacket(messageNative, messageNative.size, address, 2222))
                socket.send(DatagramPacket(messageNativeV3, messageNativeV3.size, address, 2222))
                socket.send(DatagramPacket(messageOsc, messageOsc.size, address, 2223))
                socket.send(DatagramPacket(messageOsc, messageOsc.size, address, 10023))
            } catch (_: Exception) {}
        }

        val receiveData = ByteArray(2048)
        val receivePacket = DatagramPacket(receiveData, receiveData.size)
        val startTime = System.currentTimeMillis()
        while ((System.currentTimeMillis() - startTime) < 2000) {
            try {
                socket.receive(receivePacket)
                val response = String(receivePacket.data, 0, receivePacket.length)
                val ip = receivePacket.address?.hostAddress ?: "Unknown"
                
                if (response.contains("WING,")) {
                    val startIndex = response.indexOf("WING,")
                    val parts = response.substring(startIndex).split(",")
                    val name = if (parts.size >= 3) "${parts[2]} @ $ip" else "WING @ $ip"
                    addMixer(name, ip)
                } else if (response.contains("/xinfo")) {
                    val parts = response.split(Regex("[^a-zA-Z0-9 _-]"))
                    val consoleName = parts.firstOrNull { (it.length > 2) && (it != "xinfo") } ?: "Wing"
                    addMixer("$consoleName @ $ip", ip)
                }
            } catch (_: Exception) { }
        }
        socket.close()
        
        if (_discoveredMixers.value.isEmpty()) {
            tryTcpScan()
        }
    }

    private suspend fun tryTcpScan() {
        val currentIp = _deviceIp.value
        if ((currentIp == "Unknown") || !currentIp.contains(".")) return
        
        val prefix = currentIp.substringBeforeLast(".")
        val jobs = mutableListOf<kotlinx.coroutines.Job>()
        
        for (i in 1..254) {
            val targetIp = "$prefix.$i"
            if (targetIp == currentIp) continue
            
            val job = viewModelScope.launch(Dispatchers.IO) {
                try {
                    val socket = java.net.Socket()
                    socket.connect(java.net.InetSocketAddress(targetIp, 2222), 150)
                    socket.getOutputStream().write("WING?".toByteArray())
                    val buffer = ByteArray(1024)
                    val read = socket.getInputStream().read(buffer)
                    if (read > 0) {
                        val response = String(buffer, 0, read)
                        if (response.contains("WING,")) {
                            addMixer("WING (TCP) @ $targetIp", targetIp)
                        }
                    }
                    socket.close()
                } catch (_: Exception) {}
            }
            jobs.add(job)
            if ((i % 20) == 0) delay(10) // Throttle a bit
        }
        kotlinx.coroutines.joinAll(*jobs.toTypedArray())
    }

    private fun addMixer(name: String, ip: String) {
        if (!_discoveredMixers.value.any { it.ip == ip }) {
            _discoveredMixers.value += WingMixer(name = name, ip = ip)
        }
    }

    private fun getBroadcastAddresses(): List<InetAddress> {
        val addresses = mutableListOf<InetAddress>()
        val interfaces = NetworkInterface.getNetworkInterfaces()
        while (interfaces.hasMoreElements()) {
            val networkInterface = interfaces.nextElement()
            if (networkInterface.isLoopback || !networkInterface.isUp) continue
            for (interfaceAddress in networkInterface.interfaceAddresses) {
                val broadcast = interfaceAddress.broadcast
                if (broadcast != null) {
                    addresses.add(broadcast)
                }
            }
        }
        return addresses
    }

    fun selectMixer(mixer: WingMixer?) {
        _selectedMixer.value = mixer
        if ((mixer != null) && (mixer.ip != "0.0.0.0")) {
            // Start listening for OSC messages from this mixer
            listenToMixer(mixer)
        }
    }

    private var heartbeatJob: kotlinx.coroutines.Job? = null

    private fun listenToMixer(mixer: WingMixer) {
        heartbeatJob?.cancel()
        heartbeatJob = viewModelScope.launch(Dispatchers.IO) {
            val socket = DatagramSocket()
            val message = "/xremote\u0000\u0000\u0000\u0000".toByteArray()
            val address = InetAddress.getByName(mixer.ip)
            
            // Start a separate job to listen for responses
            val listenerJob = launch {
                val receiveData = ByteArray(1024)
                val receivePacket = DatagramPacket(receiveData, receiveData.size)
                while (isActive) {
                    try {
                        socket.receive(receivePacket)
                        val response = String(receivePacket.data, 0, receivePacket.length)
                        
                        // Look for BPM updates: /-config/tempo or /config/tempo followed by float bytes
                        if (response.contains("/config/tempo")) {
                            val path = if (response.contains("/-config/tempo")) "/-config/tempo" else "/config/tempo"
                            val startIndex = response.indexOf(path) + 16 // Path + nulls + ,f + nulls
                            if (receivePacket.length >= (startIndex + 4)) {
                                val bpmBytes = receivePacket.data.sliceArray(startIndex until (startIndex + 4))
                                val receivedBpm = byteArrayToFloat(bpmBytes)
                                if (receivedBpm in (20f..300f)) {
                                    _bpm.value = receivedBpm
                                }
                            }
                        }
                    } catch (_: Exception) {
                        // Timeout or socket closed
                    }
                }
            }

            while (isActive) {
                try {
                    val packet10023 = DatagramPacket(message, message.size, address, 10023)
                    val packet2223 = DatagramPacket(message, message.size, address, 2223)
                    socket.send(packet10023)
                    socket.send(packet2223)
                    // WING requires /xremote every 10 seconds to keep connection alive
                    delay(9000) 
                } catch (e: Exception) {
                    e.printStackTrace()
                    delay(5000)
                }
            }
            listenerJob.cancel()
            socket.close()
        }
    }

    private fun byteArrayToFloat(bytes: ByteArray): Float {
        val intBits = (bytes[0].toInt() and 0xFF shl 24) or
                     (bytes[1].toInt() and 0xFF shl 16) or
                     (bytes[2].toInt() and 0xFF shl 8) or
                     (bytes[3].toInt() and 0xFF)
        return java.lang.Float.intBitsToFloat(intBits)
    }

    private val tapTimestamps = mutableListOf<Long>()

    fun tapTempo() {
        val now = System.currentTimeMillis()
        tapTimestamps.add(now)
        if (tapTimestamps.size > 2) {
            tapTimestamps.removeAt(0)
        }
        
        if (tapTimestamps.size == 2) {
            val interval = tapTimestamps[1] - tapTimestamps[0]
            
            // Filter out intervals that are too long (e.g. > 2 seconds / < 30 BPM)
            if (interval < 2000) {
                _bpm.value = (60000f / interval)
                
                // Send BPM to Wing if connected
                sendBpmToWing(_bpm.value)
            } else {
                // If interval was too long, treat this tap as a new first tap
                tapTimestamps.removeAt(0)
            }
        }
    }

    private fun sendBpmToWing(bpm: Float) {
        val mixer = _selectedMixer.value ?: return
        if (mixer.ip == "0.0.0.0") return // Skip network OSC in "No Mixer" mode
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val socket = DatagramSocket()
                val address = InetAddress.getByName(mixer.ip)
                val bpmBytes = floatToByteArray(bpm)
                
                // WING uses /config/tempo for the master BPM
                val msgWing = "/config/tempo\u0000\u0000\u0000,f\u0000\u0000".toByteArray() + bpmBytes
                // Legacy X32 uses /-config/tempo
                val msgX32 = "/-config/tempo\u0000\u0000\u0000,f\u0000\u0000".toByteArray() + bpmBytes
                
                val ports = listOf(2223, 10023)
                for (port in ports) {
                    socket.send(DatagramPacket(msgWing, msgWing.size, address, port))
                    socket.send(DatagramPacket(msgX32, msgX32.size, address, port))
                }
                socket.close()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun floatToByteArray(value: Float): ByteArray {
        val intBits = java.lang.Float.floatToIntBits(value)
        return byteArrayOf(
            (intBits shr 24).toByte(),
            (intBits shr 16).toByte(),
            (intBits shr 8).toByte(),
            intBits.toByte(),
        )
    }

    private var alertSocket: DatagramSocket? = null
    private var oscAlertSocket: DatagramSocket? = null

    private fun listenForAlerts() {
        // UDP Alert Listener (Port 5005)
        viewModelScope.launch(Dispatchers.IO) {
            try {
                alertSocket = DatagramSocket(5005)
                val buffer = ByteArray(1024)
                while (true) {
                    val packet = DatagramPacket(buffer, buffer.size)
                    alertSocket?.receive(packet)
                    val message = String(packet.data, 0, packet.length).trim()
                    
                    triggerAlertIfMatch(message, NotificationType.ALERT)
                }
            } catch (_: Exception) {
                // Socket might be closed
            }
        }

        // OSC Alert Listener (Port 5006)
        viewModelScope.launch(Dispatchers.IO) {
            try {
                oscAlertSocket = DatagramSocket(5006)
                val buffer = ByteArray(2048)
                while (true) {
                    val packet = DatagramPacket(buffer, buffer.size)
                    oscAlertSocket?.receive(packet)
                    
                    val response = String(packet.data, 0, packet.length)
                    
                    val type = when {
                        response.startsWith("/SoundFriend/alerts") -> NotificationType.ALERT
                        response.startsWith("/SoundFriend/messages") -> NotificationType.INFO
                        else -> null
                    }

                    if (type != null) {
                        val typeTagIndex = response.indexOf(",s")
                        if (typeTagIndex != -1) {
                            // Extract string after the type tag
                            // Simple extraction for the protocol demo
                            val message = response.substring(typeTagIndex + 3).trim { it <= ' ' }
                            triggerAlertIfMatch(message, type)
                        }
                    }
                }
            } catch (_: Exception) {
                // Socket might be closed
            }
        }
    }

    private fun triggerAlertIfMatch(message: String, type: NotificationType) {
        if (message.isNotEmpty()) {
            _notification.value = Notification(message, type)
        }
    }

    override fun onCleared() {
        super.onCleared()
        alertSocket?.close()
        oscAlertSocket?.close()
    }

    private val _deviceIp = MutableStateFlow("Unknown")
    val deviceIp = _deviceIp.asStateFlow()

    init {
        listenForAlerts()
        updateDeviceIp()
    }

    private fun updateDeviceIp() {
        viewModelScope.launch(Dispatchers.IO) {
            val interfaces = NetworkInterface.getNetworkInterfaces()
            while (interfaces.hasMoreElements()) {
                val networkInterface = interfaces.nextElement()
                if (networkInterface.isLoopback || !networkInterface.isUp) continue
                for (addr in networkInterface.inetAddresses) {
                    val host = addr.hostAddress
                    if (!addr.isLoopbackAddress && (host != null) && host.contains(".")) {
                        _deviceIp.value = host
                        return@launch
                    }
                }
            }
        }
    }

}
