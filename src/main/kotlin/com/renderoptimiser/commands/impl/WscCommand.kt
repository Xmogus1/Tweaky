package com.renderoptimiser.commands.impl

import com.renderoptimiser.commands.BaseCommand
import com.renderoptimiser.commands.CommandNodeBuilder
import com.renderoptimiser.features.impl.general.PartyChat
import com.renderoptimiser.utils.ChatUtils
import com.mojang.brigadier.arguments.StringArgumentType

object WscCommand: BaseCommand("wsc") {
    override fun CommandNodeBuilder.build() {
        runs { ChatUtils.modMessage("§7Use §f/wsc <message>§7 to talk to your party.") }
        argument("message", StringArgumentType.greedyString()) {
            runs { ctx -> PartyChat.send(StringArgumentType.getString(ctx, "message")) }
        }
    }
}
