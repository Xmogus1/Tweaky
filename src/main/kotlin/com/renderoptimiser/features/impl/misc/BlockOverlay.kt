package com.renderoptimiser.features.impl.misc

import com.renderoptimiser.features.Feature
import com.renderoptimiser.ui.clickgui.components.impl.ColorSetting
import com.renderoptimiser.ui.clickgui.components.impl.DropdownSetting
import com.renderoptimiser.ui.clickgui.components.impl.SliderSetting
import com.renderoptimiser.ui.clickgui.components.impl.ToggleSetting
import com.renderoptimiser.utils.ColorUtils.withAlpha
import com.renderoptimiser.utils.Utils
import com.renderoptimiser.utils.equalsOneOf
import com.renderoptimiser.utils.render.Render3D
import com.renderoptimiser.utils.render.RenderContext
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents

object BlockOverlay: Feature() {
    private val mode by DropdownSetting("Mode", 2, listOf("Outline", "Fill", "Filled Outline"))
    private val fillColor by ColorSetting("Fill Color", Utils.favoriteColor.withAlpha(50)).hideIf { mode.value == 0 }
    private val outlineColor by ColorSetting("Outline Color", Utils.favoriteColor, false).hideIf { mode.value == 1 }
    private val lineWidth by SliderSetting("Line Width", 2.5, 1, 10, 0.1).hideIf { mode.value == 1 }
    private val phase by ToggleSetting("Phase")

    override fun init() {
        LevelRenderEvents.BEFORE_BLOCK_OUTLINE.register { context, blockOutlineContext ->
            if (! enabled) return@register true
            if (mc.gui.hud.isHidden) return@register true

            Render3D.renderBlock(
                RenderContext.fromContext(context),
                blockOutlineContext.pos,
                outlineColor.value,
                fillColor.value,
                mode.value.equalsOneOf(0, 2),
                mode.value.equalsOneOf(1, 2),
                phase = phase.value,
                lineWidth.value
            )

            false
        }
    }
}
