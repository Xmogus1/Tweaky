package com.renderoptimiser.features.impl.misc

import com.renderoptimiser.event.impl.TickEvent
import com.renderoptimiser.features.Feature

object AutoSprint: Feature("Automatically sprint for you.") {
    override fun init() {
        register<TickEvent.Start> {
            if (mc.player == null) return@register
            if (mc.player?.isSprinting == true) return@register
            mc.options.keySprint.isDown = true
        }
    }
}