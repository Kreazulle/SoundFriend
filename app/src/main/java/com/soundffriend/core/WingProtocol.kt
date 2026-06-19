package com.soundffriend.core

/**
 * Pure Kotlin logic for Behringer WING OSC Protocol.
 * This file is designed to be shared between Android and Apple Watch.
 */

object WingProtocol {
    
    fun createOscMessage(path: String, value: Float): ByteArray {
        val pathBytes = path.toByteArray()
        val pathPadding = 4 - (pathBytes.size % 4)
        val paddedPath = pathBytes + ByteArray(pathPadding)
        
        val typeTag = ",f".toByteArray()
        val paddedType = typeTag + ByteArray(2)
        
        return paddedPath + paddedType + floatToByteArray(value)
    }

    fun createOscQueryMessage(path: String): ByteArray {
        val pathBytes = path.toByteArray()
        val nullsNeeded = 4 - (pathBytes.size % 4)
        return pathBytes + ByteArray(nullsNeeded)
    }

    fun floatToByteArray(value: Float): ByteArray {
        val intBits = java.lang.Float.floatToIntBits(value)
        return byteArrayOf(
            (intBits shr 24).toByte(),
            (intBits shr 16).toByte(),
            (intBits shr 8).toByte(),
            intBits.toByte(),
        )
    }

    fun calculateFxValue(model: String, timeMs: Float, isNormalized: Boolean = false): Float {
        val modelUpper = model.uppercase()
        
        if (isNormalized) {
            // X32/M32 specific: Most delays have a 3000ms range mapped to 0.0-1.0
            // Some might have different ranges, but 3000ms is the standard for the ones we target.
            return (timeMs / 3000f).coerceIn(0f, 1f)
        }

        return when {
            modelUpper.contains("OILCAN") || modelUpper.contains("OIL") -> {
                (timeMs / 100f).coerceIn(0f, 10f)
            }
            modelUpper.contains("BBD-DL") || modelUpper.contains("BBD") -> {
                (timeMs / 10.01f).coerceIn(1f, 100f)
            }
            modelUpper.contains("TAPE-DL") || modelUpper.contains("TAPE") -> {
                timeMs.coerceIn(60f, 650f)
            }
            else -> timeMs
        }
    }

    fun reverseCalculateFxBpm(model: String, value: Float): Float {
        if (value <= 0) return 0f
        val modelUpper = model.uppercase()
        val timeMs = when {
            modelUpper.contains("OILCAN") || modelUpper.contains("OIL") -> value * 100f
            modelUpper.contains("BBD-DL") || modelUpper.contains("BBD") -> value * 10.01f
            modelUpper.contains("TAPE-DL") || modelUpper.contains("TAPE") -> value
            else -> value
        }
        return 60000f / timeMs.coerceAtLeast(1f)
    }

    fun byteArrayToFloat(bytes: ByteArray): Float {
        if (bytes.size < 4) return 0f
        val intBits = (bytes[0].toInt() and 0xFF shl 24) or
                     (bytes[1].toInt() and 0xFF shl 16) or
                     (bytes[2].toInt() and 0xFF shl 8) or
                     (bytes[3].toInt() and 0xFF)
        return java.lang.Float.intBitsToFloat(intBits)
    }

    fun byteArrayToInt(bytes: ByteArray): Int {
        if (bytes.size < 4) return 0
        return (bytes[0].toInt() and 0xFF shl 24) or
               (bytes[1].toInt() and 0xFF shl 16) or
               (bytes[2].toInt() and 0xFF shl 8) or
               (bytes[3].toInt() and 0xFF)
    }
}
