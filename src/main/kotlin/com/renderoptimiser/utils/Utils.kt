package com.renderoptimiser.utils

import com.renderoptimiser.RenderOptimiser.mc
import net.minecraft.network.protocol.Packet
import java.awt.Color

object Utils {
    val favoriteColor = Color(0, 134, 255)

    fun Packet<*>.send() = mc.connection?.send(this)
}