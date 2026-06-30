package com.renderoptimiser.features.impl.general

import com.renderoptimiser.event.impl.ChatMessageEvent
import com.renderoptimiser.event.impl.RenderWorldEvent
import com.renderoptimiser.features.Feature
import com.renderoptimiser.ui.clickgui.components.impl.SliderSetting
import com.renderoptimiser.ui.clickgui.components.impl.ToggleSetting
import com.renderoptimiser.ui.notification.NotificationManager
import com.renderoptimiser.utils.ChatUtils
import com.renderoptimiser.utils.render.Render3D
import net.minecraft.world.phys.Vec3
import java.awt.Color

object ChatWaypoints: Feature("Click coords in chat to show a waypoint.") {
    private val duration by SliderSetting("Waypoint duration", 15, 15, 300, 5)
        .withDescription("How many seconds a clicked waypoint stays visible.")
    private val removeWhenReached by ToggleSetting("Remove when reached", true)
        .withDescription("Delete a waypoint once you get close to it.")
    private val reachDistance by SliderSetting("Reach distance", 3, 1, 10, 1)
        .withDescription("How close (blocks) counts as reached.").showIf { removeWhenReached.value }

    // Three numbers (optionally decimal, optionally x/y/z labelled) separated by commas/spaces.
    private val coordRegex = Regex("""(-?\d{1,8})(?:\.\d+)?[,\s]+[yY]?\s*:?\s*(-?\d{1,4})(?:\.\d+)?[,\s]+[zZ]?\s*:?\s*(-?\d{1,8})(?:\.\d+)?""")

    private data class Waypoint(val pos: Vec3, val label: String, val expiresAt: Long)
    private val waypoints = mutableListOf<Waypoint>()

    override fun init() {
        register<ChatMessageEvent> {
            val text = event.unformattedText
            if ("show a waypoint" in text) return@register   // ignore our own prompt

            val match = coordRegex.find(text) ?: return@register
            val x = match.groupValues[1].toIntOrNull() ?: return@register
            val y = match.groupValues[2].toIntOrNull() ?: return@register
            val z = match.groupValues[3].toIntOrNull() ?: return@register
            if (y < - 64 || y > 320) return@register   // skip implausible Y to cut false positives

            ChatUtils.clickableChat(
                message = "§9§lTWEAKY§r §7Click to show a waypoint at §b$x, $y, $z",
                command = "/tweaky waypoint $x $y $z",
                hover = "§7Shows a waypoint for §f${duration.value}s§7. Click again to remove."
            )
        }

        register<RenderWorldEvent> {
            if (waypoints.isEmpty()) return@register

            val now = System.currentTimeMillis()
            val pp = mc.player?.position()
            if (removeWhenReached.value && pp != null) {
                val reachSq = (reachDistance.value * reachDistance.value).toDouble()
                waypoints.removeAll { it.expiresAt <= now || pp.distanceToSqr(it.pos.add(0.5, 0.5, 0.5)) <= reachSq }
            }
            else {
                waypoints.removeAll { it.expiresAt <= now }
            }

            val ctx = event.ctx
            val outline = Color(0, 200, 255)
            val fill = Color(0, 200, 255, 55)

            for (wp in waypoints) {
                val secondsLeft = ((wp.expiresAt - now) / 1000L) + 1
                val cx = wp.pos.x + 0.5
                val cz = wp.pos.z + 0.5
                Render3D.renderBox(ctx, cx, wp.pos.y, cz, 1.0, 1.0, outline, fill, phase = true)
                Render3D.renderTracer(ctx, Vec3(cx, wp.pos.y + 0.5, cz), outline)
                Render3D.renderString(ctx, "§b${wp.label} §7(${secondsLeft}s)", cx, wp.pos.y + 1.4, cz, Color.WHITE, 1.0, true)
            }
        }
    }

    override fun onDisable() {
        super.onDisable()
        waypoints.clear()
    }

    fun addWaypoint(x: Int, y: Int, z: Int) {
        val label = "$x, $y, $z"

        // Toggle: clicking the same coords again removes that waypoint.
        if (waypoints.removeAll { it.label == label }) {
            NotificationManager.push("Waypoint removed", label)
            return
        }

        waypoints.add(Waypoint(Vec3(x.toDouble(), y.toDouble(), z.toDouble()), label, System.currentTimeMillis() + duration.value * 1000L))
        NotificationManager.push("Waypoint set", "$label for ${duration.value}s")
    }

    /** Removes every active waypoint (used by /tweaky waypoint clear). */
    fun clearWaypoints() {
        if (waypoints.isEmpty()) {
            NotificationManager.push("No waypoints", "Nothing to clear")
            return
        }
        val count = waypoints.size
        waypoints.clear()
        NotificationManager.push("Waypoints cleared", "$count removed")
    }
}
