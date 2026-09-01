package dev.reserve.logic

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class DurationFormatTest {

    @Test
    fun `a damaged file reporting no duration shows the unknown marker`() {
        assertEquals(DurationFormat.UNKNOWN, DurationFormat.format(0L))
        assertEquals(DurationFormat.UNKNOWN, DurationFormat.format(-1L))
    }

    @Test
    fun `sub-minute durations still show a minutes field`() {
        assertEquals("0:07", DurationFormat.format(7_000L))
        assertEquals("0:59", DurationFormat.format(59_999L))
    }

    @Test
    fun `minutes and seconds are zero padded`() {
        assertEquals("3:05", DurationFormat.format(185_000L))
        assertEquals("59:59", DurationFormat.format(3_599_000L))
    }

    @Test
    fun `an hour or more adds an hours field`() {
        assertEquals("1:00:00", DurationFormat.format(3_600_000L))
        assertEquals("2:03:04", DurationFormat.format(7_384_000L))
    }

    @Test
    fun `partial seconds are truncated rather than rounded up`() {
        assertEquals("1:00", DurationFormat.format(60_999L))
    }
}
