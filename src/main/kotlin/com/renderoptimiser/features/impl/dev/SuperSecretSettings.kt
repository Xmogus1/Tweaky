package com.renderoptimiser.features.impl.dev

import com.renderoptimiser.features.Feature
import com.renderoptimiser.features.annotations.Dev
import com.renderoptimiser.mixin.IGameRenderer
import com.renderoptimiser.ui.notification.NotificationManager
import com.renderoptimiser.utils.ChatUtils
import com.renderoptimiser.utils.ThreadUtils
import net.minecraft.resources.Identifier

@Dev
object SuperSecretSettings: Feature("???") {
    // (namespace, chain). "minecraft" chains ship with the game; "tweaky" chains are bundled by this mod.
    private val shaders = listOf(
        "minecraft" to "invert",
        "minecraft" to "creeper",
        "minecraft" to "spider",
        "minecraft" to "blur",
        "tweaky" to "desaturate",
        "tweaky" to "sepia",
        "tweaky" to "psychedelic",
        "tweaky" to "posterize",
    )
    private var index = -1   // -1 = off

    /** Advance to the next shader (wrapping through Off). Called by the vanilla button from MixinOptionsScreen. */
    fun cycle() {
        index = if (index >= shaders.lastIndex) -1 else index + 1
        ThreadUtils.runOnMcThread {
            try {
                if (index < 0) {
                    mc.gameRenderer.clearPostEffect()
                }
                else {
                    val (ns, name) = shaders[index]
                    (mc.gameRenderer as IGameRenderer).invokeSetPostEffect(Identifier.fromNamespaceAndPath(ns, name))
                }
            }
            catch (e: Throwable) {
                NotificationManager.error("Shader failed to load", e.message ?: "unknown")
            }
        }
    }

    override fun onDisable() {
        super.onDisable()
        if (index >= 0) {
            index = -1
            ThreadUtils.runOnMcThread { mc.gameRenderer.clearPostEffect() }
        }
    }
}
