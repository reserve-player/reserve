package dev.reserve

import android.Manifest
import android.content.Context
import android.database.MatrixCursor
import android.os.Build
import android.provider.MediaStore
import android.provider.Settings
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

private const val BASE_URI = "content://media/external/video/media"
private const val COLUMN_BUCKET = "bucket_display_name"
private const val COLUMN_DATA = "_data"

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class MediaStoreVideoSourceTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    private fun cursorWith(vararg columns: String) = MatrixCursor(arrayOf(*columns))

    // ---- permanent denial -----------------------------------------------------------------
    //
    // Android reports `shouldShowRequestPermissionRationale = false` both BEFORE the first ask
    // and AFTER a permanent denial, so these three cases are the whole reason the "already
    // asked" flag exists. Getting them backwards either strands a first-time user in settings
    // or leaves the dead button this was written to remove.

    @Test
    fun `access is not permanently denied before the dialog has ever been shown`() {
        assertFalse(MediaStoreVideoSource.isPermanentlyDenied(context, showRationale = false))
    }

    @Test
    fun `a soft denial is not permanent while the system still offers the dialog`() {
        MediaStoreVideoSource.markRequested(context)

        assertTrue(MediaStoreVideoSource.wasRequested(context))
        assertFalse(MediaStoreVideoSource.isPermanentlyDenied(context, showRationale = true))
    }

    @Test
    fun `access is permanently denied once asked and the system stops offering the dialog`() {
        MediaStoreVideoSource.markRequested(context)

        assertTrue(MediaStoreVideoSource.isPermanentlyDenied(context, showRationale = false))
    }

    @Test
    fun `granted access is never reported as permanently denied`() {
        MediaStoreVideoSource.markRequested(context)
        shadowOf(ApplicationProvider.getApplicationContext<android.app.Application>())
            .grantPermissions(Manifest.permission.READ_MEDIA_VIDEO)

        assertFalse(MediaStoreVideoSource.isPermanentlyDenied(context, showRationale = false))
    }

    @Test
    fun `the settings intent points at this app rather than the settings root`() {
        val intent = MediaStoreVideoSource.appSettingsIntent("dev.reserve")

        assertEquals(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, intent.action)
        assertEquals("dev.reserve", intent.data?.schemeSpecificPart)
    }

    private fun modernCursor() = cursorWith(
        MediaStore.Video.Media._ID,
        MediaStore.Video.Media.TITLE,
        MediaStore.Video.Media.DISPLAY_NAME,
        MediaStore.Video.Media.DURATION,
        COLUMN_BUCKET,
    )

    @Test
    fun `maps a well-formed row to a video item`() {
        val cursor = modernCursor()
        cursor.addRow(arrayOf(12L, "Bohemian Rhapsody", "bohemian.mp4", 355_000L, "Karaoke"))

        val items = MediaStoreVideoSource.readCursor(cursor, BASE_URI)

        assertEquals(1, items.size)
        val item = items.single()
        assertEquals(12L, item.id)
        assertEquals("Bohemian Rhapsody", item.title)
        assertEquals("$BASE_URI/12", item.uri)
        assertEquals(355_000L, item.durationMs)
        assertEquals("Karaoke", item.folder)
    }

    @Test
    fun `a missing title falls back to the file name`() {
        val cursor = modernCursor()
        cursor.addRow(arrayOf(3L, null, "holiday-clip.mp4", 1_000L, "Movies"))

        assertEquals("holiday-clip.mp4", MediaStoreVideoSource.readCursor(cursor, BASE_URI).single().title)
    }

    @Test
    fun `a blank title is treated as missing`() {
        val cursor = modernCursor()
        cursor.addRow(arrayOf(4L, "   ", "clip.mp4", 1_000L, "Movies"))

        assertEquals("clip.mp4", MediaStoreVideoSource.readCursor(cursor, BASE_URI).single().title)
    }

    @Test
    fun `a row with no usable name at all still produces an item`() {
        val cursor = modernCursor()
        cursor.addRow(arrayOf(7L, null, null, 1_000L, "Movies"))

        assertEquals("Video 7", MediaStoreVideoSource.readCursor(cursor, BASE_URI).single().title)
    }

    @Test
    fun `a damaged file reporting no duration maps to zero rather than crashing`() {
        val cursor = modernCursor()
        cursor.addRow(arrayOf(5L, "Broken", "broken.mp4", null, "Movies"))

        assertEquals(0L, MediaStoreVideoSource.readCursor(cursor, BASE_URI).single().durationMs)
    }

    @Test
    fun `the folder comes from the file path when the bucket column is absent`() {
        val cursor = cursorWith(
            MediaStore.Video.Media._ID,
            MediaStore.Video.Media.TITLE,
            MediaStore.Video.Media.DISPLAY_NAME,
            MediaStore.Video.Media.DURATION,
            COLUMN_DATA,
        )
        cursor.addRow(arrayOf(9L, "Clip", "clip.mp4", 1_000L, "/storage/emulated/0/Karaoke/clip.mp4"))

        assertEquals("Karaoke", MediaStoreVideoSource.readCursor(cursor, BASE_URI).single().folder)
    }

    @Test
    fun `a row with neither folder source reports an empty folder`() {
        val cursor = cursorWith(
            MediaStore.Video.Media._ID,
            MediaStore.Video.Media.TITLE,
            MediaStore.Video.Media.DURATION,
        )
        cursor.addRow(arrayOf(1L, "Clip", 1_000L))

        assertEquals("", MediaStoreVideoSource.readCursor(cursor, BASE_URI).single().folder)
    }

    @Test
    fun `a cursor without an id column yields nothing instead of throwing`() {
        val cursor = cursorWith(MediaStore.Video.Media.TITLE)
        cursor.addRow(arrayOf<Any?>("Orphan"))

        assertTrue(MediaStoreVideoSource.readCursor(cursor, BASE_URI).isEmpty())
    }

    @Test
    fun `an empty cursor yields an empty library`() {
        assertTrue(MediaStoreVideoSource.readCursor(modernCursor(), BASE_URI).isEmpty())
    }

    @Test
    fun `every row is read, in cursor order`() {
        val cursor = modernCursor()
        cursor.addRow(arrayOf(1L, "A", "a.mp4", 1_000L, "Movies"))
        cursor.addRow(arrayOf(2L, "B", "b.mp4", 2_000L, "Movies"))
        cursor.addRow(arrayOf(3L, "C", "c.mp4", 3_000L, "Movies"))

        assertEquals(
            listOf(1L, 2L, 3L),
            MediaStoreVideoSource.readCursor(cursor, BASE_URI).map { it.id },
        )
    }

    @Test
    fun `folderOf prefers the bucket over the path`() {
        assertEquals(
            "Karaoke",
            MediaStoreVideoSource.folderOf("Karaoke", "/storage/emulated/0/Movies/clip.mp4"),
        )
    }

    @Test
    fun `folderOf copes with a bare file name and a trailing slash`() {
        assertEquals("", MediaStoreVideoSource.folderOf(null, "clip.mp4"))
        assertEquals("", MediaStoreVideoSource.folderOf(null, null))
        assertEquals("Movies", MediaStoreVideoSource.folderOf("", "/sdcard/Movies/clip.mp4"))
    }

    @Test
    fun `Android 13 and up asks for the video-only permission`() {
        assertEquals(
            Manifest.permission.READ_MEDIA_VIDEO,
            MediaStoreVideoSource.requiredPermission(Build.VERSION_CODES.TIRAMISU),
        )
        assertEquals(
            Manifest.permission.READ_MEDIA_VIDEO,
            MediaStoreVideoSource.requiredPermission(Build.VERSION_CODES.UPSIDE_DOWN_CAKE),
        )
    }

    @Test
    fun `older Android asks for the blanket storage permission`() {
        assertEquals(
            Manifest.permission.READ_EXTERNAL_STORAGE,
            MediaStoreVideoSource.requiredPermission(Build.VERSION_CODES.LOLLIPOP),
        )
        assertEquals(
            Manifest.permission.READ_EXTERNAL_STORAGE,
            MediaStoreVideoSource.requiredPermission(Build.VERSION_CODES.S_V2),
        )
    }
}
