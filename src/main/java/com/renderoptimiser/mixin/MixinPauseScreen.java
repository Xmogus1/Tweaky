package com.renderoptimiser.mixin;

import com.renderoptimiser.features.impl.gui.ScreenshotsMenu;
import com.renderoptimiser.ui.gui.ScreenshotsScreen;
import com.renderoptimiser.utils.screenshots.ScreenshotButton;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.SpriteIconButton;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Adds a camera icon button to the vanilla pause / Game Menu that opens the Tweaky Screenshots gallery.
 *
 * Fail-safe by design:
 *  - {@code require = 0}: if PauseScreen#init ever changes signature the injection silently no-ops
 *    instead of crashing the game.
 *  - Whole body wrapped in try/catch(Throwable): a vanilla screen can never be broken by this mixin.
 *  - Only injects while {@link ScreenshotsMenu} is enabled.
 *
 * Cross-version notes (26.1.2 vs 26.2):
 *  - {@code PauseScreen#init} exists identically in both; TAIL inject is the portable target (the 26.2
 *    icon-row LinearLayout is a local, not a field, so we position our own button manually instead of
 *    appending to it — this also works on 26.1.2, which has no icon row at all).
 *  - {@code SpriteIconButton.builder(Component, OnPress, boolean)} + {@code .width(int)} + {@code .sprite(Identifier,int,int)}
 *    + {@code .build()} are byte-identical in both jars.
 *  - Screen opening: {@code Minecraft#setScreen} was REMOVED in 26.2; {@code setScreenAndShow} exists in
 *    BOTH jars, so we use it unconditionally (no per-version branch needed).
 *  - The icon is a GUI SPRITE at assets/tweaky/textures/gui/sprites/screenshots.png, referenced without
 *    extension as Identifier("tweaky","screenshots"). SpriteIconButton requires a sprite, not a raw texture.
 */
@Mixin(PauseScreen.class)
public abstract class MixinPauseScreen extends Screen {

    private static final Identifier TWEAKY$ICON = Identifier.fromNamespaceAndPath("tweaky", "screenshots");

    private MixinPauseScreen(Component title) {
        super(title);
    }

    @Inject(method = "init", at = @At("TAIL"), require = 0)
    private void tweaky$addScreenshotsButton(CallbackInfo ci) {
        try {
            if (!ScreenshotsMenu.INSTANCE.enabled) return;

            // 20x20 icon-only button, 15x15 sprite (matches vanilla's pause-menu icon-row style).
            SpriteIconButton button = SpriteIconButton.builder(
                            Component.literal("Screenshots"),
                            b -> Minecraft.getInstance().setScreenAndShow(new ScreenshotsScreen()),
                            true /* iconOnly */)
                    .size(20, 20)
                    .sprite(TWEAKY$ICON, 15, 15)
                    .build();

            // Slot the button into the vanilla icon row and re-centre the row (see ScreenshotButton).
            ScreenshotButton.place(this.children(), this.width, button);
            this.addRenderableWidget(button);
        }
        catch (Throwable ignored) {
        }
    }

}
