package com.renderoptimiser.event.impl

import com.renderoptimiser.event.Event
import net.minecraft.network.protocol.Packet

abstract class PacketEvent: Event(cancelable = true) {
    class Sent(val packet: Packet<*>): PacketEvent()
    class Received(val packet: Packet<*>): PacketEvent()
}