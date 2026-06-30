package com.renderoptimiser.features

import com.renderoptimiser.RenderOptimiser
import com.renderoptimiser.RenderOptimiser.mc
import com.renderoptimiser.config.Config
import com.renderoptimiser.event.EventBus.register
import com.renderoptimiser.event.impl.RenderOverlayEvent
import com.renderoptimiser.ui.clickgui.enums.CategoryType
import com.renderoptimiser.ui.hud.HudEditorScreen
import com.renderoptimiser.ui.hud.HudElement
import com.renderoptimiser.ui.utils.Resolution
import com.renderoptimiser.utils.render.Render2D.width
import io.github.classgraph.ClassGraph

object FeatureManager {
    val hudElements = mutableListOf<HudElement>()
    val features = mutableSetOf<Feature>()

    fun registerFeatures() {
        val scanResult = ClassGraph()
            .enableAllInfo()
            .acceptPackages("com.renderoptimiser")
            .ignoreClassVisibility()
            .overrideClassLoaders(Thread.currentThread().contextClassLoader)
            .scan()

        scanResult.use { result ->
            val featureClasses = result.getSubclasses("com.renderoptimiser.features.Feature")
            RenderOptimiser.logger.debug("ClassGraph found ${featureClasses.size} subclasses of Feature")

            featureClasses.forEach { classInfo ->
                try {
                    val clazz = classInfo.loadClass()
                    val instance = clazz.getDeclaredField("INSTANCE").get(null) as? Feature

                    instance?.let { feature ->
                        feature.initialize()
                        hudElements.addAll(feature.hudElements)
                        features.add(feature)
                        RenderOptimiser.logger.info("Successfully loaded feature: ${feature::class.simpleName}")
                    }
                }
                catch (e: Exception) {
                    RenderOptimiser.logger.error("Failed to load feature class: ${classInfo.name}", e)
                }
            }
        }

        Config.load()

        register<RenderOverlayEvent> {
            if (mc.gui.screen() == HudEditorScreen) return@register

            Resolution.refresh()
            Resolution.push(event.context)
            hudElements.forEach { if (it.shouldDraw) it.renderElement(event.context, false) }
            Resolution.pop(event.context)
        }
    }

    fun getFeaturesByCategory(category: CategoryType): List<Feature> {
        return features.filter { it.category == category }
    }

    fun getFeatureByName(name: String): Feature? {
        return features.find { it.name == name }
    }

    fun getHudByName(name: String): HudElement? {
        return hudElements.find { it.name == name }
    }

    fun createFeatureList(): String {
        val featureList = StringBuilder()
        for ((category, features) in features.groupBy { it.category }.entries.sortedBy { it.key.ordinal }) {
            featureList.appendLine("Category: ${category.name}")
            for (feature in features.sortedByDescending { it.name.width() }) {
                featureList.appendLine("- ${feature.name}: ${feature.description ?: ""}")
            }
            featureList.appendLine()
        }
        return featureList.toString()
    }
}