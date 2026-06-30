package com.renderoptimiser.mixin;

import com.renderoptimiser.features.impl.visual.Cosmetics;
import com.renderoptimiser.features.impl.visual.text.TextReplacer;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.util.FormattedCharSequence;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * Cosmetics custom-name hooks: every string/sequence the Font prepares or measures goes through
 * TextReplacer, so cosmetic names show up in nametags, tab list, chat, scoreboard etc.
 * All five targets javap-verified identical on 26.1.2 and 26.2.
 *
 * priority 500: LOWER than NoammAddons' default 1000 so Tweaky's hooks run FIRST at runtime.
 * (Mixin resolves every mixin's injection target BEFORE inserting any code, and each insertion
 * lands immediately before the ORIGINAL instruction — i.e. after earlier-applied mixins' code.
 * So at a shared HEAD, the earlier-applied = lower-priority mixin executes first.) A name Tweaky
 * replaces is therefore gone before Noamm's replacer sees the text — Tweaky wins conflicts,
 * Noamm still covers everyone else.
 */
@Mixin(value = Font.class, priority = 500)
public class MixinFont {
    @Unique
    private static boolean tweaky$shouldReplace() {
        return Cosmetics.shouldReplaceText();
    }

    @ModifyVariable(method = "prepareText(Ljava/lang/String;FFIZI)Lnet/minecraft/client/gui/Font$PreparedText;", at = @At("HEAD"), argsOnly = true)
    private String tweaky$onDrawString(String text) {
        if (!tweaky$shouldReplace()) return text;
        return TextReplacer.handleString(text);
    }

    @ModifyVariable(method = "prepareText(Lnet/minecraft/util/FormattedCharSequence;FFIZZI)Lnet/minecraft/client/gui/Font$PreparedText;", at = @At("HEAD"), argsOnly = true)
    private FormattedCharSequence tweaky$onDrawSequence(FormattedCharSequence text) {
        if (!tweaky$shouldReplace()) return text;
        return TextReplacer.handleCharSequence(text);
    }

    @ModifyVariable(method = "width(Ljava/lang/String;)I", at = @At("HEAD"), argsOnly = true)
    private String tweaky$onWidthString(String text) {
        if (!tweaky$shouldReplace()) return text;
        return TextReplacer.handleString(text);
    }

    @ModifyVariable(method = "width(Lnet/minecraft/network/chat/FormattedText;)I", at = @At("HEAD"), argsOnly = true)
    private FormattedText tweaky$onWidthComponent(FormattedText text) {
        if (!tweaky$shouldReplace()) return text;
        if (text instanceof Component) return TextReplacer.handleComponent((Component) text);
        return text;
    }

    @ModifyVariable(method = "width(Lnet/minecraft/util/FormattedCharSequence;)I", at = @At("HEAD"), argsOnly = true)
    private FormattedCharSequence tweaky$onWidthSequence(FormattedCharSequence text) {
        if (!tweaky$shouldReplace()) return text;
        return TextReplacer.handleCharSequence(text);
    }
}
