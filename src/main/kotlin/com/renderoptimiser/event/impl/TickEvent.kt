package com.renderoptimiser.event.impl

import com.renderoptimiser.event.Event

abstract class TickEvent: Event(false) {
    object Start: TickEvent()
    object End: TickEvent()
    object Server: TickEvent()
}