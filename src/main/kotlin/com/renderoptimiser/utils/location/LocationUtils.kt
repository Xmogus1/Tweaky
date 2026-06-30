package com.renderoptimiser.utils.location

import com.renderoptimiser.RenderOptimiser
import com.renderoptimiser.RenderOptimiser.mc
import com.renderoptimiser.event.EventBus
import com.renderoptimiser.event.EventPriority
import com.renderoptimiser.event.impl.MainThreadPacketReceivedEvent
import com.renderoptimiser.event.impl.TickEvent
import com.renderoptimiser.event.impl.WorldChangeEvent
import com.renderoptimiser.utils.ChatUtils.removeFormatting
import com.renderoptimiser.utils.remove
import com.renderoptimiser.utils.startsWithOneOf
import net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket
import net.minecraft.network.protocol.game.ClientboundSetObjectivePacket
import net.minecraft.network.protocol.game.ClientboundSetPlayerTeamPacket
import net.minecraft.world.phys.AABB
import kotlin.jvm.optionals.getOrNull

object LocationUtils {
    @JvmStatic
    val onHypixel get() = mc.player?.connection?.serverBrand()?.contains("hypixel", ignoreCase = true) == true

    @JvmField
    var inSkyblock = false

    @JvmField
    var world: WorldType? = null

    @JvmField
    var inDungeon = false

    @JvmField
    var dungeonFloor: String? = null

    @JvmField
    var dungeonFloorNumber: Int? = null

    @JvmStatic
    val isMasterMode get() = dungeonFloor?.startsWith("M") == true

    @JvmField
    var inBoss = false

    @JvmField
    var P3Section: Int? = null

    @JvmField
    var F7Phase: Int? = null

    @JvmField
    var serverId: String? = null

    private val lobbyRegex = Regex("\\d\\d/\\d\\d/\\d\\d (\\w{0,6}) *")

    init {
        EventBus.register<MainThreadPacketReceivedEvent.Post>(EventPriority.HIGHEST) {
            if (RenderOptimiser.isDev) return@register setDevModeValues()
            if (! onHypixel) return@register

            if (event.packet is ClientboundPlayerInfoUpdatePacket) {
                val actions = event.packet.actions()
                if (actions.contains(ClientboundPlayerInfoUpdatePacket.Action.UPDATE_DISPLAY_NAME) || actions.contains(ClientboundPlayerInfoUpdatePacket.Action.ADD_PLAYER)) {
                    val area = event.packet.entries().find { it.displayName?.string?.startsWithOneOf("Area: ", "Dungeon: ") == true }?.displayName?.string ?: return@register
                    world = WorldType.entries.firstOrNull { area.remove("Area: ", "Dungeon: ") == it.tabName }
                }
            }
            else if (event.packet is ClientboundSetPlayerTeamPacket) {
                val prams = event.packet.parameters.getOrNull() ?: return@register
                val text = (prams.playerPrefix.string + prams.playerSuffix.string).removeFormatting()
                lobbyRegex.find(text)?.groupValues?.get(1)?.let { serverId = it }

                if (! inDungeon && text.contains("The Catacombs (") && ! text.contains("Queue")) {
                    inDungeon = true
                    dungeonFloor = text.substringAfter("(").substringBefore(")")
                    dungeonFloorNumber = dungeonFloor?.lastOrNull()?.digitToIntOrNull() ?: 0
                }
            }
            else if (event.packet is ClientboundSetObjectivePacket) {
                if (! inSkyblock) inSkyblock = onHypixel && event.packet.objectiveName == "SBScoreboard"
            }
        }

        EventBus.register<TickEvent.Start>(EventPriority.HIGHEST) {
            inBoss = isInBossRoom()
            F7Phase = getPhase()
            P3Section = findP3Section()
        }

        EventBus.register<WorldChangeEvent>(EventPriority.HIGHEST) { reset() }
    }

    private fun reset() {
        inSkyblock = false
        inDungeon = false
        dungeonFloor = null
        dungeonFloorNumber = null
        inBoss = false
        P3Section = null
        F7Phase = null
        world = null
        serverId = null
    }

    private fun setDevModeValues() {
        inSkyblock = true
        inDungeon = true
        dungeonFloor = "F7"
        dungeonFloorNumber = 7
        F7Phase = getPhase()
        P3Section = findP3Section()
        inBoss = isInBossRoom()
    }

    private fun getPhase(): Int? {
        if (dungeonFloorNumber != 7 || ! inBoss) return null
        val y = mc.player?.y ?: return null

        return when {
            y > 210 -> 1
            y > 155 -> 2
            y > 100 -> 3
            y > 45 -> 4
            else -> 5
        }
    }

    private val p3Sections = arrayOf(
        AABB(90.0, 158.0, 123.0, 111.0, 105.0, 32.0),
        AABB(16.0, 158.0, 122.0, 111.0, 105.0, 143.0),
        AABB(19.0, 158.0, 48.0, - 3.0, 106.0, 142.0),
        AABB(91.0, 158.0, 50.0, - 3.0, 106.0, 30.0)
    )

    private fun findP3Section(): Int? {
        if (F7Phase != 3) return null
        val playerPos = mc.player?.position() ?: return null

        for (i in p3Sections.indices) {
            if (p3Sections[i].contains(playerPos)) {
                return i + 1
            }
        }

        return null
    }

    private val bossRoomBounds = arrayOf(
        AABB(- 14.0, 55.0, 49.0, - 72.0, 146.0, - 40.0),
        AABB(- 40.0, 99.0, - 40.0, 24.0, 54.0, 59.0),
        AABB(- 40.0, 118.0, - 40.0, 42.0, 64.0, 37.0),
        AABB(- 40.0, 112.0, - 40.0, 50.0, 53.0, 47.0),
        AABB(- 40.0, 112.0, - 8.0, 50.0, 53.0, 118.0),
        AABB(- 40.0, 51.0, - 8.0, 22.0, 110.0, 134.0),
        AABB(- 8.0, 0.0, - 8.0, 134.0, 254.0, 147.0)
    )

    private fun isInBossRoom(): Boolean {
        val floor = dungeonFloorNumber ?: return false
        if (floor !in 1 .. 7) return false
        val playerPos = mc.player?.position() ?: return false
        return bossRoomBounds[floor - 1].contains(playerPos)
    }
}