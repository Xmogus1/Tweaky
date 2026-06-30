package com.renderoptimiser.utils.items

import com.renderoptimiser.utils.ChatUtils.formattedText
import com.renderoptimiser.utils.ChatUtils.removeFormatting
import com.renderoptimiser.utils.items.ItemRarity.Companion.PET_PATTERN
import com.renderoptimiser.utils.items.ItemRarity.Companion.RARITY_PATTERN
import com.renderoptimiser.utils.items.ItemRarity.Companion.rarityCache
import net.minecraft.core.component.DataComponents
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.component.CustomData
import net.minecraft.world.item.component.ItemLore
import java.util.concurrent.*
import kotlin.jvm.optionals.getOrNull


object ItemUtils {
    val idToNameMap = ConcurrentHashMap<String, String>()
    val nameToIdMap = ConcurrentHashMap<String, String>()

    val ItemStack.customData get() = getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag()
    val ItemStack.lore get() = getOrDefault(DataComponents.LORE, ItemLore.EMPTY).styledLines().map { it.formattedText }
    val ItemStack.itemUUID get() = customData.getString("uuid").getOrNull() ?: ""
    val ItemStack.skyblockId: String
        get() {
            if (isEmpty) return ""
            val customData = customData
            var sbItemID: String? = null

            if (customData.contains("id")) sbItemID = customData.getString("id").getOrNull()?.replace(":", "-")

            return sbItemID.orEmpty()
        }

    fun getSkullTexture(stack: ItemStack): String? {
        if (stack.isEmpty) return null
        val profile = stack.get(DataComponents.PROFILE) ?: return null
        val properties = profile.partialProfile().properties
        return properties["textures"].firstOrNull()?.value
    }

    fun getSkullId(stack: ItemStack): String? {
        if (stack.isEmpty) return null
        val profile = stack.get(DataComponents.PROFILE) ?: return null
        return profile.partialProfile().id.toString()
    }

    fun ItemStack.hasGlint() = componentsPatch.toString().contains("minecraft:enchantment_glint_override=>true")

    fun getRarity(item: ItemStack?): ItemRarity {
        item ?: return ItemRarity.NONE
        if (item.isEmpty) return ItemRarity.NONE
        rarityCache[item]?.let { return it }

        val rarity = run {
            val lore = item.lore.takeUnless(List<*>::isEmpty) ?: return@run ItemRarity.NONE

            for (i in lore.indices) {
                val line = lore[lore.lastIndex - i]
                val rarityName = RARITY_PATTERN.find(line)?.groups?.get("rarity")?.value?.removeFormatting()?.substringAfter("SHINY ")
                ItemRarity.entries.find { it.rarityName == rarityName }?.let { return@run it }
            }

            PET_PATTERN.find(item.hoverName.formattedText)?.groupValues?.getOrNull(1)?.let(ItemRarity::byBaseColor) ?: ItemRarity.NONE
        }

        rarityCache[item] = rarity
        return rarity
    }
}