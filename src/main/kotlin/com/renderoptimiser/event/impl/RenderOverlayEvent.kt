package com.renderoptimiser.event.impl

import com.renderoptimiser.event.Event
import net.minecraft.client.DeltaTracker
import net.minecraft.client.gui.GuiGraphicsExtractor

class RenderOverlayEvent(val context: GuiGraphicsExtractor, val deltaTracker: DeltaTracker): Event(cancelable = false)