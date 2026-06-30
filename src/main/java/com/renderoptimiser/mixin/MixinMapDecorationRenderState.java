package com.renderoptimiser.mixin;

import com.renderoptimiser.utils.render.MapDecorationSkin;
import net.minecraft.client.renderer.state.MapRenderState;
import net.minecraft.resources.Identifier;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

/**
 * Adds a per-decoration skin slot to the map render state (see {@link MapDecorationSkin}). Set during
 * extraction by MapPlayerHeads.assignSkins, read during rendering by MixinMapRenderer. Instances are
 * recreated on every extract, so the default null means "vanilla arrow".
 */
@Mixin(MapRenderState.MapDecorationRenderState.class)
public class MixinMapDecorationRenderState implements MapDecorationSkin {

    @Unique
    @Nullable
    private Identifier tweaky$skin;

    @Override
    @Nullable
    public Identifier getTweaky_skin() {
        return tweaky$skin;
    }

    @Override
    public void setTweaky_skin(@Nullable Identifier skin) {
        tweaky$skin = skin;
    }
}
