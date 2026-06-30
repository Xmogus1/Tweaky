package com.renderoptimiser.utils

import com.mojang.authlib.GameProfile
import net.fabricmc.fabric.api.client.rendering.v1.RenderStateDataKey
import net.fabricmc.loader.api.FabricLoader
import net.minecraft.client.renderer.entity.state.AvatarRenderState
import java.util.UUID

/**
 * Interop with NoammAddons (Tweaky's upstream) when both mods are installed.
 *
 * Cosmetic precedence is "Tweaky wins, Noamm fills the gaps":
 *  - NAMES: handled by mixin ordering — Tweaky's MixinFont runs at priority 1500 (before Noamm's
 *    default-1000 hooks), so a name Tweaky already replaced is invisible to Noamm's replacer,
 *    while players Tweaky doesn't cover fall through to Noamm untouched.
 *  - SIZES: handled here. Noamm's AvatarRenderer scale hook looks up the GameProfile its extract
 *    hook stashed under its own RenderStateDataKey. When Tweaky applies a size, [suppressSize]
 *    swaps that stashed profile for a dummy (nil UUID — never in any cosmetics map), so Noamm's
 *    hook skips the player instead of multiplying our scale. Players without a Tweaky size are
 *    never suppressed, so Noamm's sizes still show for them.
 */
object NoammCompat {

    /** Noamm's public GAME_PROFILE_KEY, or null when NoammAddons isn't installed. */
    private val noammProfileKey: RenderStateDataKey<GameProfile>? by lazy {
        if (! FabricLoader.getInstance().isModLoaded("noammaddons")) return@lazy null
        runCatching {
            @Suppress("UNCHECKED_CAST")
            Class.forName("com.github.noamm9.features.impl.dev.Cosmetics")
                .getField("GAME_PROFILE_KEY")
                .get(null) as RenderStateDataKey<GameProfile>
        }.onSuccess {
            com.renderoptimiser.RenderOptimiser.logger.info("NoammCompat: NoammAddons detected — Tweaky cosmetics take precedence")
        }.onFailure {
            com.renderoptimiser.RenderOptimiser.logger.warn("NoammCompat: NoammAddons present but its cosmetics key was not found — sizes may stack", it)
        }.getOrNull()
    }

    private val dummyProfile = GameProfile(UUID(0L, 0L), "tweaky")

    /** Makes NoammAddons skip its custom-size scaling for this render state. */
    fun suppressSize(state: AvatarRenderState) {
        val key = noammProfileKey ?: return
        runCatching { state.setData(key, dummyProfile) }
    }

    private class NameReplacerAccess(
        val instance: Any,
        val map: MutableMap<String, String>,
        val rebuild: java.lang.reflect.Method,
    )

    /** Noamm's TextReplacer internals (its name→replacement map + the engine-rebuilding add()). */
    private val noammNameReplacer: NameReplacerAccess? by lazy {
        if (! FabricLoader.getInstance().isModLoaded("noammaddons")) return@lazy null
        runCatching {
            val cls = Class.forName("com.github.noamm9.features.impl.dev.text.TextReplacer")
            val instance = cls.getField("INSTANCE").get(null)
            val mapField = cls.getDeclaredField("replaceMap").apply { isAccessible = true }
            @Suppress("UNCHECKED_CAST")
            val map = mapField.get(null) as MutableMap<String, String>
            NameReplacerAccess(instance, map, cls.getMethod("add", Map::class.java))
        }.getOrNull()
    }

    /**
     * Removes [names] from Noamm's name-replacement map and rebuilds its matcher, so Noamm can
     * never rewrite (or re-rewrite the output of) a name Tweaky owns. Ordering-independent —
     * without this, a Tweaky name CONTAINING the real name gets re-replaced by Noamm no matter
     * which hook runs first. Re-run periodically: Noamm refetches its list on boot/reload.
     */
    fun suppressNames(names: Collection<String>) {
        if (names.isEmpty()) return
        val access = noammNameReplacer ?: return
        runCatching {
            var removed = false
            for (name in names) if (access.map.remove(name) != null) removed = true
            // add(emptyMap) makes Noamm rebuild its Aho-Corasick engine from the purged map
            if (removed) access.rebuild.invoke(access.instance, emptyMap<String, String>())
        }
    }

    fun status(): String = when {
        ! FabricLoader.getInstance().isModLoaded("noammaddons") -> "not installed"
        noammProfileKey != null -> "override armed" + if (noammNameReplacer != null) " (+names)" else " (sizes only)"
        else -> "installed but key NOT found"
    }
}
