package com.renderoptimiser.event.impl

import com.renderoptimiser.event.Event
import com.renderoptimiser.utils.render.RenderContext

class RenderWorldEvent(val ctx: RenderContext): Event(cancelable = false)