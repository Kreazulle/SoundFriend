package com.soundffriend.core

/**
 * Interface for mixer-specific implementations.
 */
interface MixerHandler {
    val brand: String
    val model: String
    val maxFxSlots: Int
    
    fun getFxQueryPath(slotId: Int): String
    fun getTempoPaths(): List<String>
    fun getFxParamPaths(slotId: Int, paramId: Int): List<String>
    fun isFxQueryResponse(path: String): Boolean
}

object WingHandler : MixerHandler {
    override val brand = "Behringer"
    override val model = "WING"
    override val maxFxSlots = 16
    
    override fun getFxQueryPath(slotId: Int) = "/fx/$slotId/mdl"
    
    override fun getTempoPaths() = listOf("/config/tempo")
    
    override fun getFxParamPaths(slotId: Int, paramId: Int) = listOf("/fx/$slotId/$paramId")
    
    override fun isFxQueryResponse(path: String) = path.contains("/fx/") && path.contains("/mdl")
}

object X32Handler : MixerHandler {
    override val brand = "Behringer"
    override val model = "X32"
    override val maxFxSlots = 8
    
    override fun getFxQueryPath(slotId: Int) = "/fx/$slotId/type"
    
    override fun getTempoPaths() = listOf("/-config/tempo", "/config/tempo")
    
    override fun getFxParamPaths(slotId: Int, paramId: Int): List<String> {
        // X32/M32 delays usually use parameter 1 for time.
        // We send to both formats just in case.
        val parStr = paramId.toString().padStart(2, '0')
        return listOf("/fx/$slotId/par/$parStr", "/fx/$slotId/$paramId")
    }
    
    override fun isFxQueryResponse(path: String) = path.contains("/fx/") && path.contains("/type")
}

object M32Handler : MixerHandler {
    override val brand = "Midas"
    override val model = "M32"
    override val maxFxSlots = 8
    
    override fun getFxQueryPath(slotId: Int) = "/fx/$slotId/type"
    
    override fun getTempoPaths() = listOf("/-config/tempo", "/config/tempo")
    
    override fun getFxParamPaths(slotId: Int, paramId: Int): List<String> {
        val parStr = paramId.toString().padStart(2, '0')
        return listOf("/fx/$slotId/par/$parStr", "/fx/$slotId/$paramId")
    }
    
    override fun isFxQueryResponse(path: String) = path.contains("/fx/") && path.contains("/type")
}

object MixerHandlerFactory {
    fun getHandler(brand: String, model: String): MixerHandler {
        return when {
            model.contains("WING", ignoreCase = true) -> WingHandler
            model.contains("M32", ignoreCase = true) -> M32Handler
            model.contains("X32", ignoreCase = true) -> X32Handler
            else -> WingHandler // Default to Wing
        }
    }
}
