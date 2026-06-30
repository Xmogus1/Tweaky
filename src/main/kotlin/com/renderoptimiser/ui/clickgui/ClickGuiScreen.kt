package com.renderoptimiser.ui.clickgui

import com.renderoptimiser.RenderOptimiser
import com.renderoptimiser.RenderOptimiser.MOD_ID
import com.renderoptimiser.config.Config
import com.renderoptimiser.features.Feature
import com.renderoptimiser.features.FeatureManager
import com.renderoptimiser.features.impl.misc.sound.SoundManager
import com.renderoptimiser.ui.clickgui.components.Setting
import com.renderoptimiser.ui.clickgui.components.Style
import com.renderoptimiser.ui.clickgui.enums.CategoryType
import com.renderoptimiser.ui.gui.SoundManagerScreen
import com.renderoptimiser.ui.utils.Animation
import com.renderoptimiser.ui.utils.Resolution
import com.renderoptimiser.ui.utils.TextInputHandler
import com.renderoptimiser.utils.ColorUtils.withAlpha
import com.renderoptimiser.utils.render.Render2D
import com.mojang.blaze3d.platform.InputConstants
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.input.CharacterEvent
import net.minecraft.client.input.KeyEvent
import net.minecraft.client.input.MouseButtonEvent
import net.minecraft.network.chat.Component
import net.minecraft.resources.Identifier
import org.lwjgl.glfw.GLFW
import java.awt.Color

/**
 * A single fixed config window (sidebar + content) over a dimmed backdrop.
 * Replaces the old scattered draggable panels / floating feature windows.
 */
object ClickGuiScreen: Screen(Component.literal("ClickGUI")) {
    // ---- Palette (flat dark theme, hardcoded except accent = Style.accentColor) ----
    private val windowBg = Color(21, 23, 28)
    private val sidebarBg = Color(16, 18, 22)
    private val rowBg = Color(27, 30, 36)
    private val rowHover = Color(33, 37, 44)
    private val subPanelBg = Color(22, 24, 29)
    private val borderColor = Color(35, 38, 44)
    private val borderColorLight = Color(42, 46, 55)
    private val textPrimary = Color(232, 234, 237)
    private val textMuted = Color(135, 140, 149)
    private val toggleOffTrack = Color(47, 52, 64)
    private val knobColor = Color(255, 255, 255)
    private val sidebarSelected = Color(27, 30, 36)
    private val sidebarHover = Color(22, 24, 29)

    // ---- Layout constants (in Resolution coord space) ----
    private const val windowWidth = 560f
    private const val windowHeight = 360f
    private const val sidebarWidth = 160f
    private const val headerHeight = 40f
    private const val rowHeight = 38f
    private const val rowSpacing = 4f
    private const val contentPadding = 12f
    private const val navItemHeight = 26f

    // Logo texture (placeholder copied from assets/tweaky/icon.png; user can swap this file).
    private val logoTexture = Identifier.fromNamespaceAndPath(MOD_ID, "textures/gui/logo.png")

    // ---- Window position (recomputed each frame, centered + clamped) ----
    private var winX = 0f
    private var winY = 0f

    // ---- State ----
    private val categories: List<CategoryType>
        get() = CategoryType.entries.filter { FeatureManager.getFeaturesByCategory(it).isNotEmpty() }

    private var selectedCategory: CategoryType? = null
    private val expanded = mutableSetOf<Feature>()

    private var scrollTarget = 0f
    private val scrollAnim = Animation(200L)
    private var maxScroll = 0f
    private var contentTop = 0f
    private var contentBottom = 0f
    private var contentLeft = 0f
    private var contentRight = 0f

    var searchQuery = ""

    private val searchHandler = TextInputHandler(
        textProvider = { searchQuery },
        textSetter = { searchQuery = it }
    )

    // ---- External API kept for compatibility (referenced by old call-sites) ----
    var selectedFeature: Feature? = null
        set(value) {
            field = null
            if (value != null) expanded.add(value)
        }

    fun openFeatureWindow(feature: Feature, preferredX: Float? = null, preferredY: Float? = null) {
        if (feature.configSettings.isNotEmpty()) expanded.add(feature)
    }

    fun isMouseOverConfigWindow(mouseX: Int, mouseY: Int): Boolean {
        return mouseX >= winX && mouseX <= winX + windowWidth && mouseY >= winY && mouseY <= winY + windowHeight
    }

    private fun ensureSelectedCategory() {
        val cats = categories
        if (cats.isEmpty()) {
            selectedCategory = null
            return
        }
        if (selectedCategory == null || selectedCategory !in cats) selectedCategory = cats.first()
    }

    private fun displayName(category: CategoryType): String =
        if (category == CategoryType.FLOOR7) "Floor 7" else category.name.lowercase().replaceFirstChar { it.uppercase() }

    private fun visibleFeatures(): List<Feature> {
        val cat = selectedCategory ?: return emptyList()
        return FeatureManager.getFeaturesByCategory(cat)
            .filter { searchQuery.isEmpty() || it.name.contains(searchQuery, ignoreCase = true) }
            .sortedBy { it.name }
    }

    private fun visibleSettingsOf(feature: Feature): List<Setting<*>> =
        feature.configSettings.filter { it.visibility.invoke() }

    // =====================================================================
    // Render
    // =====================================================================
    override fun extractRenderState(context: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, a: Float) {
        Resolution.refresh()
        Resolution.push(context)
        val mX = Resolution.getMouseX(mouseX.toDouble())
        val mY = Resolution.getMouseY(mouseY.toDouble())

        ensureSelectedCategory()
        TooltipManager.reset()

        // Dimmed backdrop
        context.fillGradient(0, 0, Resolution.width.toInt(), Resolution.height.toInt(), Color(0, 0, 0, 140).rgb, Color(0, 0, 0, 170).rgb)

        winX = ((Resolution.width - windowWidth) / 2f).coerceAtLeast(0f)
        winY = ((Resolution.height - windowHeight) / 2f).coerceAtLeast(0f)

        // Window shell
        Render2D.drawRect(context, winX, winY, windowWidth, windowHeight, windowBg)
        Render2D.drawBorder(context, winX, winY, windowWidth, windowHeight, borderColorLight)

        drawSidebar(context, mX, mY)
        drawContent(context, mX, mY)

        TooltipManager.draw(context, Resolution.width, Resolution.height)
        Resolution.pop(context)
    }

    private fun drawSidebar(context: GuiGraphicsExtractor, mX: Int, mY: Int) {
        val sx = winX
        val sy = winY
        Render2D.drawRect(context, sx, sy, sidebarWidth, windowHeight, sidebarBg)
        Render2D.drawRect(context, sx + sidebarWidth - 1f, sy, 1f, windowHeight, borderColor)

        // Title + logo image (vertically centered with the title text)
        val logoSize = 24f
        val logoX = sx + 14f
        val logoY = sy + 13f
        Render2D.drawTexture(context, logoTexture, logoX, logoY, logoSize, logoSize)
        Render2D.drawString(
            context,
            "§lTweaky",
            logoX + logoSize + 8f,
            logoY + (logoSize / 2f) - 4f,
            textPrimary
        )

        // Category nav
        var ny = sy + 54f
        categories.forEach { category ->
            val selected = category == selectedCategory
            val hovered = mX >= sx && mX <= sx + sidebarWidth && mY >= ny && mY <= ny + navItemHeight

            if (selected) {
                Render2D.drawRect(context, sx, ny, sidebarWidth, navItemHeight, sidebarSelected)
                Render2D.drawRect(context, sx, ny, 3f, navItemHeight, Style.accentColor)
            }
            else if (hovered) {
                Render2D.drawRect(context, sx, ny, sidebarWidth, navItemHeight, sidebarHover)
            }

            val labelColor = if (selected) textPrimary else textMuted
            val label = if (selected) "§l${displayName(category)}" else displayName(category)
            Render2D.drawString(context, label, sx + 18f, ny + (navItemHeight / 2f) - 4f, labelColor)

            ny += navItemHeight
        }

        // Version (bottom)
        val version = runCatching { RenderOptimiser.MOD_VERSION }.getOrDefault("")
        if (version.isNotEmpty()) {
            Render2D.drawString(context, "v$version", sx + 14f, sy + windowHeight - 16f, textMuted, 0.85f)
        }
    }

    private fun drawContent(context: GuiGraphicsExtractor, mX: Int, mY: Int) {
        val cx = winX + sidebarWidth
        val cy = winY
        val cw = windowWidth - sidebarWidth

        // Header row: category name (left) + search box (right)
        val titleText = selectedCategory?.let { displayName(it) } ?: "Render Optimiser"
        Render2D.drawString(context, "§l$titleText", cx + contentPadding, cy + 14f, textPrimary)
        drawSearchBox(context, cx, cy, cw, mX, mY)
        Render2D.drawRect(context, cx, cy + headerHeight, cw, 1f, borderColor)

        // Scrollable content region
        contentLeft = cx + contentPadding
        contentRight = cx + cw - contentPadding
        contentTop = cy + headerHeight + contentPadding
        contentBottom = cy + windowHeight - contentPadding
        val viewportWidth = (contentRight - contentLeft).coerceAtLeast(60f)
        val viewportHeight = (contentBottom - contentTop).coerceAtLeast(40f)

        val features = visibleFeatures()

        // Measure total content height
        var totalHeight = 0f
        features.forEach { feature ->
            totalHeight += rowHeight
            if (feature in expanded) totalHeight += expandedHeight(feature)
            totalHeight += rowSpacing
        }
        if (totalHeight > 0f) totalHeight -= rowSpacing

        maxScroll = (totalHeight - viewportHeight).coerceAtLeast(0f)
        scrollTarget = scrollTarget.coerceIn(0f, maxScroll)
        scrollAnim.update(scrollTarget)
        if (scrollAnim.value > maxScroll) scrollAnim.set(maxScroll)

        val scrollbarReserve = if (maxScroll > 0f) 6f else 0f
        val rowWidth = viewportWidth - scrollbarReserve

        context.enableScissor(contentLeft.toInt(), contentTop.toInt(), contentRight.toInt(), contentBottom.toInt())

        if (features.isEmpty()) {
            Render2D.drawCenteredString(
                context,
                "§8No features here",
                contentLeft + (viewportWidth / 2f),
                contentTop + (viewportHeight / 2f) - 4f,
                textMuted,
                shadow = false
            )
        }
        else {
            var rowY = contentTop - scrollAnim.value
            features.forEach { feature ->
                val visibleOnScreen = rowY + rowHeight > contentTop && rowY < contentBottom
                val isExpanded = feature in expanded
                val expH = if (isExpanded) expandedHeight(feature) else 0f

                if (rowY + rowHeight + expH > contentTop && rowY < contentBottom) {
                    drawFeatureRow(context, feature, contentLeft, rowY, rowWidth, mX, mY, isExpanded)
                    if (isExpanded) {
                        drawExpandedSettings(context, feature, contentLeft, rowY + rowHeight, rowWidth, expH, mX, mY)
                    }
                }
                rowY += rowHeight + expH + rowSpacing
            }
        }

        context.disableScissor()

        // Scrollbar
        if (maxScroll > 0f) {
            val barWidth = 3f
            val barX = contentRight - barWidth
            val thumbHeight = ((viewportHeight / totalHeight) * viewportHeight).coerceAtLeast(20f)
            val thumbTravel = (viewportHeight - thumbHeight).coerceAtLeast(0f)
            val thumbY = contentTop + ((scrollAnim.value / maxScroll) * thumbTravel)
            Render2D.drawRect(context, barX, contentTop, barWidth, viewportHeight, Color(255, 255, 255, 12))
            Render2D.drawRect(context, barX, thumbY, barWidth, thumbHeight, Style.accentColor.withAlpha(160))
        }
    }

    private fun drawSearchBox(context: GuiGraphicsExtractor, cx: Float, cy: Float, cw: Float, mX: Int, mY: Int) {
        val bw = 150f
        val bh = 22f
        val bx = cx + cw - contentPadding - bw
        val by = cy + (headerHeight - bh) / 2f

        Render2D.drawRect(context, bx, by, bw, bh, Color(15, 17, 21))
        Render2D.drawBorder(context, bx, by, bw, bh, if (searchHandler.listening) Style.accentColor else borderColor)
        // Focus underline
        Render2D.drawRect(context, bx, by + bh - 1f, bw, 1f, if (searchHandler.listening) Style.accentColor else borderColor)

        searchHandler.x = bx + 6f
        searchHandler.y = by
        searchHandler.width = bw - 12f
        searchHandler.height = bh

        if (searchQuery.isEmpty() && !searchHandler.listening) {
            Render2D.drawString(context, "§8Search...", bx + 8f, by + (bh / 2f) - 4f, textMuted, shadow = false)
        }
        else {
            searchHandler.draw(context, mX.toFloat(), mY.toFloat())
        }
    }

    private fun drawFeatureRow(
        context: GuiGraphicsExtractor,
        feature: Feature,
        x: Float,
        y: Float,
        width: Float,
        mX: Int,
        mY: Int,
        isExpanded: Boolean
    ) {
        val hovered = mX >= x && mX <= x + width && mY >= y && mY <= y + rowHeight &&
            mY >= contentTop && mY <= contentBottom
        Render2D.drawRect(context, x, y, width, rowHeight, if (hovered) rowHover else rowBg)
        if (isExpanded) Render2D.drawRect(context, x, y, 2f, rowHeight, Style.accentColor.withAlpha(180))

        // Name + description
        Render2D.drawString(context, "§l${feature.name}", x + 12f, y + 7f, textPrimary)
        feature.description?.let { desc ->
            Render2D.drawString(context, desc, x + 12f, y + 22f, textMuted, 0.85f, shadow = false)
        }

        // Toggle pill (right)
        drawToggle(context, feature.enabled, x + width - toggleWidth - 12f, y + (rowHeight - toggleHeight) / 2f)
    }

    private const val toggleWidth = 28f
    private const val toggleHeight = 14f

    private fun drawToggle(context: GuiGraphicsExtractor, enabled: Boolean, tx: Float, ty: Float) {
        val track = if (enabled) Style.accentColor else toggleOffTrack
        Render2D.drawRect(context, tx, ty, toggleWidth, toggleHeight, track)
        val knobSize = toggleHeight - 4f
        val knobX = if (enabled) tx + toggleWidth - knobSize - 2f else tx + 2f
        Render2D.drawRect(context, knobX, ty + 2f, knobSize, knobSize, knobColor)
    }

    private fun expandedHeight(feature: Feature): Float {
        val settings = visibleSettingsOf(feature)
        if (settings.isEmpty()) return 28f
        val inner = settings.sumOf { it.height }.toFloat()
        return inner + (settingsPaddingTop + settingsPaddingBottom)
    }

    private const val settingsPaddingTop = 8f
    private const val settingsPaddingBottom = 8f
    private const val settingsIndent = 12f

    private fun drawExpandedSettings(
        context: GuiGraphicsExtractor,
        feature: Feature,
        x: Float,
        y: Float,
        width: Float,
        height: Float,
        mX: Int,
        mY: Int
    ) {
        Render2D.drawRect(context, x, y, width, height, subPanelBg)
        Render2D.drawRect(context, x, y, 2f, height, Style.accentColor.withAlpha(120))

        val settings = visibleSettingsOf(feature)
        if (settings.isEmpty()) {
            Render2D.drawString(context, "§8No visible settings", x + settingsIndent + 4f, y + 10f, textMuted, shadow = false)
            return
        }

        val settingX = (x + settingsIndent).toInt()
        val settingWidth = (width - settingsIndent - 8f).toInt().coerceAtLeast(60)
        var sy = y + settingsPaddingTop

        settings.forEach { setting ->
            setting.x = settingX
            setting.y = sy.toInt()
            setting.width = settingWidth
            setting.draw(context, mX, mY)

            val hovered = mX >= setting.x && mX <= setting.x + setting.width &&
                mY >= setting.y && mY <= setting.y + setting.height &&
                mY >= contentTop && mY <= contentBottom
            if (hovered) TooltipManager.hover(setting.description, mX, mY)

            sy += setting.height
        }
    }

    // =====================================================================
    // Input
    // =====================================================================
    override fun mouseClicked(mouseButtonEvent: MouseButtonEvent, bl: Boolean): Boolean {
        val mx = Resolution.getMouseX(mouseButtonEvent.x)
        val my = Resolution.getMouseY(mouseButtonEvent.y)
        val button = mouseButtonEvent.button()

        // 1) Settings of expanded features (highest priority so dropdowns/sliders work)
        if (insideContent(mx, my)) {
            for (feature in visibleFeatures()) {
                if (feature !in expanded) continue
                for (setting in visibleSettingsOf(feature)) {
                    if (setting.mouseClicked(mx.toDouble(), my.toDouble(), button)) {
                        searchHandler.resetState()
                        return true
                    }
                }
            }
        }

        // 2) Search box
        if (searchHandler.mouseClicked(mx.toFloat(), my.toFloat(), mouseButtonEvent)) return true

        // 3) Sidebar category nav
        if (mx >= winX && mx <= winX + sidebarWidth && my >= winY && my <= winY + windowHeight) {
            var ny = winY + 54f
            for (category in categories) {
                if (my >= ny && my <= ny + navItemHeight && button == 0) {
                    if (selectedCategory != category) {
                        selectedCategory = category
                        scrollTarget = 0f
                        scrollAnim.set(0f)
                    }
                    Style.playClickSound(1f)
                    searchHandler.resetState()
                    return true
                }
                ny += navItemHeight
            }
            return true
        }

        // 4) Feature rows (toggle area vs body)
        if (insideContent(mx, my)) {
            val rowWidth = (contentRight - contentLeft) - (if (maxScroll > 0f) 6f else 0f)
            var rowY = contentTop - scrollAnim.value
            for (feature in visibleFeatures()) {
                val isExpanded = feature in expanded
                val expH = if (isExpanded) expandedHeight(feature) else 0f
                if (my >= rowY && my <= rowY + rowHeight) {
                    handleRowClick(feature, mx.toFloat(), contentLeft, rowY, rowWidth, button)
                    searchHandler.resetState()
                    return true
                }
                rowY += rowHeight + expH + rowSpacing
            }
        }

        // Click anywhere else inside the window: consume (keeps it modal-feeling)
        if (isMouseOverConfigWindow(mx, my)) {
            searchHandler.resetState()
            return true
        }

        searchHandler.resetState()
        return super.mouseClicked(mouseButtonEvent, bl)
    }

    private fun handleRowClick(feature: Feature, mx: Float, x: Float, y: Float, width: Float, button: Int) {
        val toggleX = x + width - toggleWidth - 12f
        val onToggle = mx >= toggleX - 4f && mx <= x + width

        if (onToggle) {
            feature.toggle()
            Style.playClickSound(if (feature.enabled) 1.1f else 0.9f)
            return
        }

        // Body click -> expand/collapse settings, or open SoundManager screen
        if (feature is SoundManager) {
            RenderOptimiser.screen = SoundManagerScreen()
            return
        }

        if (feature.configSettings.isEmpty()) {
            // Nothing to expand: treat body click as toggle for convenience
            feature.toggle()
            Style.playClickSound(if (feature.enabled) 1.1f else 0.9f)
            return
        }

        if (feature in expanded) expanded.remove(feature) else expanded.add(feature)
        Style.playClickSound(if (feature in expanded) 1.05f else 0.95f)
    }

    override fun mouseReleased(mouseButtonEvent: MouseButtonEvent): Boolean {
        val button = mouseButtonEvent.button()
        searchHandler.mouseReleased()
        FeatureManager.features.forEach { feature ->
            feature.configSettings.forEach { it.mouseReleased(button) }
        }
        return super.mouseReleased(mouseButtonEvent)
    }

    override fun mouseScrolled(mouseX: Double, mouseY: Double, horizontal: Double, vertical: Double): Boolean {
        val mx = Resolution.getMouseX(mouseX)
        val my = Resolution.getMouseY(mouseY)

        // Let expanded settings consume scroll first (e.g. dropdown lists)
        if (insideContent(mx, my)) {
            for (feature in visibleFeatures()) {
                if (feature !in expanded) continue
                for (setting in visibleSettingsOf(feature)) {
                    if (setting.mouseScrolled(mx, my, vertical)) return true
                }
            }
        }

        if (isMouseOverConfigWindow(mx, my)) {
            scrollTarget = (scrollTarget - (vertical * 28).toFloat()).coerceIn(0f, maxScroll)
            return true
        }

        return super.mouseScrolled(mouseX, mouseY, horizontal, vertical)
    }

    override fun charTyped(characterEvent: CharacterEvent): Boolean {
        for (feature in expanded) {
            for (setting in visibleSettingsOf(feature)) {
                if (setting.charTyped(characterEvent.codepoint.toChar())) return true
            }
        }
        if (searchHandler.keyTyped(characterEvent)) return true
        return super.charTyped(characterEvent)
    }

    override fun keyPressed(keyEvent: KeyEvent): Boolean {
        for (feature in expanded) {
            for (setting in visibleSettingsOf(feature)) {
                if (setting.keyPressed(keyEvent.key, keyEvent.scancode, keyEvent.modifiers)) return true
            }
        }

        if (searchHandler.keyPressed(keyEvent)) return true

        if (keyEvent.hasControlDown() && keyEvent.input() == GLFW.GLFW_KEY_F) {
            searchHandler.listening = !searchHandler.listening
            return true
        }

        if (keyEvent.key == InputConstants.KEY_ESCAPE) {
            // ESC collapses any expanded feature first, otherwise closes screen
            if (expanded.isNotEmpty()) {
                expanded.clear()
                return true
            }
        }

        return super.keyPressed(keyEvent)
    }

    private fun insideContent(mx: Int, my: Int): Boolean =
        mx >= contentLeft && mx <= contentRight && my >= contentTop && my <= contentBottom

    override fun onClose() {
        expanded.clear()
        searchHandler.resetState()
        searchQuery = ""
        Config.save()
        super.onClose()
    }

    override fun isPauseScreen(): Boolean = false
}
