package com.renderoptimiser

import com.renderoptimiser.commands.CommandManager
import com.renderoptimiser.config.PogObject
import com.renderoptimiser.event.EventDispatcher
import com.renderoptimiser.features.FeatureManager
import com.renderoptimiser.utils.*
import com.renderoptimiser.utils.render.ItemRenderer
import com.renderoptimiser.utils.render.ModRenderPipelines
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import net.fabricmc.api.ClientModInitializer
import net.fabricmc.fabric.api.client.rendering.v1.PictureInPictureRendererRegistry
import net.fabricmc.loader.api.FabricLoader
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.screens.Screen
import org.slf4j.LoggerFactory

object RenderOptimiser: ClientModInitializer {
    const val MOD_ID = "tweaky"
    val MOD_NAME by lazy { FabricLoader.getInstance().getModContainer(MOD_ID).get().metadata.name }
    val MOD_VERSION by lazy { FabricLoader.getInstance().getModContainer(MOD_ID).get().metadata.version.friendlyString }
    const val PREFIX = "§9§lTWEAKY§r"

    @JvmField
    val scope = CoroutineScope(Dispatchers.Default + SupervisorJob() + CoroutineName(MOD_NAME))

    @JvmField
    val mc = Minecraft.getInstance()

    @JvmField
    val logger = LoggerFactory.getLogger(MOD_NAME)

    @JvmField
    var isLoaded = false

    val cacheData = PogObject("cacheData", mutableMapOf<String, Any>())
    val debugFlags = mutableSetOf<String>()
    val isDev get() = debugFlags.contains("dev")

    var screen: Screen? = null
        set(value) {
            field = value
            if (value == null) return
            ThreadUtils.scheduledTask(1) {
                mc.setScreenAndShow(value)
                field = null
            }
        }

    override fun onInitializeClient() {
        System.setProperty("java.awt.headless", "false")
        ModRenderPipelines.init()

        PictureInPictureRendererRegistry.register { ItemRenderer() }

        EventDispatcher.init()
        ServerUtils.init()
        ChatUtils.init()

        FeatureManager.registerFeatures()
        CommandManager.registerAll()

        isLoaded = true
    }
}