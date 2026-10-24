package com.renderoptimiser.hypixel

import com.renderoptimiser.RenderOptimiser.logger
import net.hypixel.modapi.HypixelModAPI
import net.hypixel.modapi.handler.ClientboundPacketHandler
import net.hypixel.modapi.packet.impl.clientbound.ClientboundPartyInfoPacket
import net.hypixel.modapi.packet.impl.serverbound.ServerboundPartyInfoPacket
import java.util.UUID

/**
 * Thin wrapper over the Hypixel Mod API for party membership. The mod-api classes are compile-only;
 * at runtime they are provided by the HypixelModAPI mod. If it is absent, [init] catches the
 * class-load failure and [available] stays false, so [com.renderoptimiser.features.impl.general.PartyChat]
 * falls back to its chat-based [com.renderoptimiser.party.PartyTracker].
 */
object HypixelApi {
    @Volatile
    var available = false
        private set

    @Volatile
    var partyMembers: Set<UUID> = emptySet()
        private set

    @Volatile
    private var started = false

    fun init() {
        if (started) return
        started = true
        try {
            HypixelModAPI.getInstance().registerHandler(ClientboundPartyInfoPacket::class.java, ClientboundPacketHandler { packet ->
                val members = runCatching { packet.memberMap.keys.toSet() }.getOrDefault(emptySet())
                partyMembers = if (packet.isInParty) members else emptySet()
            })
            available = true
            logger.info("Tweaky: hooked the Hypixel Mod API for party info")
        }
        catch (t: Throwable) {
            available = false
            logger.info("Tweaky: Hypixel Mod API unavailable, party chat falls back to chat tracking")
        }
    }

    fun queryParty() {
        if (! available) return
        runCatching { HypixelModAPI.getInstance().sendPacket(ServerboundPartyInfoPacket()) }
    }
}
