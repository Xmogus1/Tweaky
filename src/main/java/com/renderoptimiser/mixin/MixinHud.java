package com.renderoptimiser.mixin;

import com.renderoptimiser.event.EventBus;
import com.renderoptimiser.event.impl.ActionBarMessageEvent;
import com.renderoptimiser.event.impl.RenderOverlayEvent;
import com.renderoptimiser.features.impl.misc.Camera;
import com.renderoptimiser.features.impl.visual.DarkMode;
import com.renderoptimiser.features.impl.visual.Scoreboard;
import com.renderoptimiser.utils.location.LocationUtils;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.Hud;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Hud.class)
public abstract class MixinHud {
    @Shadow
    @Final
    private Minecraft minecraft;

    @Shadow private boolean isHidden;
    @Shadow @Nullable private Component title;
    @Shadow @Nullable private Component subtitle;

    @Shadow
    public abstract Font getFont();


    @Inject(method = "extractTitle", at = @At(value = "INVOKE", target = "Lorg/joml/Matrix3x2fStack;scale(FF)Lorg/joml/Matrix3x2f;", ordinal = 0, shift = At.Shift.AFTER))
    private void onScaleTitle(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker, CallbackInfo ci) {
        if (title == null) return;

        float maxWidth = minecraft.getWindow().getGuiScaledWidth() * 0.85f;
        float currentWidth = minecraft.font.width(title) * 4.0f;
        if (currentWidth > maxWidth) {
            float scaleFactor = maxWidth / currentWidth;
            graphics.pose().scale(scaleFactor);
        }
    }

    @Inject(method = "extractTitle", at = @At(value = "INVOKE", target = "Lorg/joml/Matrix3x2fStack;scale(FF)Lorg/joml/Matrix3x2f;", ordinal = 1, shift = At.Shift.AFTER))
    private void onScaleSubtitle(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker, CallbackInfo ci) {
        if (subtitle == null) return;

        float maxWidth = minecraft.getWindow().getGuiScaledWidth() * 0.85f;
        float currentWidth = minecraft.font.width(subtitle) * 2.0f;
        if (currentWidth > maxWidth) {
            float scaleFactor = maxWidth / currentWidth;
            graphics.pose().scale(scaleFactor);
        }
    }

    @Inject(method = "extractRenderState", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/Hud;extractSleepOverlay(Lnet/minecraft/client/gui/GuiGraphicsExtractor;Lnet/minecraft/client/DeltaTracker;)V"))
    public void onRenderHud(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker, CallbackInfo ci) {
        if (this.isHidden) return;
        if (this.minecraft.debugEntries.isOverlayVisible()) return;
        EventBus.post(new RenderOverlayEvent(graphics, deltaTracker));
    }

    @Inject(method = "extractRenderState", at = @At(value = "HEAD"))
    public void onRenderHudPre(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker, CallbackInfo ci) {
        if (!DarkMode.getTintHud().getValue()) DarkMode.drawOverlay(graphics);
    }

    @Inject(method = "extractRenderState", at = @At(value = "TAIL"))
    public void onRenderHudPost(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker, CallbackInfo ci) {
        if (DarkMode.getTintHud().getValue()) DarkMode.drawOverlay(graphics);
    }

    @Inject(method = "extractPortalOverlay", at = @At("HEAD"), cancellable = true)
    public void onRenderPortalOverlay(GuiGraphicsExtractor graphics, float alpha, CallbackInfo ci) {
        if (Camera.INSTANCE.enabled && Camera.getHidePortalOverlay().getValue()) ci.cancel();
    }

    @Inject(method = "extractConfusionOverlay", at = @At("HEAD"), cancellable = true)
    public void onRenderConfusionOverlay(GuiGraphicsExtractor graphics, float strength, CallbackInfo ci) {
        if (Camera.INSTANCE.enabled && Camera.getDisableNausea().getValue()) ci.cancel();
    }

    @Inject(method = "extractScoreboardSidebar", at = @At("HEAD"), cancellable = true)
    public void renderScoreboardSidebar(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker, CallbackInfo ci) {
        if (Scoreboard.INSTANCE.enabled) ci.cancel();
    }

    @ModifyVariable(method = "setOverlayMessage", at = @At("HEAD"), argsOnly = true)
    private Component onSetOverlayMessage(Component string) {
        var event = new ActionBarMessageEvent(string);
        if (EventBus.post(event)) return Component.empty();
        return Component.literal(event.getMessage());
    }

    @Inject(method = "extractEffects", at = @At("HEAD"), cancellable = true)
    private void onRenderEffects(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker, CallbackInfo ci) {
        if (LocationUtils.inSkyblock) ci.cancel();
    }

}
