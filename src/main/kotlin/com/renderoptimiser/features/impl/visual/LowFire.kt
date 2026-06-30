package com.renderoptimiser.features.impl.visual

import com.renderoptimiser.features.Feature
import com.renderoptimiser.ui.clickgui.components.*
import com.renderoptimiser.ui.clickgui.components.impl.SliderSetting

/**
 * Lowers the first-person fire overlay so it blocks less of your view (classic "low fire").
 * Implemented per-version in MixinScreenEffectRenderer (the fire submit call is wrapped with a
 * push/translate/pop — method names diverge between 26.1.2 and 26.2).
 */
object LowFire: Feature("Lowers the first-person fire overlay.") {

    private val amount by SliderSetting("Lower By", 30, 0, 60, 1, "%")
        .withDescription("How far the fire overlay is pushed down.")

    /** World-units downward offset for the fire quads; 0 = feature inactive. */
    @JvmStatic
    fun offset(): Float = if (enabled) amount.value / 100f else 0f
}
