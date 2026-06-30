package com.renderoptimiser.event.impl

import com.renderoptimiser.event.Event
import com.renderoptimiser.utils.ChatUtils.formattedText
import com.renderoptimiser.utils.ChatUtils.unformattedText
import net.minecraft.network.chat.Component

class ActionBarMessageEvent(val component: Component): Event(cancelable = true) {
    val formattedText by lazy { component.formattedText }
    val unformattedText by lazy { component.unformattedText }

    var message = formattedText
}