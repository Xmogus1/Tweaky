package com.renderoptimiser.features.impl.general

import com.renderoptimiser.event.impl.TickEvent
import com.renderoptimiser.features.Feature
import com.renderoptimiser.ui.clickgui.components.impl.SliderSetting

/**
 * Multiplies your creative flight speed. Applied every tick while you can fly (creative/spectator)
 * so it survives the server re-sending your abilities; disabling restores the vanilla 0.05.
 */
object FlySpeed: Feature("Change how fast you fly in creative mode.") {

    private val multiplier by SliderSetting("Fly Speed", 2f, 1f, 10f, 0.5f)
        .withDescription("Multiplier on top of the default creative flight speed.")

    private const val DEFAULT = 0.05f

    override fun init() {
        register<TickEvent.Start> {
            val abilities = mc.player?.abilities ?: return@register
            if (abilities.mayfly) abilities.flyingSpeed = DEFAULT * multiplier.value
        }
    }

    override fun onDisable() {
        super.onDisable()
        mc.player?.abilities?.let { if (it.mayfly) it.flyingSpeed = DEFAULT }
    }
}
