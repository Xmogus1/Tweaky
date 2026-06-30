package com.renderoptimiser.commands.impl

import com.renderoptimiser.RenderOptimiser.mc
import com.renderoptimiser.commands.BaseCommand
import com.renderoptimiser.commands.CommandNodeBuilder
import com.renderoptimiser.ui.notification.NotificationManager
import com.renderoptimiser.utils.ChatUtils

object SendCoordinatesCommand: BaseCommand("sendcoordinates", mutableSetOf("sendcoords")) {
    override fun CommandNodeBuilder.build() {
        runs {
            val player = mc.player
            if (player == null) {
                NotificationManager.error("Can't send coordinates", "You need to be in a world.")
                return@runs
            }
            ChatUtils.sendMessage("x: ${player.blockX}, y: ${player.blockY}, z: ${player.blockZ}")
        }
    }
}
