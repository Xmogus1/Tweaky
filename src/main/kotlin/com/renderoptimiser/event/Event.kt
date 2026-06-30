package com.renderoptimiser.event

import com.renderoptimiser.RenderOptimiser
import com.renderoptimiser.event.impl.MouseClickEvent
import com.renderoptimiser.event.impl.PlayerInteractEvent
import com.renderoptimiser.utils.ChatUtils
import com.renderoptimiser.utils.remove

abstract class Event(val cancelable: Boolean = false) {
    var cancellationSource: String? = null
        private set

    open var isCanceled = false
        set(value) {
            if (! cancelable) return
            if (value && ! field && RenderOptimiser.debugFlags.contains("cancel")) {
                captureSource()
            }
            field = value
        }

    open fun cancel() {
        isCanceled = true
    }

    private fun captureSource() {
        val stack = Thread.currentThread().stackTrace

        for (i in 3 until stack.size) {
            val element = stack[i]
            val className = element.className

            if (className.startsWith("com.renderoptimiser.event") ||
                className.startsWith("java.lang") ||
                className.startsWith("kotlin.") ||
                className.contains("EventBus")
            ) continue

            val eventName = this.javaClass.name.remove("com.renderoptimiser.event.impl.")
            val fileName = element.fileName ?: "Unknown File"
            val lineNumber = element.lineNumber
            val methodName = element.methodName

            cancellationSource = "$fileName:$lineNumber ($methodName)"

            if (this is PlayerInteractEvent || this is MouseClickEvent) {
                ChatUtils.modMessage("§c$eventName canceled by: §e$cancellationSource")
            }

            break
        }
    }
}