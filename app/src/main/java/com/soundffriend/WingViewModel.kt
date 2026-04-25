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

class WingViewModel : ViewModel() {
    private val _discoveredMixers = MutableStateFlow<List<WingMixer>>(emptyList())
    val discoveredMixers = _discoveredMixers.asStateFlow()

    private val _selectedMixer = MutableStateFlow<WingMixer?>(null)
    val selectedMixer = _selectedMixer.asStateFlow()

    private val _bpm = MutableStateFlow(120f)
    val bpm = _bpm.asStateFlow()

    private val _alertMessage = MutableStateFlow<String?>(null)
    val alertMessage = _alertMessage.asStateFlow()

    fun dismissAlert() {
        _alertMessage.value = null
    }



    fun startDiscovery() {
        viewModelScope.launch(Dispatchers.IO) {
            val socket = DatagramSocket()
            socket.broadcast = true
            
            val message = "/xinfo\u0000\u0000".toByteArray() // Basic OSC /xinfo
            
            // Try to find broadcast addresses
            val broadcastAddresses = getBroadcastAddresses()
            
            for (address in broadcastAddresses) {
                try {
                    val packet = DatagramPacket(message, message.size, address, 10023)
                    socket.send(packet)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }

            // Listen for responses
            val receiveData = ByteArray(1024)
            val receivePacket = DatagramPacket(receiveData, receiveData.size)
            
            socket.soTimeout = 2000 // 2 seconds timeout for discovery
            val startTime = System.currentTimeMillis()
            while (System.currentTimeMillis() - startTime < 3000) {
                try {
                    socket.receive(receivePacket)
                    val response = String(receivePacket.data, 0, receivePacket.length)
                    val ip = receivePacket.address?.hostAddress ?: "Unknown"
                    
                    // Simple parsing: if response contains /xinfo, it's a Wing/X32
                    if (response.contains("/xinfo")) {
                        // OSC string format: /xinfo \u0000~~ [name] [ip] [version] [type]
                        // We attempt to find the name between the first set of quotes after /xinfo
                        val parts = response.split("\"")
                        val consoleName = if (parts.size >= 2) {
                            parts[1] // The first quoted string is usually the name
                        } else {
                            "Wing"
                        }
                        
                        val mixer = WingMixer(name = "$consoleName @ $ip", ip = ip)
                        if (!_discoveredMixers.value.any { it.ip == ip }) {
                            _discoveredMixers.value += mixer
                        }
                    }
                } catch (_: Exception) {
                    // Timeout or other error
                }
            }
            socket.close()
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
        if (mixer != null && mixer.ip != "0.0.0.0") {
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
                        
                        // Look for BPM updates: /-config/tempo followed by float bytes
                        if (response.contains("/-config/tempo")) {
                            val startIndex = response.indexOf("/-config/tempo") + 16 // Path + nulls + ,f + nulls
                            if (receivePacket.length >= startIndex + 4) {
                                val bpmBytes = receivePacket.data.sliceArray(startIndex until startIndex + 4)
                                val receivedBpm = byteArrayToFloat(bpmBytes)
                                if (receivedBpm in 20f..300f) {
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
                    val packet = DatagramPacket(message, message.size, address, 10023)
                    socket.send(packet)
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
                // WING uses /-config/tempo for the master BPM
                val message = "/-config/tempo\u0000\u0000\u0000,f\u0000\u0000".toByteArray() + floatToByteArray(bpm)
                val packet = DatagramPacket(message, message.size, InetAddress.getByName(mixer.ip), 10023)
                socket.send(packet)
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
            intBits.toByte()
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
                    
                    triggerAlertIfMatch(message)
                }
            } catch (_: Exception) {
                // Socket might be closed
            }
        }

        // OSC Alert Listener (Port 5006)
        viewModelScope.launch(Dispatchers.IO) {
            try {
                oscAlertSocket = DatagramSocket(5006)
                val buffer = ByteArray(1024)
                while (true) {
                    val packet = DatagramPacket(buffer, buffer.size)
                    oscAlertSocket?.receive(packet)
                    
                    // Simple OSC parsing for string message
                    // Format: /alert\u0000,s\u0000[string]
                    val data = packet.data
                    val response = String(data, 0, packet.length)
                    
                    if (response.startsWith("/alert")) {
                        // Find the start of the string argument (after the type tag ',s')
                        val typeTagIndex = response.indexOf(",s")
                        if (typeTagIndex != -1) {
                            // The string starts after the null padding of the type tag
                            // In OSC, arguments start on 4-byte boundaries. 
                            // This is a simplified extraction for the demo
                            val message = response.substring(typeTagIndex + 3).trim { it <= ' ' }
                            triggerAlertIfMatch(message)
                        }
                    }
                }
            } catch (_: Exception) {
                // Socket might be closed
            }
        }
    }

    private fun triggerAlertIfMatch(message: String) {
        if (message.isNotEmpty()) {
            _alertMessage.value = message
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
        startDiscovery()
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
                    if (!addr.isLoopbackAddress && host != null && host.contains(".")) {
                        _deviceIp.value = host
                        return@launch
                    }
                }
            }
        }
    }

}
