package com.renderoptimiser.features.impl.sound

import com.renderoptimiser.config.PogObject
import com.renderoptimiser.features.Feature

object SoundManager: Feature("Adjust volumes for every sound in the game") {
    var volumes by PogObject("renderoptimiser_sounds", mutableMapOf<String, Float>())

    @JvmStatic
    fun getMultiplier(id: String): Float {
        if (! enabled) return 1.0f
        return volumes.getOrDefault(id, 1f)
    }
}