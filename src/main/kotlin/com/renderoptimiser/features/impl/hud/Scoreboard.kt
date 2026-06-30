package com.renderoptimiser.features.impl.hud

import com.renderoptimiser.event.impl.MainThreadPacketReceivedEvent
import com.renderoptimiser.features.Feature
import com.renderoptimiser.ui.clickgui.components.*
import com.renderoptimiser.ui.clickgui.components.Style
import com.renderoptimiser.ui.clickgui.components.impl.ColorSetting
import com.renderoptimiser.ui.clickgui.components.impl.DropdownSetting
import com.renderoptimiser.ui.clickgui.components.impl.SliderSetting
import com.renderoptimiser.ui.clickgui.components.impl.ToggleSetting
import com.renderoptimiser.ui.hud.HudElement
import com.renderoptimiser.utils.ChatUtils.formattedText
import com.renderoptimiser.utils.render.Render2D
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.network.chat.Component
import net.minecraft.network.protocol.game.*
import net.minecraft.world.scores.DisplaySlot
import net.minecraft.world.scores.Objective
import net.minecraft.world.scores.PlayerTeam
import java.awt.Color
import net.minecraft.world.scores.Scoreboard as MCScoreboard

object Scoreboard: Feature("Draws a custom scoreboard instead of the vanilla one.") {

    // ---- Title ----
    private val titleBox by ToggleSetting("Title Box", true).section("Title")
        .withDescription("Give the title its own boxed strip at the top.")
    private val titleBoxColor by ColorSetting("Title Box Color", Color(28, 31, 38, 230))
        .showIf { titleBox.value }

    // ---- Colors ----
    private val backgroundColor by ColorSetting("Background", Color(15, 15, 15, 190)).section("Style")
    private val accentLine by DropdownSetting("Accent Line", 1, listOf("Off", "Top", "Under Title", "Left", "Top + Left"))
        .withDescription("Where the accent-colored line is drawn (uses the menu accent color).")

    // ---- Border ----
    private val border by ToggleSetting("Border", true).section("Border")
    private val accentBorder by ToggleSetting("Accent Border", false)
        .withDescription("Use the menu accent color for the border.").showIf { border.value }
    private val borderColor by ColorSetting("Border Color", Color(255, 255, 255, 40))
        .showIf { border.value && !accentBorder.value }
    private val borderThickness by SliderSetting("Border Thickness", 1, 1, 3, 1)
        .showIf { border.value }

    // ---- Rows ----
    private val rowTint by ToggleSetting("Row Tint", false).section("Rows")
        .withDescription("Subtle alternating tint behind every other line.")
    private val showNumbers by ToggleSetting("Show Numbers", false)
        .withDescription("Show the red score numbers on the right, like vanilla.")
        .onChange { needsUpdate = true }
    private val textShadow by ToggleSetting("Text Shadow", false)
    private val lineSpacing by SliderSetting("Line Spacing", 2, 0, 6, 1)
        .onChange { needsUpdate = true }
    private val padding by SliderSetting("Padding", 8, 4, 14, 1)
        .onChange { needsUpdate = true }

    private var needsUpdate = true
    private val cachedLines = mutableListOf<String>()
    private val cachedNumbers = mutableListOf<String>()
    private var cachedTitle = ""
    private var cachedW = 0f
    private var cachedH = 0f

    private fun titleStripHeight() = mc.font.lineHeight + padding.value.toFloat()
    private fun lineHeight() = (mc.font.lineHeight + lineSpacing.value).toFloat()

    @Suppress("RemoveRedundantQualifierName")
    private val hud = object: HudElement() {
        override val name = "Scoreboard"
        override val toggle get() = Scoreboard.enabled

        override fun draw(ctx: GuiGraphicsExtractor, example: Boolean): Pair<Float, Float> {
            val scoreboard = mc.level?.scoreboard ?: return 0f to 0f
            val objective = scoreboard.getDisplayObjective(DisplaySlot.SIDEBAR) ?: return 0f to 0f

            if (needsUpdate) updateCache(scoreboard, objective)
            if (cachedLines.isEmpty()) return 0f to 0f

            val w = cachedW.toDouble()
            val h = cachedH.toDouble()
            val x0 = -w - 5
            val y0 = -(h / 2)
            val pad = padding.value.toFloat()
            val stripH = titleStripHeight()
            val lineH = lineHeight()
            val shadow = textShadow.value
            val accent = Style.accentColor

            // Background + title strip
            Render2D.drawRect(ctx, x0, y0, w, h, backgroundColor.value)
            if (titleBox.value) Render2D.drawRect(ctx, x0, y0, w, stripH.toDouble(), titleBoxColor.value)

            // Title, centered in its strip
            Render2D.drawCenteredString(
                ctx, cachedTitle,
                (x0 + w / 2).toFloat(),
                (y0 + (stripH - mc.font.lineHeight) / 2f + 1f).toFloat(),
                shadow = shadow
            )

            // Accent line placement
            when (accentLine.value) {
                1 -> Render2D.drawRect(ctx, x0, y0, w, 2.0, accent)                              // Top
                2 -> Render2D.drawRect(ctx, x0, y0 + stripH, w, 1.5, accent)                     // Under Title
                3 -> Render2D.drawRect(ctx, x0, y0, 2.0, h, accent)                              // Left
                4 -> {                                                                            // Top + Left
                    Render2D.drawRect(ctx, x0, y0, w, 2.0, accent)
                    Render2D.drawRect(ctx, x0, y0, 2.0, h, accent)
                }
            }

            // Rows
            val startY = y0 + stripH + 4
            cachedLines.forEachIndexed { index, text ->
                val rowY = startY + (index * lineH)
                if (rowTint.value && index % 2 == 0) {
                    Render2D.drawRect(ctx, x0, rowY - 1, w, lineH.toDouble(), Color(255, 255, 255, 10))
                }
                Render2D.drawString(ctx, text, (x0 + pad).toFloat(), rowY.toFloat(), shadow = shadow)
                if (showNumbers.value) {
                    val num = cachedNumbers.getOrNull(index) ?: ""
                    if (num.isNotEmpty()) {
                        val numW = mc.font.width(num)
                        Render2D.drawString(ctx, num, (x0 + w - pad - numW).toFloat(), rowY.toFloat(), shadow = shadow)
                    }
                }
            }

            // Border (drawn last, on top, wrapping the whole box)
            if (border.value) {
                val t = borderThickness.value
                val bc = if (accentBorder.value) accent else borderColor.value
                Render2D.drawBorder(ctx, x0 - t, y0 - t, w + t * 2, h + t * 2, bc, t)
            }

            return cachedW to cachedH
        }

        override fun isHovered(mx: Int, my: Int): Boolean {
            if (cachedW == 0f) return false
            val visualWidth = cachedW * scale
            val visualHeight = cachedH * scale
            return mx >= x - visualWidth - 5 && mx <= x && my >= y - (visualHeight / 2) && my <= y + (visualHeight / 2)
        }

        override fun drawBackground(ctx: GuiGraphicsExtractor, mx: Int, my: Int) {
            if (cachedW == 0f) return
            val scaledW = cachedW * scale
            val scaledH = cachedH * scale
            val drawX = x - scaledW - 5
            val drawY = y - (scaledH / 2)

            val hovered = mx >= drawX && mx <= drawX + scaledW && my >= drawY && my <= drawY + scaledH
            val borderColor = if (isDragging || hovered) Style.accentColor else Color(255, 255, 255, 40)

            Render2D.drawRect(ctx, drawX.toDouble(), drawY.toDouble(), scaledW.toDouble(), scaledH.toDouble(), Color(10, 10, 10, 150))
            Render2D.drawRect(ctx, drawX.toDouble(), drawY.toDouble(), scaledW.toDouble(), 1.0, borderColor)
            Render2D.drawRect(ctx, drawX.toDouble(), (drawY + scaledH - 1).toDouble(), scaledW.toDouble(), 1.0, borderColor)
        }
    }

    override fun init() {
        hud.x = 200f
        hud.y = 200f
        hudElements.add(hud)

        register<MainThreadPacketReceivedEvent.Post> {
            if (
                event.packet is ClientboundSetScorePacket || event.packet is ClientboundSetObjectivePacket ||
                event.packet is ClientboundSetDisplayObjectivePacket || event.packet is ClientboundResetScorePacket ||
                event.packet is ClientboundSetPlayerTeamPacket
            ) {
                needsUpdate = true
            }
        }
    }

    private fun updateCache(scoreboard: MCScoreboard, objective: Objective) {
        cachedLines.clear()
        cachedNumbers.clear()
        cachedTitle = objective.displayName.formattedText

        val scores = scoreboard.listPlayerScores(objective).sortedByDescending { it.value }.take(15)

        if (scores.isEmpty()) {
            cachedW = 0f
            cachedH = 0f
            needsUpdate = false
            return
        }

        val font = mc.font
        var maxW = font.width(cachedTitle).toFloat()

        scores.forEach { score ->
            val name = score.ownerName().string
            val team = scoreboard.getPlayersTeam(name)
            val line = PlayerTeam.formatNameForTeam(team, Component.literal(name)).formattedText
            val number = "§c${score.value}"

            cachedLines.add(line)
            cachedNumbers.add(number)

            var lineW = font.width(line).toFloat()
            if (showNumbers.value) lineW += font.width(number).toFloat() + 6f
            maxW = maxOf(maxW, lineW)
        }

        val pad = padding.value.toFloat()
        cachedW = maxW + (pad * 2)
        cachedH = titleStripHeight() + 4f + (cachedLines.size * lineHeight()) + (pad / 2f)
        needsUpdate = false
    }
}
