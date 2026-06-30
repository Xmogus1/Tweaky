package com.renderoptimiser.event.impl

import com.renderoptimiser.event.Event
import net.minecraft.world.entity.Entity

class CheckEntityRenderEvent(val entity: Entity): Event(cancelable = true)
