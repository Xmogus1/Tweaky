package com.renderoptimiser.mixin;

import com.renderoptimiser.features.impl.gui.RemoveLoadingScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.LoadingOverlay;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.server.packs.resources.ReloadInstance;
import net.minecraft.util.Mth;
import net.minecraft.util.Util;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Optional;
import java.util.function.Consumer;

/**
 * Resource-reload half of {@link RemoveLoadingScreen} — 26.2 VERSION (DIVERGENT FILE: the 26.1.2 copy
 * reads the screen via minecraft.screen and removes the overlay via minecraft.setOverlay(); in 26.2 both
 * moved onto Gui).
 *
 * When switching resource packs, vanilla covers everything with the Mojang-logo LoadingOverlay. In 26.x
 * the overlay's completion logic lives in tick() (reload.isDone -> onFinish -> arms fadeOutStart), while
 * extractRenderState only draws + smooths progress + removes the overlay after the fade. So we can
 * safely replace the DRAWING wholesale: keep the underlying screen visible with a thin progress bar on
 * top, keep currentProgress fed (isReadyToFadeOut/tick stay honest), and drop the overlay the instant
 * tick() arms the fade-out (skipping the fade). The initial game-boot overlay (no level, no screen — a
 * black void behind) is left fully vanilla.
 */
@Mixin(LoadingOverlay.class)
public abstract class MixinLoadingOverlay {

    @Shadow @Final private Minecraft minecraft;
    @Shadow @Final private ReloadInstance reload;
    @Shadow @Final private Consumer<Optional<Throwable>> onFinish;
    @Shadow private float currentProgress;
    @Shadow private long fadeOutStart;

    @Inject(method = "extractRenderState", at = @At("HEAD"), cancellable = true, require = 0)
    private void tweaky$minimalReloadOverlay(GuiGraphicsExtractor ctx, int mouseX, int mouseY, float partial, CallbackInfo ci) {
        try {
            if (!RemoveLoadingScreen.INSTANCE.enabled) return;
            // Initial boot: the TitleScreen is set while the FIRST reload is still running, so its
            // textures (panorama cubemap etc.) don't exist yet — extracting it then crashes at draw
            // time ("Texture view does not exist"). isGameLoadFinished() only flips true once boot
            // fully completes, so it cleanly separates boot (keep vanilla) from later pack reloads.
            if (!minecraft.isGameLoadFinished()) return;
            ci.cancel();

            // Self-contained completion, mirroring vanilla tick() (guarded the same way so whichever
            // runs first wins): finish the reload, then refresh the screen for the new resources.
            if (fadeOutStart == -1L && reload.isDone()) {
                try {
                    reload.checkExceptions();
                    onFinish.accept(Optional.empty());
                } catch (Throwable t) {
                    onFinish.accept(Optional.of(t));
                }
                fadeOutStart = Util.getMillis();
                Screen current = minecraft.gui.screen();
                if (current != null) {
                    current.init(minecraft.getWindow().getGuiScaledWidth(), minecraft.getWindow().getGuiScaledHeight());
                }
            }

            // Remove BEFORE any drawing, so nothing below can ever block the overlay from going away.
            if (fadeOutStart != -1L) {
                minecraft.gui.setOverlay(null);
                return;
            }

            // Keep whatever is underneath visible. Isolated: if the screen can't extract mid-reload
            // (textures in flux), we just skip the backdrop this frame — never the removal above.
            try {
                Screen screen = minecraft.gui.screen();
                if (screen != null) screen.extractRenderStateWithTooltipAndSubtitles(ctx, mouseX, mouseY, partial);
            } catch (Throwable ignored) {
            }

            // Vanilla smooths this field in the code we skipped; keep it fed + draw the thin bar.
            currentProgress = Mth.clamp(reload.getActualProgress(), 0.0f, 1.0f);
            ctx.nextStratum();
            int barWidth = (int) (ctx.guiWidth() * currentProgress);
            if (barWidth > 0) ctx.fill(0, 0, barWidth, 2, 0xFFFFFFFF);
        } catch (Throwable ignored) {
        }
    }
}
