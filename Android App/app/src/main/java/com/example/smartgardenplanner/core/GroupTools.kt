package com.example.smartgardenplanner.core

import kotlin.math.hypot

/**
 * Working with a whole clump at once (FR-061): find the group a plant belongs to (plants of the same variety that
 * touch, within 1.6 × their spacing of each other), move it, or lay it out again as other rows × columns, keeping its
 * centre. The caller checks the result with the normal placement rules before storing it. Pure Kotlin.
 */
object GroupTools {

    /** The plants of [start]'s variety connected to it (itself included). */
    fun groupOf(nodes: List<PlantedNodeEntity>, start: PlantedNodeEntity, lookup: (String) -> SeedEntity?): List<PlantedNodeEntity> {
        val r = lookup(start.seedCode)?.exclusionRadiusM ?: 0.3f
        val link = 2f * r * 1.6f + 0.01f
        val same = nodes.filter { it.seedCode == start.seedCode }.toMutableList()
        val out = mutableListOf<PlantedNodeEntity>()
        val queue = ArrayDeque<PlantedNodeEntity>()
        same.firstOrNull { it.id == start.id }?.let { queue += it; same.remove(it) } ?: return listOf(start)
        while (queue.isNotEmpty()) {
            val c = queue.removeFirst(); out += c
            val near = same.filter { hypot((it.coordinateXM - c.coordinateXM).toDouble(), (it.coordinateYM - c.coordinateYM).toDouble()) <= link }
            same.removeAll(near); queue.addAll(near)
        }
        return out
    }

    /** Plants whose centres are inside the rectangle (x0, y0)–(x1, y1), any order of corners. */
    fun inRect(nodes: List<PlantedNodeEntity>, x0: Float, y0: Float, x1: Float, y1: Float): List<PlantedNodeEntity> {
        val ax = minOf(x0, x1); val bx = maxOf(x0, x1); val ay = minOf(y0, y1); val by = maxOf(y0, y1)
        return nodes.filter { it.coordinateXM in ax..bx && it.coordinateYM in ay..by }
    }

    fun moved(group: List<PlantedNodeEntity>, dx: Float, dy: Float): List<PlantedNodeEntity> =
        group.map { it.copy(coordinateXM = it.coordinateXM + dx, coordinateYM = it.coordinateYM + dy) }

    /**
     * Lays [group] out again as [rows] (plants per row, first row at the top), rows running left–right, centred where the
     * group was. Plants of different varieties keep their own variety in reading order. Spacing = the largest variety's
     * spacing × [marginMultiplier].
     */
    fun rearranged(group: List<PlantedNodeEntity>, rows: List<Int>, lookup: (String) -> SeedEntity?, marginMultiplier: Float = 1f): List<PlantedNodeEntity>? {
        if (group.isEmpty() || rows.sum() != group.size || rows.any { it <= 0 }) return null
        val r = group.maxOf { lookup(it.seedCode)?.exclusionRadiusM ?: 0.3f }
        val pitch = 2f * r * marginMultiplier + 0.001f
        val cx = group.map { it.coordinateXM }.average().toFloat()
        val cy = group.map { it.coordinateYM }.average().toFloat()
        val cols = rows.max()
        val sorted = group.sortedWith(compareBy({ it.coordinateYM }, { it.coordinateXM }))
        val out = mutableListOf<PlantedNodeEntity>()
        var i = 0
        rows.forEachIndexed { ri, n ->
            val off = (cols - n) * pitch / 2f
            for (ci in 0 until n) {
                val x = cx - (cols - 1) * pitch / 2f + off + ci * pitch
                val y = cy - (rows.size - 1) * pitch / 2f + ri * pitch
                out += sorted[i++].copy(coordinateXM = x, coordinateYM = y)
            }
        }
        return out
    }

    /** The plants in [moved] that break a rule against [others] or leave the plot (for a message; nothing is changed). */
    fun problems(
        moved: List<PlantedNodeEntity>, others: List<PlantedNodeEntity>, plot: PlotEntity, lookup: (String) -> SeedEntity?,
        marginMultiplier: Float = 1f, enforceCompanionRules: Boolean = true, guilds: List<Guild> = emptyList()
    ): Int {
        val validator = CompanionPlantingValidator()
        return moved.count { n ->
            val seed = lookup(n.seedCode) ?: return@count true
            !PlotShape.contains(plot, n.coordinateXM, n.coordinateYM) ||
                !validator.validatePlacement(n, seed, others + moved.filter { it !== n }, lookup, marginMultiplier, enforceCompanionRules, guilds).isValid
        }
    }
}

/**
 * Interface text in other languages (FR-062). Every text the interface shows is written in US English; a dictionary
 * maps it to another language, so no English is left when a dictionary is complete. Entries are:
 *  - exact texts ("Plan it" → "Planificar");
 *  - templates with {0}, {1}… for numbers, names and lists ("{0} plants placed" → "{0} plantas colocadas"); the parts
 *    are themselves translated (a list "Tomato, Pepper" part by part), so a sentence built at run time still comes out
 *    whole in the other language;
 *  - plant species names; a variety "Species - Cultivar" gets the species translated and keeps the cultivar's
 *    registered name, unless the cultivar is a plain description (e.g. "Mid-Season") that has its own entry.
 * Texts without an entry stay in English, so a partial dictionary is safe.
 * Dictionary files: one entry per line, "English<TAB>Translation"; lines starting with # are comments; "\n" stands
 * for a line break. Pure Kotlin.
 */
object I18n {

    data class Language(val code: String, val name: String)

    /** Languages with a dictionary file (i18n/<code>.txt). English needs none. */
    val LANGUAGES = listOf(Language("en", "English"), Language("es", "Español"))

    private class Template(val parts: List<String>, val out: String, key: String) {
        val literal = parts.sumOf { it.length }
        /** "{0} ({1})" → "{0} ({1})": only useful when a part is translated. */
        val identity = key == out
        val hasSemicolon = ';' in key
        /** Matches [t] against the literal parts, returning the pieces between them, or null. */
        fun match(t: String): List<String>? {
            if (!t.startsWith(parts.first()) || !t.endsWith(parts.last()) || t.length < literal) return null
            val groups = mutableListOf<String>()
            var pos = parts.first().length
            for (k in 1 until parts.size) {
                val lit = parts[k]
                val end = if (k == parts.size - 1) {
                    if (t.length - lit.length < pos) return null
                    t.length - lit.length
                } else {
                    // Smallest non-empty piece up to the next literal part.
                    if (lit.isEmpty()) return null
                    t.indexOf(lit, pos + 1).takeIf { it >= 0 } ?: return null
                }
                if (end <= pos) return null
                groups += t.substring(pos, end)
                pos = end + lit.length
            }
            return groups
        }
    }

    private var exact: Map<String, String> = emptyMap()
    private var byPrefix: Map<String, List<Template>> = emptyMap()   // templates starting with text, by first 3 chars
    private var bySuffix: Map<String, List<Template>> = emptyMap()   // templates starting with {0}, by last 3 chars
    private var loose: List<Template> = emptyList()                  // e.g. "{0} × {1}": literal only in the middle
    private val memo = HashMap<String, String>()
    private val placeholder = Regex("\\{\\d+\\}")
    var language: String = "en"
        private set

    fun parse(lines: List<String>): Map<String, String> = lines.asSequence()
        .filter { it.isNotBlank() && !it.startsWith("#") && '\t' in it }
        .map { it.substringBefore('\t').trim().replace("\\n", "\n") to it.substringAfter('\t').trim().replace("\\n", "\n") }
        .filter { it.first.isNotEmpty() && it.second.isNotEmpty() }
        .toMap()

    /** Switch language; [dictionary] from [parse] (empty for English). */
    fun use(code: String, dictionary: Map<String, String>) {
        language = code
        memo.clear()
        exact = dictionary.filterKeys { !placeholder.containsMatchIn(it) }
        val templates = dictionary.filterKeys { placeholder.containsMatchIn(it) }.map { (k, v) -> Template(k.split(placeholder), v, k) }
            .sortedByDescending { it.literal }
        // Indexed by their first (or else last) 3 literal characters; shorter ones, e.g. "{0} ({1})", are tried last.
        byPrefix = templates.filter { it.parts.first().length >= 3 }.groupBy { it.parts.first().take(3) }
        bySuffix = templates.filter { it.parts.first().length < 3 && it.parts.last().length >= 3 }.groupBy { it.parts.last().takeLast(3) }
        loose = templates.filter { it.parts.first().length < 3 && it.parts.last().length < 3 }
    }

    /** The text in the current language, or the text itself when there's no entry. */
    fun tr(text: String): String {
        if (language == "en" || text.isBlank()) return text
        memo[text]?.let { return it }
        val start = text.indexOfFirst { !it.isWhitespace() }
        val end = text.indexOfLast { !it.isWhitespace() }
        val out = text.substring(0, start) + translate(text.substring(start, end + 1), 0) + text.substring(end + 1)
        if (memo.size > 20000) memo.clear()
        memo[text] = out
        return out
    }

    private fun translate(t: String, depth: Int): String {
        exact[t]?.let { return it }
        if (depth > 6 || t.isEmpty()) return t
        direct(t, depth)?.let { return it }
        return fallback(t, depth)
    }

    /** Exact entry, variety name or template; null when none applies. */
    private fun direct(t: String, depth: Int): String? {
        exact[t]?.let { return it }
        // A variety name: "Species - Cultivar".
        val dash = t.indexOf(" - ")
        if (dash > 0 && t.indexOf(" - ", dash + 3) < 0 && t.length < 80) {
            val species = t.substring(0, dash); val cultivar = t.substring(dash + 3)
            val sp = exact[species]
            if (sp != null) return sp + " - " + (exact[cultivar] ?: cultivar)
        }
        if (depth > 6) return null
        val candidates = (byPrefix[t.take(3)].orEmpty() + bySuffix[t.takeLast(3)].orEmpty()).sortedByDescending { it.literal } + loose
        for (tpl in candidates) {
            val groups = tpl.match(t) ?: continue
            // A template that starts with a part ("{0}: {1} in …") never matches across list items ("a; b").
            if (tpl.parts.first().isEmpty() && !tpl.hasSemicolon && groups.any { "; " in it }) continue
            // A part in quotes (“{0}”, '{0}') is a name the user typed, e.g. a plot name: it stays as written.
            val parts = groups.mapIndexed { i, g -> if (tpl.parts[i].endsWith("“") || tpl.parts[i].endsWith("'") || tpl.parts[i].endsWith("\"")) g else part(g, depth + 1) }
            if (tpl.literal == 0 || (tpl.identity && parts == groups)) continue
            // Loose templates ("{0} and {1}") only when every part is translated, so "rows and columns" isn't touched.
            if (tpl in loose && parts.indices.any { parts[it] == groups[it] && groups[it].any { c -> c.isLetter() } }) continue
            var out = tpl.out
            parts.forEachIndexed { i, g -> out = out.replace("{$i}", g) }
            return out
        }
        return null
    }

    private fun fallback(t: String, depth: Int): String {
        // A bullet or symbol in front ("• ", "✓ ", "→ ", "+ "): translate what follows it.
        val lead = t.indexOfFirst { it.isLetterOrDigit() || it in "(\"“¿¡" }
        if (lead > 0 && t[lead - 1] == ' ') {
            val rest = translate(t.substring(lead), depth + 1)
            if (rest != t.substring(lead)) return t.substring(0, lead) + rest
        }
        // A final full stop or colon that the dictionary entry doesn't have.
        if (t.length > 2 && t.last() in ".:") {
            val body = t.dropLast(1)
            val tb = translate(body, depth + 1)
            if (tb != body) return tb + t.last()
        }
        // Several sentences in one text: each one on its own.
        // Several sentences in one text: the longest runs of sentences that have an entry, else each one on its own.
        val sentences = sentenceBreak.split(t)
        if (sentences.size > 1) {
            val out = mutableListOf<String>()
            var i = 0
            while (i < sentences.size) {
                var done = false
                for (j in sentences.size downTo i + 2) {
                    if (i == 0 && j == sentences.size) continue
                    val run = (i until j).joinToString(" ") { sentences[it] }
                    val tr = direct(run, depth + 1)
                    if (tr != null) { out += tr; i = j; done = true; break }
                }
                if (!done) { out += translate(sentences[i], depth + 1); i++ }
            }
            val joined = out.joinToString(" ")
            if (joined != sentences.joinToString(" ")) return joined
        }
        // "A — B": both sides.
        val dashAt = t.indexOf(" — ")
        if (dashAt > 0) {
            val a = translate(t.substring(0, dashAt), depth + 1); val b = translate(t.substring(dashAt + 3), depth + 1)
            if (a != t.substring(0, dashAt) || b != t.substring(dashAt + 3)) return "$a — $b"
        }
        // "Label: the rest", where the label has an entry.
        val colonAt = t.indexOf(": ")
        if (colonAt > 0) {
            val head = t.substring(0, colonAt + 1)
            val th = exact[head]
            if (th != null) return th + " " + part(t.substring(colonAt + 2), depth + 1)
        }
        // A plain list, when every item has an entry.
        if (", " in t) {
            val items = t.split(", ")
            val out = items.map { translate(it, depth + 1) }
            if (out.indices.all { out[it] != items[it] }) return out.joinToString(", ")
        }
        return t
    }

    private val sentenceBreak = Regex("(?<=[.!?])\\s+(?=[A-Z0-9“\"(])")

    /** A piece of a template: translated whole, or as a list ("a, b and c") item by item. */
    private fun part(g: String, depth: Int): String {
        if (g.all { it.isDigit() || it == '.' || it == '-' || it == ' ' || it == ',' }) return g
        val t = g.trim()
        if (t.isEmpty() || depth > 8) return g
        // An entry or template for the whole piece first; then a list item by item; only then the looser rules.
        direct(t, depth)?.let { return g.replace(t, it) }
        for (sep in listOf("; ", ", ", " · ", " + ")) {
            if (sep in t) {
                val items = t.split(sep)
                val out = items.map { part(it, depth + 1) }
                if (out != items) return g.replace(t, out.joinToString(sep))
            }
        }
        val whole = fallback(t, depth)
        return if (whole != t) g.replace(t, whole) else g
    }

    /** Share of [texts] that have a translation (for the language menu, e.g. "about 40 % translated"). */
    fun coverage(texts: List<String>): Double = if (texts.isEmpty()) 0.0 else texts.count { tr(it) != it }.toDouble() / texts.size
}
