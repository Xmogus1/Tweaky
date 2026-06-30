package com.renderoptimiser.features.impl.visual

import com.renderoptimiser.event.impl.WorldChangeEvent
import com.renderoptimiser.features.Feature
import com.renderoptimiser.utils.render.Render2D
import com.renderoptimiser.utils.render.Render2D.width

object CpsDisplay: Feature("Displays your left and right clicks per second.") {
    private val leftClicks = mutableListOf<Long>()
    private val rightClicks = mutableListOf<Long>()

    override fun init() {
        hudElement("CPS Display") { ctx, _ ->
            val l = getCps(leftClicks)
            val r = getCps(rightClicks)
            val text = "§f$l §7| §f$r §bCPS"
            Render2D.drawString(ctx, text, 2f, 2f)
            return@hudElement text.width().toFloat() + 4f to 12f
        }

        register<WorldChangeEvent> {
            leftClicks.clear()
            rightClicks.clear()
        }
    }

    private fun getCps(list: MutableList<Long>): Int {
        val now = System.currentTimeMillis()
        list.removeIf { now - it > 1000 }
        return list.size
    }

    @JvmStatic
    fun addLeftClick() {
        if (! enabled) return
        leftClicks.add(System.currentTimeMillis())
    }

    @JvmStatic
    fun addRightClick() {
        if (! enabled) return
        rightClicks.add(System.currentTimeMillis())
    }
}