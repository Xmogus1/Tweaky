package com.renderoptimiser.mixin;

import com.renderoptimiser.features.impl.dev.SuperSecretSettings;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.options.OptionsScreen;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(OptionsScreen.class)
public abstract class MixinOptionsScreen extends Screen {
    private MixinOptionsScreen(Component title) {
        super(title);
    }

    // When Super Secret Settings is enabled, replace the "Credits & Attribution" button with the
    // classic 1.8-style "Super Secret Settings" button that cycles screen shaders. Fail-safe:
    // optional injection + swallowed errors so the vanilla options screen can never break.
    @Inject(method = "init", at = @At("TAIL"), require = 0)
    private void tweaky$replaceCreditsButton(CallbackInfo ci) {
        try {
            if (!SuperSecretSettings.INSTANCE.enabled) return;

            AbstractWidget credits = null;
            for (GuiEventListener child : this.children()) {
                if (child instanceof AbstractWidget widget && widget.getMessage().getString().contains("Credit")) {
                    credits = widget;
                    break;
                }
            }
            if (credits == null) return;

            int bx = credits.getX();
            int by = credits.getY();
            int bw = credits.getWidth();
            int bh = credits.getHeight();
            this.removeWidget(credits);
            this.addRenderableWidget(
                Button.builder(Component.literal("Super Secret Settings"), b -> SuperSecretSettings.INSTANCE.cycle())
                    .bounds(bx, by, bw, bh)
                    .build()
            );
        }
        catch (Throwable ignored) {
        }
    }
}
