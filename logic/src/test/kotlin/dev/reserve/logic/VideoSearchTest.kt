package dev.reserve.logic

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class VideoSearchTest {

    @Test
    fun `normalize lowercases, strips accents and collapses whitespace`() {
        assertEquals("cafe del mar", VideoSearch.normalize("  Café   Del  Mar "))
        assertEquals("uber", VideoSearch.normalize("Über"))
        assertEquals("na wo tabeta", VideoSearch.normalize("Nã wo tabeta"))
    }

    @Test
    fun `tokenize drops empty fragments`() {
        assertEquals(listOf("two", "words"), VideoSearch.tokenize("  Two   Words  "))
        assertEquals(emptyList<String>(), VideoSearch.tokenize("   "))
    }

    @Test
    fun `an empty query returns the library untouched`() {
        val library = listOf(video(1), video(2))

        assertEquals(library, VideoSearch.search(library, ""))
        assertEquals(library, VideoSearch.search(library, "   "))
    }

    @Test
    fun `every token must match, so extra words narrow the results`() {
        val library = listOf(
            video(1, title = "Bohemian Rhapsody"),
            video(2, title = "Bohemian Like You"),
        )

        assertEquals(2, VideoSearch.search(library, "bohemian").size)
        assertEquals(listOf(1L), VideoSearch.search(library, "bohemian rhapsody").map { it.id })
    }

    @Test
    fun `tokens may match in any order`() {
        val library = listOf(video(1, title = "Bohemian Rhapsody"))

        assertEquals(listOf(1L), VideoSearch.search(library, "rhapsody bohemian").map { it.id })
    }

    @Test
    fun `search is accent and case insensitive`() {
        val library = listOf(video(1, title = "Café Del Mar"))

        assertEquals(listOf(1L), VideoSearch.search(library, "cafe").map { it.id })
        assertEquals(listOf(1L), VideoSearch.search(library, "CAFÉ").map { it.id })
    }

    @Test
    fun `an exact title beats a prefix, which beats a word prefix, which beats a substring`() {
        val exact = video(1, title = "Wembley")
        val prefix = video(2, title = "Wembley 1986")
        val wordPrefix = video(3, title = "Live At Wembley Stadium")
        val substring = video(4, title = "Newembley")

        val tokens = VideoSearch.tokenize("wembley")

        assertEquals(0, VideoSearch.rank(exact, tokens))
        assertEquals(1, VideoSearch.rank(prefix, tokens))
        assertEquals(2, VideoSearch.rank(wordPrefix, tokens))
        assertEquals(3, VideoSearch.rank(substring, tokens))
    }

    @Test
    fun `scattered tokens rank below a contiguous phrase`() {
        val phrase = video(1, title = "Bohemian Rhapsody Live")
        val scattered = video(2, title = "Rhapsody In Blue, Bohemian Mix")

        val tokens = VideoSearch.tokenize("bohemian rhapsody")

        assertEquals(1, VideoSearch.rank(phrase, tokens))
        assertEquals(4, VideoSearch.rank(scattered, tokens))
    }

    @Test
    fun `a folder-only match still matches but ranks last`() {
        val item = video(1, title = "Bohemian Rhapsody", folder = "Karaoke")

        assertEquals(5, VideoSearch.rank(item, VideoSearch.tokenize("karaoke")))
    }

    @Test
    fun `a video matching nothing is excluded`() {
        val item = video(1, title = "Bohemian Rhapsody", folder = "Karaoke")

        assertNull(VideoSearch.rank(item, VideoSearch.tokenize("waterloo")))
        assertTrue(VideoSearch.search(listOf(item), "waterloo").isEmpty())
    }

    @Test
    fun `results come back best first`() {
        val library = listOf(
            video(1, title = "Live At Wembley Stadium"),
            video(2, title = "Wembley"),
            video(3, title = "Wembley 1986"),
        )

        assertEquals(listOf(2L, 3L, 1L), VideoSearch.search(library, "wembley").map { it.id })
    }

    @Test
    fun `equally ranked results are ordered by title so the list is stable`() {
        val library = listOf(
            video(1, title = "Zulu Song"),
            video(2, title = "Alpha Song"),
        )

        assertEquals(listOf(2L, 1L), VideoSearch.search(library, "song").map { it.id })
    }

    @Test
    fun `a folder match combines with a title match`() {
        val library = listOf(
            video(1, title = "Bohemian Rhapsody", folder = "Karaoke"),
            video(2, title = "Bohemian Rhapsody", folder = "Concerts"),
        )

        assertEquals(listOf(1L), VideoSearch.search(library, "bohemian karaoke").map { it.id })
    }
}
