package com.renderoptimiser.features.impl.visual.text

import com.google.gson.JsonParser
import com.mojang.serialization.JsonOps
import net.minecraft.ChatFormatting
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.ComponentSerialization
import net.minecraft.network.chat.MutableComponent
import net.minecraft.network.chat.Style
import net.minecraft.network.chat.TextColor
import net.minecraft.util.FormattedCharSequence

/**
 * Rewrites player names to their cosmetic names everywhere text renders (nametags, tab list,
 * chat, scoreboard...) via the Font hooks in MixinFont. Matching is done with an Aho-Corasick
 * automaton so any number of names costs one pass over the text.
 *
 * Replacement strings support `&`/`§` legacy codes and `&#RRGGBB` hex colors. Hex becomes a REAL
 * [TextColor] style on the built Component — vanilla's renderer has no §-hex format, so a naive
 * `§x§R§R...` string renders as whatever the last legacy digit is. For the raw-string draw path
 * (which can't carry styles at all) hex is approximated to the nearest of the 16 legacy colors.
 *
 * The map is replaced wholesale by [setAll] on every cosmetics broadcast (handles revokes too).
 */
object TextReplacer {
    private val engine = AhoCorasick()

    /** The 16 legacy chat colors (char → RGB), fixed since forever — used for the hex fallback. */
    private val LEGACY_COLORS = listOf(
        '0' to 0x000000, '1' to 0x0000AA, '2' to 0x00AA00, '3' to 0x00AAAA,
        '4' to 0xAA0000, '5' to 0xAA00AA, '6' to 0xFFAA00, '7' to 0xAAAAAA,
        '8' to 0x555555, '9' to 0x5555FF, 'a' to 0x55FF55, 'b' to 0x55FFFF,
        'c' to 0xFF5555, 'd' to 0xFF55FF, 'e' to 0xFFFF55, 'f' to 0xFFFFFF,
    )

    /** Replaces ALL current replacements (empty map clears). Called on the MC thread. */
    fun setAll(map: Map<String, String>) {
        engine.build(HashMap(map)) { raw -> parseStyled(raw) }
    }

    @JvmStatic
    fun handleString(text: String): String {
        if (text.isEmpty()) return text
        return engine.replaceString(text)
    }

    @JvmStatic
    fun handleComponent(component: Component): Component {
        if (engine.isEmpty()) return component
        return engine.replaceComponent(component)
    }

    @JvmStatic
    fun handleCharSequence(seq: FormattedCharSequence): FormattedCharSequence {
        if (engine.isEmpty()) return seq
        return engine.replaceCharSequence(seq)
    }

    /** Styled-component form of [parseStyled] for one-off messages (server panel chat etc.). */
    fun styledComponent(raw: String): MutableComponent = parseStyled(raw).second

    /**
     * Parses `&`/`§` legacy codes and `&#RRGGBB` hex into:
     *  - a legacy §-string (hex → nearest legacy color) for raw-string draws, and
     *  - a properly styled [MutableComponent] (hex → real [TextColor]) for component draws.
     *
     * Gradient-tool JSON (`{"version":5,"text":"...","colors":[{"hex":"#RRGGBB","pos":0..100}]}`)
     * is also accepted and rendered as a smooth per-character gradient.
     */
    fun parseStyled(raw: String): Pair<String, MutableComponent> {
        parseGradientJson(raw)?.let { return it }
        parseComponentJson(raw)?.let { return it }
        return parseCodes(raw)
    }

    /**
     * Vanilla text-component JSON (`{"text":"","extra":[{"text":"c","color":"#D4C500","bold":true,
     * "shadow_color":[r,g,b,a]},…]}`) — the format NoammAddons names use — parsed with Minecraft's
     * own component codec, so every style field (incl. shadow_color) works. Plain JsonOps is
     * enough: cosmetic names never contain the hover events that would need registry access.
     * Must run AFTER [parseGradientJson] — the lenient vanilla codec would half-parse gradient
     * JSON (it ignores unknown keys like "colors").
     */
    private fun parseComponentJson(raw: String): Pair<String, MutableComponent>? {
        val trimmed = raw.trim()
        if (! trimmed.startsWith("{") && ! trimmed.startsWith("[")) return null
        return runCatching {
            val json = JsonParser.parseString(trimmed)
            val comp = ComponentSerialization.CODEC.parse(JsonOps.INSTANCE, json)
                .result().orElse(null)?.copy() ?: return null
            legacyApprox(comp) to comp
        }.getOrNull()
    }

    /** Flattens a component into a legacy §-string (nearest color + bold/italic) for raw-string draws. */
    private fun legacyApprox(comp: Component): String {
        val sb = StringBuilder()
        comp.visualOrderText.accept { _, style, cp ->
            style.color?.let { sb.append('§').append(nearestLegacyChar(it.value)) }
            if (style.isBold) sb.append("§l")
            if (style.isItalic) sb.append("§o")
            sb.appendCodePoint(cp)
            true
        }
        return sb.toString()
    }

    /**
     * Renders gradient-tool JSON: each character gets the gradient color at its position.
     * Also honors "shadowcolors" (a second gradient for the text shadow), plus the
     * bold/italic/underline/strikethrough/obfuscated flags.
     */
    private fun parseGradientJson(raw: String): Pair<String, MutableComponent>? {
        val trimmed = raw.trim()
        if (! trimmed.startsWith("{") || ! trimmed.endsWith("}")) return null
        return runCatching {
            val obj = JsonParser.parseString(trimmed).asJsonObject
            val text = obj.get("text")?.asString ?: return null
            val stops = parseStops(obj, "colors") ?: return null
            if (text.isEmpty() || stops.isEmpty()) return null
            val shadowStops = parseStops(obj, "shadowcolors")?.takeIf { it.isNotEmpty() }

            fun flag(vararg keys: String) = keys.any { obj.get(it)?.takeIf { e -> e.isJsonPrimitive }?.asBoolean == true }
            val bold = flag("bold")
            val italic = flag("italic")
            val underlined = flag("underlined", "underline")
            val strikethrough = flag("strikethrough")
            val obfuscated = flag("obfuscated", "magic")

            var legacyCodes = ""
            if (bold) legacyCodes += "§l"
            if (italic) legacyCodes += "§o"
            if (underlined) legacyCodes += "§n"
            if (strikethrough) legacyCodes += "§m"
            if (obfuscated) legacyCodes += "§k"

            val root = Component.literal("")
            val legacy = StringBuilder()
            val cps = text.codePoints().toArray()
            cps.forEachIndexed { i, cp ->
                val pos = if (cps.size <= 1) 0.0 else i * 100.0 / (cps.size - 1)
                val rgb = sampleGradient(stops, pos)
                val ch = String(Character.toChars(cp))

                var style = Style.EMPTY.withColor(TextColor.fromRgb(rgb))
                if (bold) style = style.withBold(true)
                if (italic) style = style.withItalic(true)
                if (underlined) style = style.withUnderlined(true)
                if (strikethrough) style = style.withStrikethrough(true)
                if (obfuscated) style = style.withObfuscated(true)
                shadowStops?.let { style = style.withShadowColor((0xFF shl 24) or sampleGradient(it, pos)) }

                root.append(Component.literal(ch).withStyle(style))
                // legacy form: color code first (it resets modifiers), then the modifier codes
                legacy.append('§').append(nearestLegacyChar(rgb)).append(legacyCodes).append(ch)
            }
            legacy.toString() to root
        }.getOrNull()
    }

    /** Parses a `[{"hex":"#RRGGBB","pos":0..100}, ...]` stop array, sorted by pos. */
    private fun parseStops(obj: com.google.gson.JsonObject, key: String): List<Pair<Double, Int>>? {
        val arr = runCatching { obj.getAsJsonArray(key) }.getOrNull() ?: return null
        return arr.mapNotNull { el ->
            val o = runCatching { el.asJsonObject }.getOrNull() ?: return@mapNotNull null
            val hex = o.get("hex")?.asString?.removePrefix("#")?.takeIf { it.length == 6 } ?: return@mapNotNull null
            val pos = o.get("pos")?.asDouble ?: return@mapNotNull null
            pos to hex.toInt(16)
        }.sortedBy { it.first }
    }

    /** Linear RGB interpolation between the two stops surrounding [pos] (percent, 0..100). */
    private fun sampleGradient(stops: List<Pair<Double, Int>>, pos: Double): Int {
        if (pos <= stops.first().first) return stops.first().second
        if (pos >= stops.last().first) return stops.last().second
        for (i in 0 until stops.size - 1) {
            val (p0, c0) = stops[i]
            val (p1, c1) = stops[i + 1]
            if (pos > p1) continue
            val f = if (p1 - p0 <= 0.0) 1.0 else (pos - p0) / (p1 - p0)
            fun lerp(a: Int, b: Int) = (a + (b - a) * f).toInt().coerceIn(0, 255)
            val r = lerp((c0 shr 16) and 0xFF, (c1 shr 16) and 0xFF)
            val g = lerp((c0 shr 8) and 0xFF, (c1 shr 8) and 0xFF)
            val b = lerp(c0 and 0xFF, c1 and 0xFF)
            return (r shl 16) or (g shl 8) or b
        }
        return stops.last().second
    }

    private fun parseCodes(raw: String): Pair<String, MutableComponent> {
        val root = Component.literal("")
        val legacy = StringBuilder(raw.length)
        val run = StringBuilder()
        var style = Style.EMPTY

        fun flush() {
            if (run.isEmpty()) return
            root.append(Component.literal(run.toString()).withStyle(style))
            run.setLength(0)
        }

        var i = 0
        while (i < raw.length) {
            val c = raw[i]
            val marker = c == '&' || c == '§'
            when {
                // &#RRGGBB → true hex style; nearest legacy color in the string form
                marker && i + 7 < raw.length && raw[i + 1] == '#' && isHexRun(raw, i + 2) -> {
                    val rgb = raw.substring(i + 2, i + 8).toInt(16)
                    flush()
                    style = Style.EMPTY.withColor(TextColor.fromRgb(rgb))
                    legacy.append('§').append(nearestLegacyChar(rgb))
                    i += 8
                }

                // &c / §l etc. → vanilla legacy semantics (colors reset modifiers)
                marker && i + 1 < raw.length && ChatFormatting.getByCode(raw[i + 1]) != null -> {
                    val fmt = ChatFormatting.getByCode(raw[i + 1]) !!
                    flush()
                    style = style.applyLegacyFormat(fmt)
                    legacy.append('§').append(raw[i + 1])
                    i += 2
                }

                else -> {
                    run.append(c)
                    legacy.append(c)
                    i ++
                }
            }
        }
        flush()
        return legacy.toString() to root
    }

    private fun isHexRun(s: String, start: Int): Boolean {
        for (j in start until start + 6) {
            val c = s[j]
            if (c !in '0' .. '9' && c !in 'a' .. 'f' && c !in 'A' .. 'F') return false
        }
        return true
    }

    private fun nearestLegacyChar(rgb: Int): Char {
        val r = (rgb shr 16) and 0xFF
        val g = (rgb shr 8) and 0xFF
        val b = rgb and 0xFF
        var best = 'f'
        var bestDist = Int.MAX_VALUE
        for ((char, color) in LEGACY_COLORS) {
            val dr = r - ((color shr 16) and 0xFF)
            val dg = g - ((color shr 8) and 0xFF)
            val db = b - (color and 0xFF)
            val dist = dr * dr + dg * dg + db * db
            if (dist < bestDist) {
                bestDist = dist
                best = char
            }
        }
        return best
    }
}
