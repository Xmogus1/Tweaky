package com.renderoptimiser.mixin;

import com.renderoptimiser.features.impl.gui.ScreenshotsMenu;
import com.renderoptimiser.ui.gui.ScreenshotsScreen;
import com.renderoptimiser.utils.screenshots.ScreenshotButton;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.SpriteIconButton;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Adds the same Screenshots camera button to the main-menu TitleScreen icon strip (the small square
 * language/accessibility buttons), mirroring {@link MixinPauseScreen}. Shares the placement logic in
 * {@link ScreenshotButton}. Fail-safe: {@code require = 0} + try/catch(Throwable) + only while enabled.
 */
@Mixin(TitleScreen.class)
public abstract class MixinTitleScreen extends Screen {

    private static final Identifier TWEAKY$ICON = Identifier.fromNamespaceAndPath("tweaky", "screenshots");

    private MixinTitleScreen(Component title) {
        super(title);
    }

    @Inject(method = "init", at = @At("TAIL"), require = 0)
    private void tweaky$addScreenshotsButton(CallbackInfo ci) {
        try {
            if (!ScreenshotsMenu.INSTANCE.enabled) return;

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
