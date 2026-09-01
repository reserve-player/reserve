package dev.reserve

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.FeatureInfo
import android.content.pm.PackageManager
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Locks the promises made by the manifest.
 *
 * "Offline" is only meaningful if it is enforced rather than intended, so these read the
 * MERGED manifest, not the source one - a dependency that quietly adds a network permission
 * fails here. media3-exoplayer does exactly that, which is why the app merges it back out.
 *
 * Android drops a `<uses-permission>` whose `maxSdkVersion` is below the running SDK, so the
 * storage permissions genuinely differ by version and have to be asserted per era.
 *
 * Where the app can be installed is a manifest promise too, so the Android TV declarations are
 * checked here rather than left to be discovered by a user with a TV box.
 */
@RunWith(RobolectricTestRunner::class)
class ManifestTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    private fun requestedPermissions(): List<String> {
        val info = context.packageManager
            .getPackageInfo(context.packageName, PackageManager.GET_PERMISSIONS)
        return info.requestedPermissions?.toList().orEmpty()
    }

    @Test
    @Config(sdk = [32, 33])
    fun `the merged manifest asks for nothing that could reach the network`() {
        val permissions = requestedPermissions()

        val networkPermissions = permissions.filter { permission ->
            NETWORK_WORDS.any { permission.contains(it, ignoreCase = true) }
        }

        assertEquals(
            "This app is offline by construction, so no network permission may survive the " +
                "manifest merge. Leaked: $networkPermissions. Full requested set: $permissions",
            emptyList<String>(),
            networkPermissions,
        )
    }

    @Test
    @Config(sdk = [32])
    fun `on Android 12 and older the app asks for storage access`() {
        val permissions = requestedPermissions()

        assertTrue(
            "Android 12 has no per-media permission, so the library is unreadable without " +
                "READ_EXTERNAL_STORAGE. Requested: $permissions",
            permissions.contains(Manifest.permission.READ_EXTERNAL_STORAGE),
        )
    }

    @Test
    @Config(sdk = [33])
    fun `on Android 13 and newer the app asks for video access only`() {
        val permissions = requestedPermissions()

        assertTrue(
            "Android 13 replaced storage access with per-media permissions. Requested: $permissions",
            permissions.contains(Manifest.permission.READ_MEDIA_VIDEO),
        )
        assertEquals(
            "READ_EXTERNAL_STORAGE is capped at SDK 32 so Android drops it here. Seeing it means " +
                "the cap was lost and the app is asking for the whole of shared storage. " +
                "Requested: $permissions",
            false,
            permissions.contains(Manifest.permission.READ_EXTERNAL_STORAGE),
        )
    }

    @Test
    @Config(sdk = [33])
    fun `the app is listed in the Android TV launcher`() {
        val leanback = Intent(Intent.ACTION_MAIN).addCategory(LEANBACK_LAUNCHER)

        val matches = context.packageManager.queryIntentActivities(leanback, 0)

        assertTrue(
            "Without a LEANBACK_LAUNCHER filter the app installs on a TV but never appears on " +
                "its home screen. Resolved: ${matches.map { it.activityInfo?.name }}",
            matches.any { it.activityInfo?.name == MainActivity::class.java.name },
        )
    }

    @Test
    @Config(sdk = [33])
    fun `no hardware is demanded that would block install on a TV or on a phone`() {
        val info = context.packageManager
            .getPackageInfo(context.packageName, PackageManager.GET_CONFIGURATIONS)
        val required = info.reqFeatures.orEmpty()
            .filter { it.flags and FeatureInfo.FLAG_REQUIRED != 0 }
            .mapNotNull { it.name }

        // Touchscreen is required by DEFAULT unless declared otherwise, which is the usual reason
        // a perfectly good player is not installable on a TV box.
        assertEquals(
            "Requiring hardware narrows where the app can be installed, and this app needs none " +
                "of it: a TV has no touchscreen and a phone has no D-pad. Required: $required",
            emptyList<String>(),
            required.filter { it == TOUCHSCREEN || it == LEANBACK },
        )
    }

    private companion object {
        /** Substrings rather than a fixed list, so a permission nobody predicted still trips this. */
        val NETWORK_WORDS = listOf("INTERNET", "NETWORK", "WIFI", "BLUETOOTH", "NFC")

        const val LEANBACK_LAUNCHER = "android.intent.category.LEANBACK_LAUNCHER"
        const val TOUCHSCREEN = "android.hardware.touchscreen"
        const val LEANBACK = "android.software.leanback"
    }
}
