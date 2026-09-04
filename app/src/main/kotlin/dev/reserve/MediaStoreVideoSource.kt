package dev.reserve

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.database.Cursor
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.provider.Settings
import androidx.core.content.ContextCompat
import dev.reserve.logic.VideoItem

/** Reads the device's video library out of MediaStore. */
object MediaStoreVideoSource {

    /**
     * Read by name rather than through the MediaStore constants on purpose: the bucket column
     * only became public API for video at API 29, and `_data` is deprecated. Asking the cursor
     * what it actually has keeps this working across all three permission eras.
     */
    private const val COLUMN_BUCKET = "bucket_display_name"
    private const val COLUMN_DATA = "_data"

    /** Granted on Android 14+ when the user shares only some videos instead of all of them. */
    private const val PERMISSION_PARTIAL = "android.permission.READ_MEDIA_VISUAL_USER_SELECTED"

    private const val PREFS_ACCESS = "reserve_access"
    private const val KEY_REQUESTED = "video_access_requested"

    /** `MediaStore.VOLUME_EXTERNAL` by value — the constant itself is API 29+. */
    private const val VOLUME_EXTERNAL = "external"

    private val BASE_PROJECTION = arrayOf(
        MediaStore.Video.Media._ID,
        MediaStore.Video.Media.TITLE,
        MediaStore.Video.Media.DISPLAY_NAME,
        MediaStore.Video.Media.DURATION,
    )

    /** Android 13 split the storage permissions; below that there is only the blanket one. */
    fun requiredPermission(sdkInt: Int = Build.VERSION.SDK_INT): String =
        if (sdkInt >= Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.READ_MEDIA_VIDEO
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }

    fun hasPermission(context: Context): Boolean = isGranted(context, requiredPermission())

    /**
     * True when Android 14+ granted access to a hand-picked subset. The library then works
     * normally, just over fewer videos, so this is a hint to the user rather than an error.
     */
    fun hasPartialAccess(context: Context): Boolean =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE &&
            !hasPermission(context) &&
            isGranted(context, PERMISSION_PARTIAL)

    fun canReadVideos(context: Context): Boolean =
        hasPermission(context) || hasPartialAccess(context)

    /** Records that the system dialog has been shown at least once. */
    fun markRequested(context: Context) {
        prefs(context).edit().putBoolean(KEY_REQUESTED, true).apply()
    }

    fun wasRequested(context: Context): Boolean =
        prefs(context).getBoolean(KEY_REQUESTED, false)

    /**
     * True when the OS will no longer show the permission dialog, so the grant button would do
     * nothing and the only way back in is the system settings screen.
     *
     * Android cannot report this directly: `shouldShowRequestPermissionRationale` is false both
     * before the first ask and after a permanent denial. [wasRequested] is what separates the
     * two, which is why the request is recorded when it is launched.
     */
    fun isPermanentlyDenied(context: Context, showRationale: Boolean): Boolean =
        !canReadVideos(context) && wasRequested(context) && !showRationale

    /** The system settings page for this app, where a denied permission can be granted again. */
    fun appSettingsIntent(packageName: String): Intent =
        Intent(
            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
            Uri.fromParts("package", packageName, null),
        )

    /**
     * Blocking; call it off the main thread.
     *
     * Reads TWO collections and merges them. The video collection alone misses anything the
     * system filed elsewhere — a downloaded file belongs to the downloads collection and simply
     * is not in the video one, which is why videos in `Download/` were invisible while the same
     * files worked once moved to `Movies/`.
     */
    fun query(context: Context): List<VideoItem> =
        mergeById(queryVideoCollection(context), queryAllVideoFiles(context))

    /**
     * Merges the two collections, deduped by id, the video collection winning any clash.
     *
     * Split out from [query] for the same reason as [readCursor]: the rule is testable without a
     * real content provider. Dedupe by id is exact rather than a heuristic — the video collection
     * is a view over the files table, so one file carries one `_ID` in both.
     */
    internal fun mergeById(
        fromVideo: List<VideoItem>,
        fromFiles: List<VideoItem>,
    ): List<VideoItem> {
        if (fromFiles.isEmpty()) return fromVideo
        // No Map.putIfAbsent here: it is an API 24 default method and minSdk is 21, so it would
        // throw NoSuchMethodError on exactly the old TV boxes this fix is aimed at.
        val merged = LinkedHashMap<Long, VideoItem>(fromVideo.size + fromFiles.size)
        fromVideo.forEach { merged[it.id] = it }
        fromFiles.forEach { if (!merged.containsKey(it.id)) merged[it.id] = it }
        return merged.values.sortedBy { it.title.lowercase() }
    }

    private fun queryVideoCollection(context: Context): List<VideoItem> {
        val collection = MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        val cursor = context.contentResolver.query(
            collection,
            BASE_PROJECTION + folderColumn(),
            null,
            null,
            "${MediaStore.Video.Media.TITLE} COLLATE NOCASE ASC",
        ) ?: return emptyList()
        return cursor.use { readCursor(it, collection.toString()) }
    }

    /**
     * Every file the system has classified as video, wherever it lives.
     *
     * Deliberately failure-isolated: this is a SUPPLEMENT to the video collection, so an OEM
     * build that rejects the query must add nothing rather than take the whole scan down with
     * it. Losing videos a user can see today would be worse than not finding new ones.
     */
    private fun queryAllVideoFiles(context: Context): List<VideoItem> = try {
        val collection = MediaStore.Files.getContentUri(VOLUME_EXTERNAL)
        val cursor = context.contentResolver.query(
            collection,
            BASE_PROJECTION + folderColumn(),
            "${MediaStore.Files.FileColumns.MEDIA_TYPE} = ?",
            arrayOf(MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO.toString()),
            null,
        )
        cursor?.use { readCursor(it, collection.toString()) } ?: emptyList()
    } catch (_: RuntimeException) {
        // SecurityException / IllegalArgumentException / SQLiteException all land here.
        emptyList()
    }

    /** The bucket column only became public API for video at API 29; below that derive it. */
    private fun folderColumn(): String =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) COLUMN_BUCKET else COLUMN_DATA

    /**
     * Maps a MediaStore cursor to items. Split out from [query] so the mapping — including the
     * missing-column and damaged-file paths — is testable without a real content provider.
     */
    fun readCursor(cursor: Cursor, baseUri: String): List<VideoItem> {
        val idColumn = cursor.getColumnIndex(MediaStore.Video.Media._ID)
        if (idColumn < 0) return emptyList()
        val titleColumn = cursor.getColumnIndex(MediaStore.Video.Media.TITLE)
        val nameColumn = cursor.getColumnIndex(MediaStore.Video.Media.DISPLAY_NAME)
        val durationColumn = cursor.getColumnIndex(MediaStore.Video.Media.DURATION)
        val bucketColumn = cursor.getColumnIndex(COLUMN_BUCKET)
        val dataColumn = cursor.getColumnIndex(COLUMN_DATA)

        val items = mutableListOf<VideoItem>()
        while (cursor.moveToNext()) {
            val id = cursor.getLong(idColumn)
            items += VideoItem(
                id = id,
                title = cursor.stringOrNull(titleColumn)
                    ?: cursor.stringOrNull(nameColumn)
                    ?: "Video $id",
                uri = "$baseUri/$id",
                durationMs = cursor.longOrZero(durationColumn),
                folder = folderOf(cursor.stringOrNull(bucketColumn), cursor.stringOrNull(dataColumn)),
            )
        }
        return items
    }

    /** Falls back to the parent directory name where the bucket column is unavailable. */
    internal fun folderOf(bucket: String?, path: String?): String {
        if (!bucket.isNullOrBlank()) return bucket
        if (path.isNullOrBlank()) return ""
        return path.trimEnd('/').substringBeforeLast('/', "").substringAfterLast('/')
    }

    private fun isGranted(context: Context, permission: String): Boolean =
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS_ACCESS, Context.MODE_PRIVATE)

    private fun Cursor.stringOrNull(column: Int): String? =
        if (column < 0 || isNull(column)) null else getString(column)?.takeIf { it.isNotBlank() }

    private fun Cursor.longOrZero(column: Int): Long =
        if (column < 0 || isNull(column)) 0L else getLong(column)
}
