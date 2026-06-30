package com.renderoptimiser.mixin;

import com.renderoptimiser.features.impl.general.Chat;
import com.renderoptimiser.interfaces.IChatComponent;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.client.multiplayer.chat.GuiMessage;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

import static com.renderoptimiser.RenderOptimiser.mc;

@Mixin(ChatComponent.class)
public abstract class MixinChatComponent implements IChatComponent {

    @Shadow @Final private List<GuiMessage.Line> trimmedMessages;

    @Unique
    private static final DateTimeFormatter TWEAKY_TIME = DateTimeFormatter.ofPattern("HH:mm");

    /**
     * Chat timestamps (Chat feature): the private 4-arg addMessage is the single funnel every public
     * entry point (player, server-system, client-system) goes through — identical in 26.1.2 and 26.2.
     */
    @ModifyVariable(
            method = "addMessage(Lnet/minecraft/network/chat/Component;Lnet/minecraft/network/chat/MessageSignature;Lnet/minecraft/client/multiplayer/chat/GuiMessageSource;Lnet/minecraft/client/multiplayer/chat/GuiMessageTag;)V",
            at = @At("HEAD"), argsOnly = true, require = 0
    )
    private Component tweaky$prependTimestamp(Component message) {
        try {
            if (!Chat.timestampsActive()) return message;
            return Component.empty()
                    .append(Component.literal("[" + LocalTime.now().format(TWEAKY_TIME) + "] ").withStyle(ChatFormatting.GRAY))
                    .append(message);
        } catch (Throwable t) {
            return message;
        }
    }

    @Shadow private int chatScrollbarPos;

    @Shadow
    public abstract boolean isChatFocused();

    @Shadow
    protected abstract int getWidth();

    @Shadow
    protected abstract double getScale();

    @Shadow
    public abstract int getLinesPerPage();

    @Shadow
    protected abstract int getLineHeight();

    @Unique
    private double screenToChatX(double x) {
        return (x / getScale()) - 4.0;
    }

    @Unique
    private double screenToChatY(double y) {
        double scaledHeight = mc.getWindow().getGuiScaledHeight();
        double yFromBottom = scaledHeight - y - 40.0;
        return yFromBottom / (getScale() * getLineHeight());
    }

    @Unique
    private int getMessageLineIndexAt(double x, double y) {
        if (!isChatFocused()) return -1;
        if (x < -4.0) return -1;

        double maxX = Math.floor(getWidth() / getScale());
        if (x > maxX) return -1;

        int maxLines = Math.min(getLinesPerPage(), trimmedMessages.size());
        if (y >= 0 && y < maxLines) {
            int index = (int) Math.floor(y + chatScrollbarPos);
            if (index >= 0 && index < trimmedMessages.size()) return index;
        }

        return -1;
    }

    @Override
    public double getMouseXtoChatX() {
        return screenToChatX(mc.mouseHandler.getScaledXPos(mc.getWindow()));
    }

    @Override
    public double getMouseYtoChatY() {
        return screenToChatY(mc.mouseHandler.getScaledYPos(mc.getWindow()));
    }

    @Override
    public double getLineIndex(double x, double y) {
        return getMessageLineIndexAt(x, y);
    }

    @Override
    public List<GuiMessage.Line> getVisibleMessages() {
        return this.trimmedMessages;
    }
}