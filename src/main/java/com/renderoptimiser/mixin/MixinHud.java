package com.renderoptimiser.mixin;

import com.renderoptimiser.event.EventBus;
import com.renderoptimiser.event.impl.ActionBarMessageEvent;
import com.renderoptimiser.event.impl.RenderOverlayEvent;
import com.renderoptimiser.features.impl.visual.Camera;
import com.renderoptimiser.features.impl.visual.DarkMode;
import com.renderoptimiser.features.impl.hud.Scoreboard;
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

    /**
     * Spyglass-scope HUD hiding, mutation-free: every read of the isHidden field inside
     * extractRenderState reports "hidden" while the Zoom scope is up. The old approach (force the field
     * at HEAD, restore at TAIL) could leave the flag STUCK — extractRenderState has an early return when
     * a LevelLoadingScreen is open (every Nether portal trip), which skipped the TAIL restore and made
     * the hand/HUD vanish until something reset it. Wrapping the reads has no state to restore.
     * The captured GuiRenderState.isHudHidden also gates renderItemInHand, so the hand hides while
     * scoping (like a real spyglass) and ONLY while scoping.
     */
    @com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation(
            method = "extractRenderState",
            at = @At(value = "FIELD", target = "Lnet/minecraft/client/gui/Hud;isHidden:Z", opcode = org.objectweb.asm.Opcodes.GETFIELD),
            require = 0
    )
    private boolean tweaky$scopeHidesHud(Hud instance, com.llamalad7.mixinextras.injector.wrapoperation.Operation<Boolean> original) {
        return original.call(instance) || com.renderoptimiser.features.impl.visual.Zoom.isScopeActive();
    }

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

    // RETURN (not TAIL): extractRenderState has multiple exit paths (LevelLoadingScreen early return,
    // hidden-HUD branch) and this must run on every one of them.
    @Inject(method = "extractRenderState", at = @At(value = "RETURN"))
    public void onRenderHudPost(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker, CallbackInfo ci) {
        if (DarkMode.getTintHud().getValue()) DarkMode.drawOverlay(graphics);
        com.renderoptimiser.features.impl.visual.Zoom.renderScope(graphics);
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
