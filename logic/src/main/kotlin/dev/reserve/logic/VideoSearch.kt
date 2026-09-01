package dev.reserve.logic

import java.text.Normalizer

/**
 * Ranked substring search over the on-device library.
 *
 * Typing on a TV remote is slow, so the ranking exists to put the intended video near the top
 * after as few keystrokes as possible: a title that starts with what you typed beats one that
 * merely contains it, and a folder-only match comes last.
 */
object VideoSearch {

    private val COMBINING_MARKS = Regex("\\p{InCombiningDiacriticalMarks}+")
    private val WHITESPACE = Regex("\\s+")

    /** Case-, accent- and whitespace-insensitive form used for every comparison. */
    fun normalize(raw: String): String {
        val decomposed = Normalizer.normalize(raw, Normalizer.Form.NFD)
        return WHITESPACE.replace(COMBINING_MARKS.replace(decomposed, ""), " ")
            .trim()
            .lowercase()
    }

    fun tokenize(query: String): List<String> =
        normalize(query).split(" ").filter { it.isNotEmpty() }

    /**
     * Rank of [item] against already-normalized [tokens]; lower is better, null means no match.
     *
     * Every token has to appear somewhere in the title or folder, so extra words narrow the
     * result set instead of widening it.
     */
    fun rank(item: VideoItem, tokens: List<String>): Int? {
        if (tokens.isEmpty()) return 0
        val title = normalize(item.title)
        val folder = normalize(item.folder)
        val haystack = if (folder.isEmpty()) title else "$title $folder"
        if (tokens.any { !haystack.contains(it) }) return null

        val phrase = tokens.joinToString(" ")
        return when {
            title == phrase -> 0
            title.startsWith(phrase) -> 1
            title.split(" ").any { it.startsWith(phrase) } -> 2
            title.contains(phrase) -> 3
            tokens.all { title.contains(it) } -> 4
            else -> 5
        }
    }

    /**
     * Matching items, best first. An empty query returns the library untouched so the browser
     * opens on the full list.
     */
    fun search(items: List<VideoItem>, query: String): List<VideoItem> {
        val tokens = tokenize(query)
        if (tokens.isEmpty()) return items
        return items.mapNotNull { item -> rank(item, tokens)?.let { RankedItem(item, it) } }
            .sortedWith(compareBy({ it.rank }, { normalize(it.item.title) }))
            .map { it.item }
    }

    private data class RankedItem(val item: VideoItem, val rank: Int)
}
