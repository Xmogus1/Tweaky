package com.renderoptimiser.mixin;

import com.renderoptimiser.features.impl.gui.RemoveLoadingScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.LevelLoadingScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Force-closes the LevelLoadingScreen ("Loading terrain...") as soon as the level and player exist, for
 * the {@link RemoveLoadingScreen} feature. In 26.x this single screen covers server join, respawn,
 * dimension change AND singleplayer spawn prep — its vanilla tick() waits for the load tracker plus an
 * artificial close delay; we close on the first possible tick instead.
 *
 * Guard: during Minecraft.doWorldLoad the screen is shown while {@code level == null}; closing then would
 * setScreen(null) -> TitleScreen and break singleplayer world join, so we only act once level+player are
 * set (i.e. the "Loading terrain" phase). tick()V and onClose() are byte-identical in 26.1.2 and 26.2
 * (the close plumbing differs internally but the virtual onClose() handles both).
 *
 * Fail-safe: require = 0 + try/catch — vanilla behavior is untouched if anything changes.
 */
@Mixin(LevelLoadingScreen.class)
public abstract class MixinLevelLoadingScreen extends Screen {

    private MixinLevelLoadingScreen(Component title) {
        super(title);
    }

    @Inject(method = "tick", at = @At("HEAD"), cancellable = true, require = 0)
    private void tweaky$skipLoadingScreen(CallbackInfo ci) {
        try {
            if (!RemoveLoadingScreen.INSTANCE.enabled) return;
            Minecraft mc = Minecraft.getInstance();
            if (mc.level == null || mc.player == null) return;
            ci.cancel();
            this.onClose();
        } catch (Throwable ignored) {
        }
    }
}
