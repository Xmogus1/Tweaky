package com.renderoptimiser.commands.impl

import com.renderoptimiser.commands.BaseCommand
import com.renderoptimiser.commands.CommandNodeBuilder
import com.renderoptimiser.features.impl.general.PartyChat
import com.mojang.brigadier.arguments.StringArgumentType

object WscCommand: BaseCommand("wsc") {
    override fun CommandNodeBuilder.build() {
        runs { PartyChat.toggleFeature() }
        argument("message", StringArgumentType.greedyString()) {
            runs { ctx -> PartyChat.send(StringArgumentType.getString(ctx, "message")) }
        }
    }
}
