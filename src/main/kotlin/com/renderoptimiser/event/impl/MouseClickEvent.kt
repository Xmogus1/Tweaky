package com.renderoptimiser.event.impl

import com.renderoptimiser.event.Event

class MouseClickEvent(val button: Int, val action: Int, val modifiers: Int): Event(true)