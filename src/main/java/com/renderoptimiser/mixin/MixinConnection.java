package com.renderoptimiser.mixin;

import com.renderoptimiser.RenderOptimiser;
import com.renderoptimiser.event.EventBus;
import com.renderoptimiser.event.impl.PacketEvent;
import com.renderoptimiser.event.impl.TickEvent;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.common.ClientboundPingPacket;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Connection.class)
public class MixinConnection {
    @Inject(method = "sendPacket", at = @At("HEAD"), cancellable = true)
    private void onPacketSent(Packet<?> packet, @Nullable ChannelFutureListener channelFutureListener, boolean bl, CallbackInfo ci) {
        if (EventBus.post(new PacketEvent.Sent(packet))) {
            ci.cancel();
        }
    }

    @Inject(method = "channelRead0(Lio/netty/channel/ChannelHandlerContext;Lnet/minecraft/network/protocol/Packet;)V", at = @At(value = "INVOKE", target = "Lnet/minecraft/network/Connection;genericsFtw(Lnet/minecraft/network/protocol/Packet;Lnet/minecraft/network/PacketListener;)V"), cancellable = true)
    private void channelRead0(ChannelHandlerContext channelHandlerContext, Packet<?> packet, CallbackInfo ci) {
        if (packet instanceof ClientboundPingPacket pingPacket && pingPacket.getId() != 0) {
            RenderOptimiser.mc.execute(() -> EventBus.post(TickEvent.Server.INSTANCE));
        }

        if (EventBus.post(new PacketEvent.Received(packet))) {
            ci.cancel();
        }
    }
}