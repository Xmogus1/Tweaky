package com.renderoptimiser.features.impl.gui

import com.renderoptimiser.features.Feature
import com.renderoptimiser.utils.ThreadUtils
import org.lwjgl.glfw.GLFW

object BorderlessWindowed: Feature("Borderless fullscreen window.") {
    private var savedX = 0
    private var savedY = 0
    private var savedWidth = 0
    private var savedHeight = 0
    private var applied = false

    override fun onEnable() {
        super.onEnable()
        applyBorderless()
    }

    override fun onDisable() {
        super.onDisable()
        restoreWindowed()
    }

    /**
     * F11 entry point (called from the fullscreen mixins while the feature is enabled): toggles
     * the borderless window on/off instead of MC's real fullscreen. [applied] tracks the WINDOW
     * state, not the feature-enabled state — they intentionally diverge so F11 can flip the
     * borderless window while the feature stays enabled.
     */
    fun toggleBorderless() {
        if (applied) restoreWindowed() else applyBorderless()
    }

    private fun applyBorderless() {
        ThreadUtils.runOnMcThread {
            val handle = mc.window.handle()
            if (handle == 0L || applied) return@runOnMcThread

            // If MC is in real (monitor) fullscreen, leave it first — otherwise we'd capture the
            // fullscreen resolution as "windowed" geometry and be poking a GLFW monitor-mode window.
            if (mc.window.isFullscreen) {
                mc.window.toggleFullScreen()
                mc.window.updateFullscreenIfChanged()
            }

            val x = IntArray(1)
            val y = IntArray(1)
            val width = IntArray(1)
            val height = IntArray(1)

            GLFW.glfwGetWindowPos(handle, x, y)
            GLFW.glfwGetWindowSize(handle, width, height)

            savedX = x[0]
            savedY = y[0]
            savedWidth = width[0]
            savedHeight = height[0]

            val monitor = GLFW.glfwGetPrimaryMonitor()
            val vidMode = GLFW.glfwGetVideoMode(monitor) ?: return@runOnMcThread

            val monitorX = IntArray(1)
            val monitorY = IntArray(1)
            GLFW.glfwGetMonitorPos(monitor, monitorX, monitorY)

            GLFW.glfwSetWindowAttrib(handle, GLFW.GLFW_DECORATED, GLFW.GLFW_FALSE)
            GLFW.glfwSetWindowPos(handle, monitorX[0], monitorY[0])
            GLFW.glfwSetWindowSize(handle, vidMode.width(), vidMode.height())
            applied = true
        }
    }

    private fun restoreWindowed() {
        if (! applied) return
        applied = false
        ThreadUtils.runOnMcThread {
            val handle = mc.window.handle()
            if (handle == 0L) return@runOnMcThread

            GLFW.glfwSetWindowAttrib(handle, GLFW.GLFW_DECORATED, GLFW.GLFW_TRUE)
            GLFW.glfwSetWindowPos(handle, savedX, savedY)
            GLFW.glfwSetWindowSize(handle, if (savedWidth > 0) savedWidth else 854, if (savedHeight > 0) savedHeight else 480)
        }
    }
}
