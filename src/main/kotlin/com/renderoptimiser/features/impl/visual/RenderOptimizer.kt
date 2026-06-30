package com.renderoptimiser.features.impl.visual

import com.renderoptimiser.event.impl.CheckEntityRenderEvent
import com.renderoptimiser.event.impl.MainThreadPacketReceivedEvent
import com.renderoptimiser.features.Feature
import com.renderoptimiser.ui.clickgui.components.impl.ToggleSetting
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket
import net.minecraft.world.entity.EntityTypes
import net.minecraft.world.entity.LivingEntity

object RenderOptimizer: Feature("Optimize rendering by hiding useless entities and effects.") {
    private val hideFallingBlocks by ToggleSetting("Hide Falling Blocks").withDescription("Stops falling block entities from spawning.")
    private val hideLightning by ToggleSetting("Hide Lightning Bolts").withDescription("Stops lightning bolts from spawning.")
    private val hideXpOrbs by ToggleSetting("Hide XP Orbs").withDescription("Stops experience orbs from spawning.")
    private val hideDeadMobs by ToggleSetting("Hide Dead Mobs").withDescription("Hides mobs that are dead or have 0 health.")
    val hideFireOnEntities by ToggleSetting("Hide Fire On Entities").withDescription("Hides the fire overlay rendered on burning entities.")

    override fun init() {
        register<MainThreadPacketReceivedEvent.Pre> {
            val packet = event.packet
            if (packet !is ClientboundAddEntityPacket) return@register

            val isBlock = packet.type == EntityTypes.FALLING_BLOCK && hideFallingBlocks.value
            val isLightning = packet.type == EntityTypes.LIGHTNING_BOLT && hideLightning.value
            val isXp = packet.type == EntityTypes.EXPERIENCE_ORB && hideXpOrbs.value

            if (isBlock || isLightning || isXp) event.isCanceled = true
        }

        register<CheckEntityRenderEvent> {
            if (! hideDeadMobs.value) return@register
            if (! event.entity.isAlive || ((event.entity as? LivingEntity)?.health ?: 1f) <= 0f) {
                event.isCanceled = true
            }
        }
    }
}
