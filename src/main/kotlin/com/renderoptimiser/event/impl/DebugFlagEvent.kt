package com.renderoptimiser.event.impl

import com.renderoptimiser.RenderOptimiser
import com.renderoptimiser.event.Event

sealed class DebugFlagEvent(val flag: String): Event(false) {
    class Add(flag: String): DebugFlagEvent(flag)
    class Remove(flag: String): DebugFlagEvent(flag)

    override fun cancel() {
        RenderOptimiser.debugFlags.remove(flag)
    }
}