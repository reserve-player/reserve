package dev.reserve

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Locks the promises made by the manifest.
 *
 * "Offline" is only meaningful if it is enforced rather than intended, so this reads the
 * MERGED manifest — a dependency that quietly pulls in INTERNET fails here.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class ManifestTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    private fun requestedPermissions(): List<String> {
        val info = context.packageManager
            .getPackageInfo(context.packageName, PackageManager.GET_PERMISSIONS)
        return info.requestedPermissions?.toList().orEmpty()
    }

    @Test
    fun `the app cannot reach the network`() {
        val permissions = requestedPermissions()

        assertFalse(
            "INTERNET must never be merged into this app - it is offline by construction",
            permissions.contains(Manifest.permission.INTERNET),
        )
        assertFalse(
            permissions.contains(Manifest.permission.ACCESS_NETWORK_STATE),
        )
    }

    @Test
    fun `the app asks to read video on every supported Android version`() {
        val permissions = requestedPermissions()

        assertTrue(permissions.contains(Manifest.permission.READ_MEDIA_VIDEO))
        assertTrue(permissions.contains(Manifest.permission.READ_EXTERNAL_STORAGE))
    }
}
