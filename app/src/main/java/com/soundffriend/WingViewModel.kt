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

data class FxSlot(val id: Int, val model: String, val hasTempo: Boolean)

enum class NotificationType { ALERT, INFO }
data class Notification(val text: String, val type: NotificationType)

class WingViewModel : ViewModel() {
    private val _discoveredMixers = MutableStateFlow<List<WingMixer>>(emptyList())
    val discoveredMixers = _discoveredMixers.asStateFlow()

    private val _selectedMixer = MutableStateFlow<WingMixer?>(null)
    val selectedMixer = _selectedMixer.asStateFlow()

    private val _bpm = MutableStateFlow(120f)
    val bpm = _bpm.asStateFlow()

    private val _fxSlots = MutableStateFlow<List<FxSlot>>(emptyList())
    val fxSlots = _fxSlots.asStateFlow()

    private val _selectedFxSlot = MutableStateFlow<FxSlot?>(null)
    val selectedFxSlot = _selectedFxSlot.asStateFlow()

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
                    val name = if (parts.size >= 3) parts[2] else "WING"
                    addMixer(name, ip)
                } else if (response.contains("/xinfo")) {
                    val parts = response.split(Regex("[^a-zA-Z0-9 _-]"))
                    val consoleName = parts.firstOrNull { (it.length > 2) && (it != "xinfo") } ?: "Wing"
                    addMixer(consoleName, ip)
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
        _fxSlots.value = emptyList()
        _selectedFxSlot.value = null
        
        if (mixer == null) {
            _discoveredMixers.value = emptyList()
            _isScanning.value = false
            discoveryJob?.cancel()
        }
        
        if ((mixer != null) && (mixer.ip != "0.0.0.0")) {
            // Start listening for OSC messages from this mixer
            listenToMixer(mixer)
            // Query for FX slots that support tempo
            queryFxSlots(mixer)
        }
    }

    private fun queryFxSlots(mixer: WingMixer) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val socket = DatagramSocket()
                val address = InetAddress.getByName(mixer.ip)
                
                // Query all 16 FX slots for their model names
                for (i in 1..16) {
                    val path = "/fx/$i/mdl"
                    // OSC String padding: must be null-terminated and total length % 4 == 0
                    val nullsNeeded = 4 - (path.length % 4)
                    val msg = path.toByteArray() + ByteArray(nullsNeeded)
                    
                    socket.send(DatagramPacket(msg, msg.size, address, 2223))
                    socket.send(DatagramPacket(msg, msg.size, address, 10023))
                }
                
                val buffer = ByteArray(2048)
                val packet = DatagramPacket(buffer, buffer.size)
                socket.soTimeout = 1000
                
                val newSlots = mutableListOf<FxSlot>()
                val startTime = System.currentTimeMillis()
                while ((System.currentTimeMillis() - startTime) < 2000) {
                    try {
                        socket.receive(packet)
                        val dataString = String(packet.data, 0, packet.length)
                        
                        if (dataString.contains("/fx/") && dataString.contains("/mdl")) {
                            val slotId = dataString.substringAfter("/fx/").substringBefore("/").toIntOrNull() ?: continue
                            
                            // OSC response format: [path]\0[padding],s\0[padding][value]\0
                            // We look for the model name after the ",s" tag
                            val typeTagIndex = dataString.indexOf(",s")
                            if (typeTagIndex != -1) {
                                // The string value usually starts 4 bytes after the start of ",s" tag (aligned)
                                val valueIndex = (typeTagIndex + 4) / 4 * 4
                                if (valueIndex < packet.length) {
                                    val modelName = String(packet.data, valueIndex, packet.length - valueIndex)
                                        .trim { it <= ' ' || it.toInt() == 0 }
                                    
                                    if (modelName.isNotEmpty() && modelName != "NONE") {
                                        val isTempoFx = modelName.contains("ST-DL", ignoreCase = true) || 
                                                       modelName.contains("TAP-DL", ignoreCase = true) ||
                                                       modelName.contains("TAPE-DL", ignoreCase = true) ||
                                                       modelName.contains("BBD-DL", ignoreCase = true) ||
                                                       modelName.contains("OILCAN", ignoreCase = true) ||
                                                       modelName.contains("DELAY", ignoreCase = true) || 
                                                       modelName.contains("DLY", ignoreCase = true) ||
                                                       modelName.contains("TAP", ignoreCase = true) ||
                                                       modelName.contains("ECHO", ignoreCase = true) ||
                                                       modelName.contains("STEREO", ignoreCase = true)
                                        
                                        if (isTempoFx && !newSlots.any { it.id == slotId }) {
                                            newSlots.add(FxSlot(slotId, modelName, true))
                                        }
                                    }
                                }
                            }
                        }
                    } catch (_: Exception) {}
                }
                _fxSlots.value = newSlots.sortedBy { it.id }
                socket.close()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun selectFxSlot(slot: FxSlot?) {
        _selectedFxSlot.value = slot
    }

    private var heartbeatJob: kotlinx.coroutines.Job? = null

    private var mainSocket: DatagramSocket? = null

    private fun getSocket(): DatagramSocket {
        if (mainSocket == null || mainSocket!!.isClosed) {
            mainSocket = DatagramSocket().apply {
                broadcast = true
                soTimeout = 0 // Non-blocking for persistent listener
            }
        }
        return mainSocket!!
    }

    private fun listenToMixer(mixer: WingMixer) {
        heartbeatJob?.cancel()
        heartbeatJob = viewModelScope.launch(Dispatchers.IO) {
            val socket = getSocket()
            val message = "/xremote\u0000\u0000\u0000\u0000".toByteArray()
            val address = InetAddress.getByName(mixer.ip)
            
            // Start a separate job to listen for responses
            val listenerJob = launch {
                val receiveData = ByteArray(2048)
                val receivePacket = DatagramPacket(receiveData, receiveData.size)
                while (isActive) {
                    try {
                        socket.receive(receivePacket)
                        val data = receivePacket.data
                        
                        // 1. Extract OSC Path
                        val nullIndex = data.indexOf(0.toByte())
                        if (nullIndex <= 0) continue
                        val path = String(data, 0, nullIndex)
                        
                        // 2. Identify if it's a message we care about
                        val isGlobalTempo = (path == "/config/tempo") || (path == "/-config/tempo")
                        val isFxTime = path.startsWith("/fx/") && (path.endsWith("/1") || path.endsWith("/par/1"))
                        
                        if (isGlobalTempo || isFxTime) {
                            // 3. Find type tag comma
                            val commaIndex = data.indexOf(','.toByte())
                            if (commaIndex != -1 && (commaIndex + 1 < receivePacket.length)) {
                                if (data[commaIndex + 1] == 'f'.toByte()) {
                                    // 4. Extract float value (aligned to 4-byte boundary)
                                    val floatStartIndex = ((commaIndex + 4) / 4) * 4
                                    if (floatStartIndex + 4 <= receivePacket.length) {
                                        val bpmBytes = data.sliceArray(floatStartIndex until (floatStartIndex + 4))
                                        val value = byteArrayToFloat(bpmBytes)
                                        
                                        var receivedBpm = 0f
                                        if (isGlobalTempo) {
                                            receivedBpm = value // Global is already BPM
                                        } else {
                                            // FX Time is likely Absolute MS (> 10) or Seconds (> 0.1)
                                            receivedBpm = when {
                                                value > 10f -> 60000f / value // MS to BPM
                                                value > 0.1f -> 60f / value    // Seconds to BPM
                                                else -> 0f
                                            }
                                        }
                                        
                                        if (receivedBpm in (20f..350f)) {
                                            _bpm.value = receivedBpm
                                        }
                                    }
                                }
                            }
                        }
                    } catch (_: Exception) {}
                }
            }

            while (isActive) {
                try {
                    val packet2223 = DatagramPacket(message, message.size, address, 2223)
                    val packet10023 = DatagramPacket(message, message.size, address, 10023)
                    socket.send(packet2223)
                    socket.send(packet10023)
                    delay(8000) // WING requires /xremote every 10s
                } catch (e: Exception) {
                    delay(5000)
                }
            }
            listenerJob.cancel()
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
        
        // If last tap was more than 2.5 seconds ago, start a new sequence
        if (tapTimestamps.isNotEmpty() && (now - tapTimestamps.last() > 2500)) {
            tapTimestamps.clear()
        }
        
        tapTimestamps.add(now)
        // Keep last 4 taps for a stable average, but react from the 2nd tap
        if (tapTimestamps.size > 4) tapTimestamps.removeAt(0)
        
        if (tapTimestamps.size >= 2) {
            val totalInterval = tapTimestamps.last() - tapTimestamps.first()
            val averageInterval = totalInterval.toFloat() / (tapTimestamps.size - 1)
            val newBpm = 60000f / averageInterval
            if (newBpm in (20f..350f)) {
                _bpm.value = newBpm
            }
        }
        
        // ALWAYS emit OSC command on every single tap
        sendBpmToWing(_bpm.value)
    }

    private fun sendBpmToWing(bpm: Float) {
        val mixer = _selectedMixer.value ?: return
        if (mixer.ip == "0.0.0.0") return
        
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val socket = getSocket()
                val address = InetAddress.getByName(mixer.ip)
                
                // 1. Prepare Master Tempo Message (BPM value)
                val msgWing = createOscMessage("/config/tempo", bpm)
                val msgX32 = createOscMessage("/-config/tempo", bpm)
                
                // 2. Prepare FX Slot Messages
                val timeMs = 60000f / bpm.coerceAtLeast(1f)
                val fxMessages = mutableListOf<ByteArray>()
                
                val selectedFx = _selectedFxSlot.value
                if (selectedFx != null) {
                    val value = calculateFxValue(selectedFx.model, timeMs)
                    for (paramId in 1..4) {
                        fxMessages.add(createOscMessage("/fx/${selectedFx.id}/$paramId", value))
                        fxMessages.add(createOscMessage("/fx/${selectedFx.id}/par/$paramId", value))
                    }
                } else {
                    for (fx in _fxSlots.value) {
                        val value = calculateFxValue(fx.model, timeMs)
                        for (paramId in 1..4) {
                            fxMessages.add(createOscMessage("/fx/${fx.id}/$paramId", value))
                            fxMessages.add(createOscMessage("/fx/${fx.id}/par/$paramId", value))
                        }
                    }
                }

                // 3. Send to all relevant ports using the shared socket
                val ports = listOf(2223, 10023)
                for (port in ports) {
                    socket.send(DatagramPacket(msgWing, msgWing.size, address, port))
                    socket.send(DatagramPacket(msgX32, msgX32.size, address, port))
                    for (fxMsg in fxMessages) {
                        socket.send(DatagramPacket(fxMsg, fxMsg.size, address, port))
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun calculateFxValue(model: String, timeMs: Float): Float {
        val modelUpper = model.uppercase()
        return when {
            modelUpper.contains("OILCAN") || modelUpper.contains("OIL") -> {
                // OILCAN: 1..1000 ms -> 0..10.0
                (timeMs / 100f).coerceIn(0f, 10f)
            }
            modelUpper.contains("BBD-DL") || modelUpper.contains("BBD") -> {
                // BBD Delay: 1001 ms -> 1..100 scale
                (timeMs / 10.01f).coerceIn(1f, 100f)
            }
            modelUpper.contains("TAPE-DL") || modelUpper.contains("TAPE") -> {
                // TAPE-DL: 60..650 ms range, mapped between values 60 and 650 (direct ms)
                timeMs.coerceIn(60f, 650f)
            }
            else -> timeMs // ST-DL (Stereo Delay), TAP-DL, etc. use absolute ms like Ultra tap
        }
    }

    private fun createOscMessage(path: String, value: Float): ByteArray {
        val pathBytes = path.toByteArray()
        // OSC strings must be null-terminated and padded to a multiple of 4 bytes
        val pathPadding = 4 - (pathBytes.size % 4)
        val paddedPath = pathBytes + ByteArray(pathPadding)
        
        val typeTag = ",f".toByteArray()
        // ",f" is 2 bytes, needs 2 nulls to reach 4 bytes
        val paddedType = typeTag + ByteArray(2)
        
        return paddedPath + paddedType + floatToByteArray(value)
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
        mainSocket?.close()
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
