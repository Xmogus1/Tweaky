package com.renderoptimiser.event

import com.renderoptimiser.RenderOptimiser.mc
import com.renderoptimiser.event.EventBus.register
import com.renderoptimiser.event.impl.*
import com.renderoptimiser.utils.render.RenderContext
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientEntityEvents
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLevelEvents
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents
import net.minecraft.network.chat.Component
import net.minecraft.network.protocol.game.*
import net.minecraft.world.inventory.MenuType
import net.minecraft.world.item.ItemStack

object EventDispatcher {
    private var invWindowId: Int = - 1
    private var invTitle: Component? = null
    private var invSlotCount: Int = 0
    private val invItems = mutableMapOf<Int, ItemStack>()
    private var invAccept = false
    private var invFired = false


    fun init() {
        LevelRenderEvents.AFTER_TRANSLUCENT_TERRAIN.register { context ->
            EventBus.post(RenderWorldEvent(RenderContext.fromContext(context)))
        }

        ClientLevelEvents.AFTER_CLIENT_LEVEL_CHANGE.register { _, _ ->
            EventBus.post(WorldChangeEvent)
        }

        ClientTickEvents.START_CLIENT_TICK.register { mc ->
            mc.level?.let { EventBus.post(TickEvent.Start) }
        }

        ClientTickEvents.END_CLIENT_TICK.register { mc ->
            mc.level?.let { EventBus.post(TickEvent.End) }
        }

        ClientLifecycleEvents.CLIENT_STOPPING.register {
            EventBus.post(ShutdownEvent)
        }

        ClientEntityEvents.ENTITY_UNLOAD.register { entity, _ ->
            EventBus.post(EntityUnloadEvent(entity))
        }


        register<PacketEvent.Received> {
            if (event.packet is ClientboundSystemChatPacket) {
                if (event.packet.overlay) return@register
                if (EventBus.post(ChatMessageEvent(event.packet.content))) { // todo post cancelable on mainthread
                    event.isCanceled = true
                }
            }
            else if (event.packet is ClientboundContainerClosePacket) {
                if (event.packet.containerId == invWindowId) resetInventoryState()
            }
            else if (event.packet is ClientboundOpenScreenPacket) {
                resetInventoryState()
                invAccept = true
                invWindowId = event.packet.containerId
                invTitle = event.packet.title
                invSlotCount = when (event.packet.type) {
                    MenuType.GENERIC_9x1 -> 9
                    MenuType.GENERIC_9x2 -> 18
                    MenuType.GENERIC_9x3 -> 27
                    MenuType.GENERIC_9x4 -> 36
                    MenuType.GENERIC_9x5 -> 45
                    MenuType.GENERIC_9x6 -> 54
                    MenuType.GENERIC_3x3 -> 9
                    MenuType.HOPPER -> 5
                    else -> 0
                }
            }
            else if (event.packet is ClientboundContainerSetContentPacket) {
                if (event.packet.containerId == invWindowId) {
                    event.packet.items.forEachIndexed { index, stack ->
                        if (index < invSlotCount && ! stack.isEmpty) {
                            invItems[index] = stack
                        }
                    }
                    finishInventoryLoading()
                }
            }
            else if (event.packet is ClientboundContainerSetSlotPacket) {
                if (invAccept && event.packet.containerId == invWindowId) {
                    val slot = event.packet.slot
                    if (slot < invSlotCount) {
                        if (! event.packet.item.isEmpty) invItems[slot] = event.packet.item
                    }
                    else finishInventoryLoading()

                    if (invItems.size >= invSlotCount) finishInventoryLoading()
                }
            }
        }
    }


    private fun resetInventoryState() {
        invWindowId = - 1
        invTitle = null
        invSlotCount = 0
        invItems.clear()
        invAccept = false
        invFired = false
    }

    private fun finishInventoryLoading() {
        if (! invAccept) return
        invAccept = false

        val title = invTitle ?: return
        val winId = invWindowId
        val slotCount = invSlotCount
        val items = HashMap(invItems)

        if (invFired) return
        if (invWindowId != winId) return

        invFired = true
        mc.execute {
            EventBus.post(ContainerFullyOpenedEvent(title, winId, slotCount, items))
        }
    }
}