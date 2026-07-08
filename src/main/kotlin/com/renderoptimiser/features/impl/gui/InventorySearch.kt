package com.renderoptimiser.features.impl.gui

import com.renderoptimiser.event.impl.ContainerEvent
import com.renderoptimiser.event.impl.KeyboardEvent
import com.renderoptimiser.event.impl.MouseClickEvent
import com.renderoptimiser.event.impl.ScreenEvent
import com.renderoptimiser.features.Feature
import com.renderoptimiser.ui.clickgui.components.Style
import com.renderoptimiser.ui.clickgui.components.impl.ColorSetting
import com.renderoptimiser.ui.clickgui.components.impl.ToggleSetting
import com.renderoptimiser.ui.utils.Resolution
import com.renderoptimiser.ui.utils.TextInputHandler
import com.renderoptimiser.utils.ChatUtils.removeFormatting
import com.renderoptimiser.utils.ChatUtils.unformattedText
import com.renderoptimiser.utils.NumbersUtils
import com.renderoptimiser.utils.items.ItemUtils.lore
import com.renderoptimiser.utils.render.Render2D
import com.renderoptimiser.utils.render.Render2D.highlight
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen
import net.minecraft.client.input.MouseButtonEvent
import net.minecraft.client.input.MouseButtonInfo
import net.minecraft.world.item.ItemStack
import org.lwjgl.glfw.GLFW
import java.awt.Color

object InventorySearch: Feature("Lets you search in inventory and support math") {
    private val ignoreCaps by ToggleSetting("Ignore Caps", true)
    private val searchLore by ToggleSetting("Search Lore")
    private val highlightColor by ColorSetting("Highlight Color", Color.RED)

    private var searchQuery = ""
    private val searchHandler = TextInputHandler({ searchQuery }, { searchQuery = it })
    private var expressionResult: Double? = null

    val color get() = highlightColor.value

    fun matches(stack: ItemStack): Boolean {
        if (searchQuery.isBlank() || stack.isEmpty) return false
        val name = stack.hoverName.unformattedText.contains(searchQuery, ignoreCaps.value)
        val lore = searchLore.value && stack.lore.any { it.removeFormatting().contains(searchQuery, ignoreCaps.value) }
        return name || lore
    }

    private const val WIDTH = 200f
    private const val HEIGHT = 22f

    override fun init() {
        register<ScreenEvent.PostRender> {
            if (mc.gui.screen() !is AbstractContainerScreen<*>) return@register

            Resolution.refresh()
            Resolution.push(event.context)

            val x = (Resolution.width / 2) - (WIDTH / 2)
            val y = (Resolution.height - 30) - (HEIGHT / 2)
            val mx = Resolution.getMouseX()
            val my = Resolution.getMouseY()

            searchHandler.x = x
            searchHandler.y = y
            searchHandler.width = WIDTH
            searchHandler.height = HEIGHT

            Render2D.drawRect(event.context, x, y, WIDTH, HEIGHT, Color(15, 15, 15, 200))
            val color = if (searchHandler.listening) Style.accentColor else Color(255, 255, 255, 30)
            Render2D.drawRect(event.context, x, y + HEIGHT - 1, WIDTH, 1f, color)

            if (searchQuery.isEmpty() && ! searchHandler.listening) Render2D.drawCenteredString(event.context, "§8Search...", x + WIDTH / 2, y + 6)
            else if (expressionResult != null) searchHandler.draw(event.context, mx.toFloat(), my.toFloat(), " = §e${NumbersUtils.formatComma(expressionResult)}")
            else searchHandler.draw(event.context, mx.toFloat(), my.toFloat())

            Resolution.pop(event.context)
        }

        register<MouseClickEvent> {
            if (mc.gui.screen() !is AbstractContainerScreen<*>) return@register
            if (event.action == GLFW.GLFW_RELEASE) searchHandler.mouseReleased()
            if (event.action == GLFW.GLFW_PRESS) {
                searchHandler.mouseClicked(
                    Resolution.getMouseX().toFloat(),
                    Resolution.getMouseY().toFloat(),
                    MouseButtonEvent(0.0, 0.0, MouseButtonInfo(event.button, event.action))
                )
            }
        }

        register<KeyboardEvent.CharTyped> {
            if (mc.gui.screen() !is AbstractContainerScreen<*>) return@register
            if (! searchHandler.listening) return@register

            searchHandler.keyTyped(event.charEvent)
            expressionResult = evaluateExpression(searchQuery)
        }

        register<KeyboardEvent.KeyPressed> {
            if (mc.gui.screen() !is AbstractContainerScreen<*>) return@register

            if (event.keyEvent.key == GLFW.GLFW_KEY_F && event.keyEvent.hasControlDown()) {
                searchHandler.listening = ! searchHandler.listening
                event.isCanceled = true
                return@register
            }

            if (! searchHandler.listening) return@register

            if (mc.options.keyInventory.matches(event.keyEvent)) {
                event.isCanceled = true
            }

            searchHandler.keyPressed(event.keyEvent)
        }

        register<ContainerEvent.Render.Slot.Pre> {
            if (matches(event.slot.item)) event.slot.highlight(event.context, highlightColor.value)
        }
    }

    // Shunting Yard Algorithm
    // This shit is less stable than I am
    private fun evaluateExpression(expr: String): Double? {
        if (expr.isBlank()) return null
        if (expr.none { it.isDigit() }) return null

        val operators = mapOf(
            "+" to 1,
            "-" to 1,
            "*" to 2,
            "x" to 2,
            "/" to 2
        )

        val tokens = mutableListOf<String>()
        var i = 0

        while (i < expr.length) when {
            expr[i].isDigit() || expr[i] == '.' -> {
                val start = i
                while (i < expr.length && (expr[i].isDigit() || expr[i] == '.')) i ++
                if (i < expr.length && expr[i].lowercaseChar() in "kmbt") i ++
                tokens.add(expr.substring(start, i))
            }

            expr[i] in "+-*x/()" -> {
                tokens.add(expr[i].toString())
                i ++
            }

            expr[i].isWhitespace() -> i ++

            else -> return null
        }

        val output = mutableListOf<String>()
        val stack = ArrayDeque<String>()

        for (token in tokens) {
            val numCheck = NumbersUtils.parseCompactNumberDouble(token)
            when {
                numCheck != null -> output.add(token)

                token == "(" -> stack.addFirst(token)

                token == ")" -> {
                    while (stack.isNotEmpty() && stack.first() != "(") output.add(stack.removeFirst())
                    if (stack.isEmpty() || stack.removeFirst() != "(") return null
                }

                token in operators -> {
                    while (stack.isNotEmpty() && stack.first() in operators && operators[token] !! <= operators[stack.first()] !!) {
                        output.add(stack.removeFirst())
                    }
                    stack.addFirst(token)
                }

                else -> return null
            }
        }

        while (stack.isNotEmpty()) {
            if (stack.first() in listOf("(", ")")) return null
            output.add(stack.removeFirst())
        }

        val evalStack = ArrayDeque<Double>()

        for (token in output) {
            val num = NumbersUtils.parseCompactNumberDouble(token)

            if (num != null) evalStack.addFirst(num)
            else if (token in operators) {
                if (evalStack.size < 2) return null

                val b = evalStack.removeFirst()
                val a = evalStack.removeFirst()

                val res = when (token) {
                    "+" -> a + b
                    "-" -> a - b
                    "*", "x" -> a * b
                    "/" -> a / b
                    else -> return null
                }
                evalStack.addFirst(res)

            }
            else return null
        }

        return if (evalStack.size == 1) evalStack.first() else null
    }
}