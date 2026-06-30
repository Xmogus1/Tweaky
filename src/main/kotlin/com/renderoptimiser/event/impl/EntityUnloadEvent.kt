package com.renderoptimiser.event.impl

import com.renderoptimiser.event.Event
import net.minecraft.world.entity.Entity

class EntityUnloadEvent(val entity: Entity): Event(false)