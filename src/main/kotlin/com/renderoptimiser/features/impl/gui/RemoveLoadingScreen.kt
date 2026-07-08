package com.renderoptimiser.features.impl.gui

import com.renderoptimiser.features.Feature

/**
 * Removes loading screens:
 *  - World loading — the "Loading terrain" [net.minecraft.client.gui.screens.LevelLoadingScreen]:
 *    [com.renderoptimiser.mixin.MixinLevelLoadingScreen] closes it on the first tick where the
 *    level+player exist (the guard keeps singleplayer server-boot vanilla, where closing early would
 *    bounce to the title screen).
 *  - Resource pack switches — the Mojang-logo LoadingOverlay: [com.renderoptimiser.mixin.MixinLoadingOverlay]
 *    (a DIVERGENT per-version file) replaces its rendering with "underlying screen + thin progress bar"
 *    and drops the overlay the moment the reload finishes (no fade). The initial game-boot overlay is
 *    left vanilla (there is nothing to show behind it).
 */
object RemoveLoadingScreen: Feature("Skip loading screens: world joins & resource pack switches.", toggled = true)
