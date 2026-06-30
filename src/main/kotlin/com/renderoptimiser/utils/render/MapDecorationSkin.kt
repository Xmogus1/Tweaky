package com.renderoptimiser.utils.render

import net.minecraft.resources.Identifier

/**
 * Duck interface added to MapRenderState.MapDecorationRenderState by
 * [com.renderoptimiser.mixin.MixinMapDecorationRenderState]: carries the matched player's skin texture
 * from extraction ([com.renderoptimiser.features.impl.visual.MapPlayerHeads.assignSkins]) to rendering
 * (MixinMapRenderer). Null = no match, render the vanilla arrow sprite.
 *
 * Lives OUTSIDE the mixin package on purpose — AutoMixinDiscovery registers every class in
 * com.renderoptimiser.mixin as a mixin, and a plain (non-@Mixin) interface there would fail to apply.
 */
interface MapDecorationSkin {
    var tweaky_skin: Identifier?
}
