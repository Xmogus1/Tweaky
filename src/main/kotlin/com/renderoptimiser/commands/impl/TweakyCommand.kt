package com.renderoptimiser.commands.impl

import com.renderoptimiser.RenderOptimiser.debugFlags
import com.renderoptimiser.RenderOptimiser.screen
import com.renderoptimiser.commands.BaseCommand
import com.renderoptimiser.commands.CommandNodeBuilder
import com.renderoptimiser.event.EventBus
import com.renderoptimiser.event.impl.DebugFlagEvent
import com.renderoptimiser.features.impl.general.ChatWaypoints
import com.renderoptimiser.features.impl.visual.Cosmetics
import com.renderoptimiser.ui.clickgui.ClickGuiScreen
import com.renderoptimiser.ui.hud.HudEditorScreen
import com.renderoptimiser.utils.ChatUtils
import com.renderoptimiser.utils.ServerUtils
import com.renderoptimiser.websocket.CosmeticsSocket
import com.mojang.brigadier.arguments.FloatArgumentType
import com.mojang.brigadier.arguments.IntegerArgumentType
import com.mojang.brigadier.arguments.StringArgumentType

object TweakyCommand: BaseCommand("tweaky") {
    private val commands = mapOf(
        "/tweaky" to "Config GUI",
        "/tweaky hud" to "HUD editor",
        "/tweaky debug" to "Debug flags",
        "/tweaky ping" to "Shows your ping in chat",
        "/tweaky tps" to "Shows the server's tps in chat",
        "/tweaky waypoint clear" to "Removes all active waypoints",
        "/tweaky cosmetics" to "Give/remove cosmetics (needs Admin Key)",
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

        literal("cosmetics") {
            runs {
                ChatUtils.chat(
                    """
                    §9§lTweaky Cosmetics§r
                    §e/tweaky cosmetics name <player> <name...> §7- set a custom name (& colors, &#hex, or gradient JSON)
                    §e/tweaky cosmetics size <player> <scale> §7- set model size (negative = upside down)
                    §e/tweaky cosmetics size <player> <x> <y> <z> §7- set per-axis model size
                    §e/tweaky cosmetics offset <player> <up> §7- float the model up/down
                    §e/tweaky cosmetics offset <player> <x> <y> <z> §7- offset the model (x/z follow the body)
                    §e/tweaky cosmetics remove <player> §7- remove a player's cosmetics
                    §e/tweaky cosmetics list §7- list everyone with cosmetics
                    §e/tweaky cosmetics msg <player> <text...> §7- message a connected player
                    §e/tweaky cosmetics msgall <text...> §7- message everyone connected
                    §e/tweaky cosmetics online §7- list connected players
                    §e/tweaky cosmetics reconnect §7- reconnect to the server
                    §e/tweaky cosmetics restore §7- push your local backup to an empty server
                    §7Long names/messages: use the web panel at §f<server>/panel
                    """.trimIndent()
                )
            }

            literal("list") {
                runs { Cosmetics.adminList() }
            }

            literal("reconnect") {
                runs { CosmeticsSocket.reconnect() }
            }

            literal("restore") {
                runs { Cosmetics.adminRestore() }
            }

            literal("debug") {
                runs { Cosmetics.printDebug() }
            }

            literal("online") {
                runs { Cosmetics.adminOnline() }
            }

            literal("msg") {
                argument("player", StringArgumentType.word()) {
                    argument("text", StringArgumentType.greedyString()) {
                        runs { ctx ->
                            Cosmetics.adminMsg(
                                StringArgumentType.getString(ctx, "player"),
                                StringArgumentType.getString(ctx, "text")
                            )
                        }
                    }
                }
            }

            literal("msgall") {
                argument("text", StringArgumentType.greedyString()) {
                    runs { ctx ->
                        Cosmetics.adminMsgAll(StringArgumentType.getString(ctx, "text"))
                    }
                }
            }

            literal("name") {
                argument("player", StringArgumentType.word()) {
                    argument("name", StringArgumentType.greedyString()) {
                        runs { ctx ->
                            Cosmetics.adminSetName(
                                StringArgumentType.getString(ctx, "player"),
                                StringArgumentType.getString(ctx, "name")
                            )
                        }
                    }
                }
            }

            literal("size") {
                argument("player", StringArgumentType.word()) {
                    argument("scale", FloatArgumentType.floatArg(- 10f, 10f)) {
                        // uniform: /tweaky cosmetics size <player> <scale>
                        runs { ctx ->
                            Cosmetics.adminSetSize(
                                StringArgumentType.getString(ctx, "player"),
                                FloatArgumentType.getFloat(ctx, "scale")
                            )
                        }

                        // per-axis: /tweaky cosmetics size <player> <x> <y> <z>
                        argument("y", FloatArgumentType.floatArg(- 10f, 10f)) {
                            argument("z", FloatArgumentType.floatArg(- 10f, 10f)) {
                                runs { ctx ->
                                    Cosmetics.adminSetSize(
                                        StringArgumentType.getString(ctx, "player"),
                                        FloatArgumentType.getFloat(ctx, "scale"),
                                        FloatArgumentType.getFloat(ctx, "y"),
                                        FloatArgumentType.getFloat(ctx, "z")
                                    )
                                }
                            }
                        }
                    }
                }
            }

            literal("offset") {
                argument("player", StringArgumentType.word()) {
                    argument("a", FloatArgumentType.floatArg(- 10f, 10f)) {
                        // vertical only: /tweaky cosmetics offset <player> <up>
                        runs { ctx ->
                            Cosmetics.adminSetOffset(
                                StringArgumentType.getString(ctx, "player"),
                                0f,
                                FloatArgumentType.getFloat(ctx, "a"),
                                0f
                            )
                        }

                        // full: /tweaky cosmetics offset <player> <x> <y> <z>
                        argument("y", FloatArgumentType.floatArg(- 10f, 10f)) {
                            argument("z", FloatArgumentType.floatArg(- 10f, 10f)) {
                                runs { ctx ->
                                    Cosmetics.adminSetOffset(
                                        StringArgumentType.getString(ctx, "player"),
                                        FloatArgumentType.getFloat(ctx, "a"),
                                        FloatArgumentType.getFloat(ctx, "y"),
                                        FloatArgumentType.getFloat(ctx, "z")
                                    )
                                }
                            }
                        }
                    }
                }
            }

            literal("remove") {
                argument("player", StringArgumentType.word()) {
                    runs { ctx ->
                        Cosmetics.adminRemove(StringArgumentType.getString(ctx, "player"))
                    }
                }
            }
        }

        literal("waypoint") {
            literal("clear") {
                runs { ChatWaypoints.clearWaypoints() }
            }

            argument("x", IntegerArgumentType.integer()) {
                argument("y", IntegerArgumentType.integer()) {
                    argument("z", IntegerArgumentType.integer()) {
                        runs { ctx ->
                            ChatWaypoints.addWaypoint(
                                IntegerArgumentType.getInteger(ctx, "x"),
                                IntegerArgumentType.getInteger(ctx, "y"),
                                IntegerArgumentType.getInteger(ctx, "z")
                            )
                        }
                    }
                }
            }
        }
    }
}
