package com.renderoptimiser.features.impl.gui

import com.renderoptimiser.RenderOptimiser
import com.renderoptimiser.features.Feature
import com.renderoptimiser.ui.clickgui.components.impl.ButtonSetting
import com.renderoptimiser.ui.clickgui.components.impl.DropdownSetting
import com.renderoptimiser.ui.clickgui.components.impl.TextInputSetting
import com.renderoptimiser.ui.clickgui.components.impl.ToggleSetting
import com.renderoptimiser.ui.gui.ScreenshotsScreen

/**
 * Adds a camera-icon button to the vanilla pause / Game Menu (via [com.renderoptimiser.mixin.MixinPauseScreen])
 * that opens a custom Screenshots gallery. The mixin only injects its button while this feature is [enabled].
 *
 * The category is [com.renderoptimiser.ui.clickgui.enums.CategoryType.GUI] purely because this file lives in
 * `features.impl.gui` (see Feature.initCategory).
 */
object ScreenshotsMenu: Feature("Adds a screenshots gallery to the pause & title menu.", toggled = true) {

    // Upload/Share settings — excluded from the CurseForge build, which ships no external-upload feature.
    //#if CURSEFORGE
    //#else
    /** Which keyless host to use when SHARE-ing. catbox is the default (no key required). */
    val uploadHost by DropdownSetting("Upload Host", 0, listOf("catbox.moe", "0x0.st"))
        .withDescription("The keyless image host used by the Share button.")

    /**
     * Optional imgur Client-ID. If non-blank, Share uploads to imgur instead of the keyless host.
     * Leave empty to stay keyless.
     */
    val imgurClientId by TextInputSetting("Imgur Client-ID", "")
        .withDescription("Optional. If set, Share uploads to imgur using this anonymous Client-ID instead of the keyless host.")

    /** After a successful Share upload, also announce the link in chat. */
    val chatLink by ToggleSetting("Chat Uploaded Link", true)
        .withDescription("After a successful Share, print a clickable link in chat (in addition to copying it).")
    //#endif

    /** Lets you open the gallery straight from the ClickGui without going through the pause menu. */
    val openButton by ButtonSetting("Open Gallery") {
        RenderOptimiser.screen = ScreenshotsScreen()
    }.withDescription("Opens the Screenshots gallery immediately.")
}
