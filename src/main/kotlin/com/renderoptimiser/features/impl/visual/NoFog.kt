package com.renderoptimiser.features.impl.visual

import com.renderoptimiser.features.Feature

/**
 * Removes terrain, sky and cloud fog (including nether/lava/blindness fog). Implemented in
 * [com.renderoptimiser.mixin.MixinFogRenderer]: every distance in the per-frame FogData is pushed to
 * Float.MAX_VALUE — the same values vanilla's own internal fog-disabled buffer uses. Takes effect
 * instantly since fog is recomputed every frame.
 */
object NoFog: Feature("Removes fog for a clear view at any distance.")
