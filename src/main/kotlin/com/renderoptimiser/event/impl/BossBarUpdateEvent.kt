package com.renderoptimiser.event.impl

import com.renderoptimiser.event.Event
import net.minecraft.network.chat.Component

class BossBarUpdateEvent(val name: Component, val progress: Float): Event(false)