package com.renderoptimiser.features.impl.misc

import com.renderoptimiser.features.Feature
import com.renderoptimiser.utils.ThreadUtils
import org.lwjgl.glfw.GLFW

object BorderlessWindowed: Feature("Makes the game window borderless and stretches it to fill the current monitor.") {
    private var savedX = 0
    private var savedY = 0
    private var savedWidth = 0
    private var savedHeight = 0

    override fun onEnable() {
        super.onEnable()
        ThreadUtils.runOnMcThread {
            val handle = mc.window.handle()
            if (handle == 0L) return@runOnMcThread

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
            GLFW.glfwSetWindowMonitor(handle, 0L, monitorX[0], monitorY[0], vidMode.width(), vidMode.height(), GLFW.GLFW_DONT_CARE)
        }
    }

    override fun onDisable() {
        super.onDisable()
        ThreadUtils.runOnMcThread {
            val handle = mc.window.handle()
            if (handle == 0L) return@runOnMcThread

            GLFW.glfwSetWindowAttrib(handle, GLFW.GLFW_DECORATED, GLFW.GLFW_TRUE)

            val width = if (savedWidth > 0) savedWidth else 854
            val height = if (savedHeight > 0) savedHeight else 480
            GLFW.glfwSetWindowMonitor(handle, 0L, savedX, savedY, width, height, GLFW.GLFW_DONT_CARE)
        }
    }
}
