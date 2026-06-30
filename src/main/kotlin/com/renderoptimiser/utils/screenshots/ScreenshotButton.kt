package com.renderoptimiser.utils.screenshots

import net.minecraft.client.gui.components.AbstractWidget

/**
 * Positions the Screenshots [button] inside the vanilla icon strip (the small square buttons on the
 * pause / title menu) and re-centres that row so it stays balanced. Shared by MixinPauseScreen and
 * MixinTitleScreen. Does NOT add the widget — the caller (a mixin with access to Screen#addRenderableWidget)
 * does that after calling this.
 */
object ScreenshotButton {

    /**
     * @param children the target screen's widgets (Screen#children())
     * @param screenWidth Screen#width, used to re-centre the row
     * @param button the already-built (but not-yet-added) icon button to place
     */
    @JvmStatic
    fun place(children: List<*>, screenWidth: Int, button: AbstractWidget) {
        // Small square widgets = the vanilla icon strip. Group by y; the biggest same-row cluster is the strip.
        val small = children.filterIsInstance<AbstractWidget>().filter { it.width in 1..24 && it.height in 1..24 }
        val row = small.groupBy { it.y }.values.maxByOrNull { it.size }?.sortedBy { it.x }

        if (row == null || row.size < 2) {
            // No icon strip (e.g. 26.1.2 pause menu) — fall back to the top-left corner.
            button.x = 6
            button.y = 6
            return
        }

        val first = row.first()
        val last = row.last()
        val spacing = 4
        val newWidth = (last.x + last.width - first.x) + spacing + button.width
        val shift = (screenWidth - newWidth) / 2 - first.x
        row.forEach { it.x += shift }          // slide the existing icons left to keep the row centred
        button.x = last.x + last.width + spacing
        button.y = first.y
    }
}
