package com.renderoptimiser.commands.impl

import com.renderoptimiser.RenderOptimiser.debugFlags
import com.renderoptimiser.RenderOptimiser.screen
import com.renderoptimiser.commands.BaseCommand
import com.renderoptimiser.commands.CommandNodeBuilder
import com.renderoptimiser.event.EventBus
import com.renderoptimiser.event.impl.DebugFlagEvent
import com.renderoptimiser.ui.clickgui.ClickGuiScreen
import com.renderoptimiser.ui.hud.HudEditorScreen
import com.renderoptimiser.utils.ChatUtils
import com.renderoptimiser.utils.ServerUtils
import com.mojang.brigadier.arguments.StringArgumentType

object TweakyCommand: BaseCommand("tweaky") {
    private val commands = mapOf(
        "/tweaky" to "Config GUI",
        "/tweaky hud" to "HUD editor",
        "/tweaky debug" to "Debug flags",
        "/tweaky ping" to "Shows your ping in chat",
        "/tweaky tps" to "Shows the server's tps in chat",
    )

    override fun CommandNodeBuilder.build() {
        runs { screen = ClickGuiScreen }

        literal("help") {
            runs {
                val helpMenu = StringBuilder("§9§lTweaky§r\n")
                commands.forEach { (cmd, desc) -> helpMenu.append("§e$cmd §7- $desc\n") }
                ChatUtils.chat(helpMenu.toString().trim())
            }
        }

        literal("hud") {
            runs { screen = HudEditorScreen }
        }

        literal("ping") {
            runs {
                ChatUtils.modMessage("§aPing: §f${ServerUtils.averagePing}ms")
            }
        }

        literal("tps") {
            runs {
                ChatUtils.modMessage("§aTPS: §f${ServerUtils.tps}")
            }
        }

        literal("debug") {
            runs {
                ChatUtils.modMessage("§7Flags: §f${debugFlags.joinToString(", ")}")
            }

            argument("flag", StringArgumentType.word()) {
                runs { ctx ->
                    val flag = StringArgumentType.getString(ctx, "flag")
                    val event: DebugFlagEvent
                    if (debugFlags.remove(flag)) {
                        ChatUtils.modMessage("§cRemoved debug flag: §b$flag")
                        event = DebugFlagEvent.Remove(flag)
                    }
                    else {
                        debugFlags.add(flag)
                        ChatUtils.modMessage("§aAdded debug flag: §b$flag")
                        event = DebugFlagEvent.Add(flag)
                    }

                    EventBus.post(event)
                }
            }
        }
    }
}
