package com.renderoptimiser.features

import com.renderoptimiser.RenderOptimiser
import com.renderoptimiser.config.Savable
import com.renderoptimiser.event.Event
import com.renderoptimiser.event.EventBus.EventContext
import com.renderoptimiser.event.EventListener
import com.renderoptimiser.event.EventPriority
import com.renderoptimiser.features.annotations.AlwaysActive
import com.renderoptimiser.features.annotations.Dev
import com.renderoptimiser.ui.clickgui.components.Setting
import com.renderoptimiser.ui.clickgui.components.impl.ButtonSetting
import com.renderoptimiser.ui.clickgui.components.impl.SliderSetting
import com.renderoptimiser.ui.clickgui.components.impl.SoundSetting
import com.renderoptimiser.ui.clickgui.enums.CategoryType
import com.renderoptimiser.ui.hud.HudElement
import com.renderoptimiser.utils.ThreadUtils
import com.renderoptimiser.utils.spaceCaps
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.resources.sounds.SimpleSoundInstance
import net.minecraft.sounds.SoundEvent
import kotlin.reflect.KProperty

open class Feature(
    val description: String? = null,
    name: String? = null,
    toggled: Boolean = false,
) {
    val name = name ?: this::class.simpleName.toString().spaceCaps()
    val listeners = mutableSetOf<EventListener<*>>()

    val configSettings = mutableSetOf<Setting<*>>()
    val hudElements = mutableSetOf<HudElement>()

    @JvmField
    var enabled = toggled

    /** The built-in default of [enabled], kept for "reset all settings". */
    val defaultEnabled = toggled

    private val isDev = this::class.java.isAnnotationPresent(Dev::class.java)
    private val alwaysActive = this::class.java.isAnnotationPresent(AlwaysActive::class.java)

    open val category = if (isDev) CategoryType.DEV else initCategory()

    protected inline val mc get() = RenderOptimiser.mc
    protected inline val scope get() = RenderOptimiser.scope
    protected inline val cacheData get() = RenderOptimiser.cacheData

    fun initialize() {
        init()

        if (enabled || alwaysActive) onEnable() else onDisable()
    }

    open fun init() = Unit
    open fun onEnable() = listeners.forEach(EventListener<*>::register)
    open fun onDisable() {
        if (alwaysActive) return
        listeners.forEach(EventListener<*>::unregister)
    }

    open fun toggle() {
        enabled = ! enabled
        if (enabled || alwaysActive) onEnable()
        else onDisable()
    }

    protected inline fun <reified T: Event> register(priority: EventPriority = EventPriority.NORMAL, noinline block: EventContext<T>.() -> Unit): EventListener<T> {
        val listener = EventListener.create<T>(priority, block)
        listeners.add(listener)
        return listener
    }

    fun hudElement(
        name: String,
        enabled: () -> Boolean = { true },
        shouldDraw: () -> Boolean = { true },
        centered: Boolean = false,
        render: (GuiGraphicsExtractor, Boolean) -> Pair<Float, Float>,
    ): HudElement {
        return object: HudElement() {
            override val name = name
            override val toggle: Boolean get() = this@Feature.enabled && enabled.invoke()
            override val shouldDraw: Boolean get() = shouldDraw.invoke()
            override fun draw(ctx: GuiGraphicsExtractor, example: Boolean): Pair<Float, Float> = render(ctx, example)
            override val centered = centered
        }.also(hudElements::add)
    }

    protected data class SoundSettings(val sound: SoundSetting, val volume: SliderSetting<Float>, val pitch: SliderSetting<Float>, val play: ButtonSetting)

    protected fun createSoundSettings(name: String, sound: SoundEvent, showIf: () -> Boolean = { true }): SoundSettings {
        val sound = SoundSetting(name, sound)
            .withDescription("The internal Minecraft sound key to play.")
            .showIf(showIf)

        val volume = SliderSetting("Volume", 0.5f, 0f, 1f, 0.1f)
            .withDescription("The loudness of the sound.")
            .showIf(showIf)

        val pitch = SliderSetting("Pitch", 1f, 0f, 2f, 0.1f)
            .withDescription("The pitch/frequency of the sound.")
            .showIf(showIf)

        val play = ButtonSetting("Play Sound", false) {
            ThreadUtils.runOnMcThread { repeat(5) { mc.soundManager.play(SimpleSoundInstance.forUI(sound.value, pitch.value, volume.value)) } }
        }.withDescription("Click to test the current sound configuration.").showIf(showIf)

        configSettings.add(sound)
        configSettings.add(volume)
        configSettings.add(pitch)
        configSettings.add(play)

        return SoundSettings(sound, volume, pitch, play)
    }


    fun getSettingByName(key: String?) = configSettings.find { it.name == key && it is Savable }

    private fun initCategory(): CategoryType {
        val parts = this::class.java.`package` !!.name.split(".")
        val categoryName = parts[parts.indexOf("impl") + 1].uppercase()
        if (CategoryType.entries.none { it.name.equals(categoryName, true) }) throw Error("Category does not exist: $categoryName")
        return CategoryType.valueOf(categoryName.uppercase())
    }

    protected fun <T: Setting<*>> T.section(name: String) = with(Setting.Companion) { this@section.section(name) }
    protected fun <T: Setting<*>> T.withDescription(desc: String) = with(Setting.Companion) { this@withDescription.withDescription(desc) }
    protected fun <T: Setting<*>> T.showIf(condition: () -> Boolean) = with(Setting.Companion) { this@showIf.showIf(condition) }
    protected fun <T: Setting<*>> T.hideIf(condition: () -> Boolean) = with(Setting.Companion) { this@hideIf.hideIf(condition) }
    protected fun <T, S: Setting<T>> S.onChange(listener: (T) -> Unit) = with(Setting.Companion) { this@onChange.onChange(listener) }
    protected operator fun <T, S: Setting<T>> S.provideDelegate(thisRef: Feature, prop: KProperty<*>) = with(Setting.Companion) { this@provideDelegate.provideDelegate(thisRef, prop) }
    protected operator fun <T, S: Setting<T>> S.getValue(thisRef: Feature, prop: KProperty<*>) = with(Setting.Companion) { this@getValue.getValue(thisRef, prop) }
}