package com.renderoptimiser.utils

import com.renderoptimiser.RenderOptimiser.mc
import com.renderoptimiser.mixin.IKeyMapping
import com.renderoptimiser.ui.utils.Animation.Companion.easeInOutCubic
import com.renderoptimiser.utils.ChatUtils.formattedText
import com.renderoptimiser.utils.ChatUtils.modMessage
import com.renderoptimiser.utils.MathUtils.interpolateYaw
import com.renderoptimiser.utils.MathUtils.lerp
import kotlinx.coroutines.delay
import net.minecraft.core.BlockPos
import net.minecraft.world.InteractionHand
import net.minecraft.world.entity.EquipmentSlot
import net.minecraft.world.entity.player.Inventory
import net.minecraft.world.item.ItemStack
import net.minecraft.world.phys.BlockHitResult
import net.minecraft.world.phys.HitResult
import net.minecraft.world.phys.Vec3
import kotlin.math.abs
import kotlin.math.min

object PlayerUtils {
    fun swingArm() = with(mc.player !!) {
        if (! swinging || this.swingTime < 0) {
            swingingArm = InteractionHand.MAIN_HAND
            swingTime = - 1
            swinging = true
        }
    }

    fun toggleSneak(bl: Boolean) {
        mc.options.keyShift.isDown = bl
    }

    fun leftClick() {
        val key = mc.options.keyAttack
        key.isDown = true
        (key as IKeyMapping).clickCount += 1
        key.isDown = false
    }

    fun rightClick() {
        val key = mc.options.keyUse
        key.isDown = true
        (key as IKeyMapping).clickCount += 1
        key.isDown = false
    }

    fun getSelectionBlock(): BlockPos? {
        val hit = mc.hitResult ?: return null
        if (hit.type != HitResult.Type.BLOCK) return null
        return (hit as BlockHitResult).blockPos
    }

    fun rotate(yaw_: Float, pitch_: Float) = mc.player?.apply {
        var yaw = yRot + MathUtils.normalizeYaw(yaw_ - yRot)
        var pitch = xRot + MathUtils.normalizePitch(pitch_ - xRot)

        val rotations = MathUtils.Rotation(yaw, pitch)
        val lastRotations = MathUtils.Rotation(yRot, xRot)

        val fixedRotations = MathUtils.fixRot(rotations, lastRotations)

        yaw = fixedRotations.yaw
        pitch = fixedRotations.pitch

        pitch = MathUtils.normalizePitch(pitch)

        yRot = yaw
        xRot = pitch

        yHeadRot = yRot
        yBodyRot = yRot

        forceSetRotation(yaw, false, pitch, false)
    }

    fun getHotbarSlot(i: Int): ItemStack? {
        if (! Inventory.isHotbarSlot(i)) return null
        val player = mc.player ?: return null
        return player.inventory.getItem(i)
    }

    fun findHotbarSlot(predicate: (ItemStack) -> Boolean): Int? {
        return (0 .. 8).firstOrNull { idx ->
            val stack = getHotbarSlot(idx) ?: return@firstOrNull false
            if (stack.isEmpty) return@firstOrNull false
            predicate(stack)
        }
    }

    fun getArmor(): List<ItemStack> {
        return listOf(EquipmentSlot.FEET, EquipmentSlot.LEGS, EquipmentSlot.CHEST, EquipmentSlot.HEAD).map {
            mc.player?.getItemBySlot(it) ?: ItemStack.EMPTY
        }
    }

    suspend fun rotateSmoothly(rot: MathUtils.Rotation, time: Long, block: suspend () -> Unit = {}) {
        val currentYaw = MathUtils.normalizeYaw(mc.player?.yRot ?: return)
        val currentPitch = MathUtils.normalizePitch(mc.player?.xRot ?: return)
        val targetYaw = MathUtils.normalizeYaw(rot.yaw)
        val targetPitch = MathUtils.normalizePitch(rot.pitch)
        val tolerance = 1f

        if (abs(currentYaw - targetYaw) <= tolerance && abs(currentPitch - targetPitch) <= tolerance) return block()

        val startTime = System.currentTimeMillis()

        while (true) {
            val elapsed = System.currentTimeMillis() - startTime
            val progress = if (time <= 0) 1.0 else min(elapsed.toDouble() / time, 1.0)
            val easedProgress = easeInOutCubic(progress).toFloat()

            val newYaw = interpolateYaw(currentYaw, targetYaw, easedProgress)
            val newPitch = lerp(currentPitch, targetPitch, easedProgress).toFloat()

            rotate(newYaw, newPitch)

            if (progress >= 1.0) {
                block()
                break
            }

            delay(1)
        }
    }

    suspend fun rotateSmoothly(target: Vec3, time: Long, block: suspend () -> Unit = {}) {
        val rot = MathUtils.calcYawPitch(target)
        rotateSmoothly(rot, time, block)
    }
}