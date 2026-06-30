package com.renderoptimiser.ui.notification

import com.renderoptimiser.RenderOptimiser.mc
import com.renderoptimiser.ui.utils.Animation
import net.minecraft.network.chat.Component
import java.awt.Color

class Notification(val title: String, val message: String, val duration: Long, val titleColor: Color = Color(85, 255, 85)) {
    val anim = Animation(350L)
    var elapsedTime = 0L
    var isDead = false

    val wrappedLines = mc.font.split(Component.literal(message), 150)
    val height: Float = 22f + (wrappedLines.size * (mc.font.lineHeight + 1f)) + 4f
}