package com.renderoptimiser.ui.gui

import com.renderoptimiser.RenderOptimiser.MOD_ID
import com.renderoptimiser.RenderOptimiser.logger
import com.renderoptimiser.RenderOptimiser.mc
import com.renderoptimiser.ui.clickgui.components.Style
import com.renderoptimiser.ui.utils.Animation
import com.renderoptimiser.ui.utils.Resolution
import com.renderoptimiser.utils.ColorUtils.withAlpha
import com.renderoptimiser.utils.ThreadUtils
import com.renderoptimiser.utils.render.Render2D
import com.renderoptimiser.utils.screenshots.ScreenshotActions
import com.mojang.blaze3d.platform.NativeImage
import net.minecraft.client.Screenshot
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.input.KeyEvent
import net.minecraft.client.input.MouseButtonEvent
import net.minecraft.client.renderer.texture.DynamicTexture
import net.minecraft.network.chat.Component
import net.minecraft.resources.Identifier
import org.lwjgl.glfw.GLFW
import java.awt.Color
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min

/**
 * Custom Screenshots gallery. Shows every image in `<gameDir>/screenshots`, newest first, as a scrollable
 * thumbnail grid. Selecting one reveals COPY / SAVE AS / SHARE.
 *
 * Texture lifecycle (all GL work on the render thread — see removed()):
 *  - Thumbnails are uploaded LAZILY and only while visible, bounded by [MAX_LIVE_TEXTURES] (LRU-evicted).
 *  - The selected full preview is uploaded separately and released when the selection changes / on close.
 *  - Every registered [Identifier] is released in [removed] so no GL handle leaks.
 *
 * A fresh instance is created per open (class, not object) so the grid re-reads the folder each time.
 */
class ScreenshotsScreen: Screen(Component.literal("Screenshots")) {

    private companion object {
        const val PANEL_W = 560f
        const val PANEL_H = 400f
        const val THUMB_W = 176f   // sized so the grid auto-computes exactly 3 columns
        const val THUMB_H = 99f
        const val GAP = 8f
        const val HEADER_H = 24f
        /** Hard cap on GPU-resident thumbnail textures; least-recently-drawn are evicted + released. */
        const val MAX_LIVE_TEXTURES = 48
    }

    private val cameraIcon = Identifier.fromNamespaceAndPath(MOD_ID, "textures/gui/screenshots.png")

    /** All screenshot files, newest first. Re-read on init(). */
    private var files: List<File> = emptyList()

    /** file -> registered id, plus native size for aspect-correct drawing. Access on render thread only. */
    private class Loaded(val id: Identifier, val w: Int, val h: Int)
    private val textures = ConcurrentHashMap<File, Loaded>()
    /** Files currently being read off-thread, to avoid double-loading. */
    private val loading = ConcurrentHashMap.newKeySet<File>()
    /** Insertion-ordered draw recency for LRU eviction (most-recent at the end). */
    private val drawOrder = ArrayList<File>()

    private var selected: File? = null
    private var previewLoaded: Loaded? = null

    private var scrollTarget = 0f
    private val scrollAnim = Animation(200L)

    // ------------------------------------------------------------------------------------------ init

    override fun init() {
        super.init()
        refreshFiles()
    }

    private fun refreshFiles() {
        val dir = File(mc.gameDirectory, Screenshot.SCREENSHOT_DIR)
        files = dir.listFiles { f -> f.isFile && f.name.endsWith(".png", true) }
            ?.sortedByDescending { it.lastModified() }
            ?: emptyList()
    }

    // ---------------------------------------------------------------------------------------- render

    override fun extractRenderState(ctx: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, delta: Float) {
        Resolution.refresh()
        Resolution.push(ctx)

        val mX = Resolution.getMouseX(mouseX).toFloat()
        val mY = Resolution.getMouseY(mouseY).toFloat()

        val x = (Resolution.width / 2f) - (PANEL_W / 2f)
        val y = (Resolution.height / 2f) - (PANEL_H / 2f)

        // dimmed backdrop + window chrome
        ctx.fillGradient(0, 0, Resolution.width.toInt(), Resolution.height.toInt(),
            Color(0, 0, 0, 140).rgb, Color(0, 0, 0, 175).rgb)
        Render2D.drawRect(ctx, x, y, PANEL_W, PANEL_H, Color(20, 20, 20, 240))
        Render2D.drawRect(ctx, x, y, PANEL_W, 2f, Style.accentColor)
        Render2D.drawTexture(ctx, cameraIcon, x + 7f, y + 5f, 14f, 14f)
        Render2D.drawCenteredString(ctx, "§lScreenshots", x + PANEL_W / 2f, y + 8f)
        Render2D.drawString(ctx, "§7${files.size} image${if (files.size == 1) "" else "s"}", x + PANEL_W - 60f, y + 8f)

        val sel = selected
        if (sel != null) drawDetail(ctx, sel, x, y, mX, mY)
        else drawGrid(ctx, x, y, mX, mY)

        Resolution.pop(ctx)
    }

    // ----- grid --------------------------------------------------------------------------------------

    private var columns = 4

    private fun drawGrid(ctx: GuiGraphicsExtractor, x: Float, y: Float, mX: Float, mY: Float) {
        val gridX = x + GAP
        val gridY = y + HEADER_H
        val gridW = PANEL_W - GAP * 2
        val gridH = PANEL_H - HEADER_H - GAP

        columns = max(1, ((gridW + GAP) / (THUMB_W + GAP)).toInt())
        val rows = ceil(files.size / columns.toDouble()).toInt()
        val cellH = THUMB_H + GAP + 10f // thumb + gap + filename line
        val totalHeight = rows * cellH

        val maxScroll = if (totalHeight > gridH) totalHeight - gridH else 0f
        scrollTarget = scrollTarget.coerceIn(-maxScroll, 0f)
        scrollAnim.update(scrollTarget)
        val scroll = scrollAnim.value

        if (files.isEmpty()) {
            Render2D.drawCenteredString(ctx, "§7No screenshots yet. Press F2 in-game.", x + PANEL_W / 2f, y + PANEL_H / 2f)
            return
        }

        Resolution.scissor(ctx, gridX, gridY, gridX + gridW, gridY + gridH)

        // Determine visible row window so we only ever touch on-screen files.
        val firstRow = max(0, (-scroll / cellH).toInt())
        val lastRow = min(rows, firstRow + ceil(gridH / cellH).toInt() + 1)

        for (row in firstRow until lastRow) {
            for (col in 0 until columns) {
                val index = row * columns + col
                if (index >= files.size) break
                val file = files[index]

                val cx = gridX + col * (THUMB_W + GAP)
                val cy = gridY + scroll + row * cellH

                val hovered = mX >= cx && mX <= cx + THUMB_W && mY >= cy && mY <= cy + THUMB_H
                Render2D.drawRect(ctx, cx, cy, THUMB_W, THUMB_H, Color(0, 0, 0, 120))

                drawThumb(ctx, file, cx, cy)

                Render2D.drawBorder(ctx, cx, cy, THUMB_W, THUMB_H,
                    if (hovered) Style.accentColor else Color(255, 255, 255, 25))

                val label = file.name.let { if (it.length > 18) it.take(15) + "..." else it }
                Render2D.drawString(ctx, "§7$label", cx, cy + THUMB_H + 1f, scale = 0.75)
            }
        }
        ctx.disableScissor()

        // scrollbar
        if (maxScroll > 0f) {
            val thumbHeight = 20f.coerceAtLeast((gridH / totalHeight) * gridH)
            val thumbY = gridY + (-scroll / maxScroll) * (gridH - thumbHeight)
            Render2D.drawRect(ctx, x + PANEL_W - 4f, gridY, 2f, gridH, Color(255, 255, 255, 15))
            Render2D.drawRect(ctx, x + PANEL_W - 4f, thumbY, 2f, thumbHeight, Style.accentColor)
        }

        evictExcessTextures()
    }

    /** Draws a thumbnail; uploads its texture on first visible frame (bounded/lazy). */
    private fun drawThumb(ctx: GuiGraphicsExtractor, file: File, cx: Float, cy: Float) {
        val loaded = textures[file]
        if (loaded == null) {
            requestLoad(file, thumbnail = true)
            Render2D.drawCenteredString(ctx, "§8...", cx + THUMB_W / 2f, cy + THUMB_H / 2f - 4f)
            return
        }
        markDrawn(file)
        // aspect-fit inside the cell
        val (dw, dh, ox, oy) = fit(loaded.w, loaded.h, THUMB_W, THUMB_H)
        Render2D.drawTexture(ctx, loaded.id, cx + ox, cy + oy, dw, dh)
    }

    // ----- detail / actions --------------------------------------------------------------------------

    //#if CURSEFORGE
    //$private val actionLabels = listOf("Copy", "Save As")
    //#else
    private val actionLabels = listOf("Copy", "Save As", "Share")
    //#endif

    private fun drawDetail(ctx: GuiGraphicsExtractor, file: File, x: Float, y: Float, mX: Float, mY: Float) {
        val areaX = x + GAP
        val areaY = y + HEADER_H
        val areaW = PANEL_W - GAP * 2
        val previewH = PANEL_H - HEADER_H - GAP - 34f // leave room for the action row

        Render2D.drawRect(ctx, areaX, areaY, areaW, previewH, Color(0, 0, 0, 140))

        val prev = previewLoaded
        if (prev == null) {
            requestLoad(file, thumbnail = false)
            Render2D.drawCenteredString(ctx, "§7Loading...", areaX + areaW / 2f, areaY + previewH / 2f - 4f)
        }
        else {
            val (dw, dh, ox, oy) = fit(prev.w, prev.h, areaW - 8f, previewH - 8f)
            Render2D.drawTexture(ctx, prev.id, areaX + 4f + ox, areaY + 4f + oy, dw, dh)
        }
        Render2D.drawBorder(ctx, areaX, areaY, areaW, previewH, Color(255, 255, 255, 25))

        // action buttons
        val btnY = areaY + previewH + 6f
        val btnW = 90f
        val btnH = 22f
        var bx = areaX
        actionLabels.forEach { label ->
            val hovered = mX >= bx && mX <= bx + btnW && mY >= btnY && mY <= btnY + btnH
            Render2D.drawRect(ctx, bx, btnY, btnW, btnH, if (hovered) Style.accentColor.withAlpha(60) else Style.bg)
            Render2D.drawBorder(ctx, bx, btnY, btnW, btnH, if (hovered) Style.accentColor else Color(255, 255, 255, 20))
            Render2D.drawCenteredString(ctx, label, bx + btnW / 2f, btnY + 7f)
            bx += btnW + GAP
        }

        // back hint
        val backHovered = mX >= areaX + areaW - 60f && mX <= areaX + areaW && mY >= btnY && mY <= btnY + btnH
        Render2D.drawCenteredString(ctx, if (backHovered) "§f§lBack" else "§7Back",
            areaX + areaW - 30f, btnY + 7f)
    }

    // ----- texture loading ---------------------------------------------------------------------------

    /**
     * Reads the file bytes off-thread, then marshals the NativeImage decode + GPU upload + register back
     * onto the render thread (mc.execute). thumbnail=false loads the full preview into [previewLoaded].
     */
    private fun requestLoad(file: File, thumbnail: Boolean) {
        if (thumbnail && textures.containsKey(file)) return
        if (! loading.add(file)) return

        ThreadUtils.async {
            val bytes = runCatching { file.readBytes() }.getOrNull()
            ThreadUtils.runOnMcThread {
                try {
                    if (bytes == null) return@runOnMcThread
                    // NativeImage.read + DynamicTexture upload must be on the render thread.
                    val image = bytes.inputStream().use { NativeImage.read(it) }
                    val w = image.width
                    val h = image.height
                    val id = idFor(file, thumbnail)
                    // DynamicTexture(Supplier<String>, NativeImage) — takes ownership of the image.
                    mc.textureManager.register(id, DynamicTexture({ file.name }, image))
                    val loaded = Loaded(id, w, h)
                    if (thumbnail) {
                        textures[file] = loaded
                        markDrawn(file)
                    }
                    else {
                        // replacing an old preview? release it first
                        previewLoaded?.let { mc.textureManager.release(it.id) }
                        previewLoaded = loaded
                    }
                }
                catch (t: Throwable) {
                    logger.error("Failed to load screenshot texture ${file.name}", t)
                }
                finally {
                    loading.remove(file)
                }
            }
        }
    }

    private fun idFor(file: File, thumbnail: Boolean): Identifier {
        val safe = file.name.lowercase().replace(Regex("[^a-z0-9._/-]"), "_")
        val prefix = if (thumbnail) "gallery/thumb" else "gallery/full"
        return Identifier.fromNamespaceAndPath(MOD_ID, "$prefix/${file.lastModified()}_$safe")
    }

    private fun markDrawn(file: File) {
        drawOrder.remove(file)
        drawOrder.add(file)
    }

    /** Keep GPU-resident thumbnails bounded — release the least-recently-drawn beyond the cap. */
    private fun evictExcessTextures() {
        while (drawOrder.size > MAX_LIVE_TEXTURES) {
            val victim = drawOrder.removeAt(0)
            textures.remove(victim)?.let { mc.textureManager.release(it.id) }
        }
    }

    // ----- input -------------------------------------------------------------------------------------

    override fun mouseClicked(event: MouseButtonEvent, isDoubleClick: Boolean): Boolean {
        val mX = Resolution.getMouseX(event.x).toFloat()
        val mY = Resolution.getMouseY(event.y).toFloat()

        val x = (Resolution.width / 2f) - (PANEL_W / 2f)
        val y = (Resolution.height / 2f) - (PANEL_H / 2f)

        val sel = selected
        if (sel != null) {
            val areaX = x + GAP
            val areaW = PANEL_W - GAP * 2
            val previewH = PANEL_H - HEADER_H - GAP - 34f
            val btnY = y + HEADER_H + previewH + 6f
            val btnW = 90f
            val btnH = 22f

            if (mY >= btnY && mY <= btnY + btnH) {
                // Back
                if (mX >= areaX + areaW - 60f && mX <= areaX + areaW) {
                    closeDetail()
                    Style.playClickSound(1f)
                    return true
                }
                var bx = areaX
                for (i in actionLabels.indices) {
                    if (mX >= bx && mX <= bx + btnW) {
                        Style.playClickSound(1f)
                        when (i) {
                            0 -> ScreenshotActions.copyToClipboard(sel)
                            1 -> ScreenshotActions.saveAs(sel)
                            //#if CURSEFORGE
                            //#else
                            2 -> ScreenshotActions.share(sel)
                            //#endif
                        }
                        return true
                    }
                    bx += btnW + GAP
                }
            }
            return super.mouseClicked(event, isDoubleClick)
        }

        // grid selection
        if (files.isEmpty()) return super.mouseClicked(event, isDoubleClick)
        val gridX = x + GAP
        val gridY = y + HEADER_H
        val cellH = THUMB_H + GAP + 10f
        val scroll = scrollAnim.value

        for (index in files.indices) {
            val row = index / columns
            val col = index % columns
            val cx = gridX + col * (THUMB_W + GAP)
            val cy = gridY + scroll + row * cellH
            if (mX >= cx && mX <= cx + THUMB_W && mY >= cy && mY <= cy + THUMB_H) {
                selectFile(files[index])
                Style.playClickSound(1f)
                return true
            }
        }
        return super.mouseClicked(event, isDoubleClick)
    }

    private fun selectFile(file: File) {
        selected = file
        previewLoaded?.let { p -> ThreadUtils.runOnMcThread { mc.textureManager.release(p.id) } }
        previewLoaded = null
        loading.remove(file) // allow a fresh full-res load
    }

    private fun closeDetail() {
        selected = null
        previewLoaded?.let { p -> ThreadUtils.runOnMcThread { mc.textureManager.release(p.id) } }
        previewLoaded = null
    }

    override fun mouseScrolled(mx: Double, my: Double, h: Double, v: Double): Boolean {
        if (selected != null) return true
        scrollTarget += (v * 40).toFloat()
        return true
    }

    override fun keyPressed(keyEvent: KeyEvent): Boolean {
        if (keyEvent.key == GLFW.GLFW_KEY_ESCAPE) {
            if (selected != null) { closeDetail(); return true }
            onClose()
            return true
        }
        return super.keyPressed(keyEvent)
    }

    override fun isPauseScreen(): Boolean = true

    // ----- lifecycle / disposal ----------------------------------------------------------------------

    override fun removed() {
        // Release every registered texture on the render thread so no GL handle leaks.
        ThreadUtils.runOnMcThread {
            textures.values.forEach { mc.textureManager.release(it.id) }
            textures.clear()
            drawOrder.clear()
            previewLoaded?.let { mc.textureManager.release(it.id) }
            previewLoaded = null
        }
        super.removed()
    }

    // ----- utils -------------------------------------------------------------------------------------

    /** Aspect-fit (srcW x srcH) into (maxW x maxH). Returns drawW, drawH, offsetX, offsetY (to center). */
    private data class Fit(val w: Float, val h: Float, val ox: Float, val oy: Float)
    private fun fit(srcW: Int, srcH: Int, maxW: Float, maxH: Float): Fit {
        if (srcW <= 0 || srcH <= 0) return Fit(maxW, maxH, 0f, 0f)
        val scale = min(maxW / srcW, maxH / srcH)
        val w = srcW * scale
        val h = srcH * scale
        return Fit(w, h, (maxW - w) / 2f, (maxH - h) / 2f)
    }
}
