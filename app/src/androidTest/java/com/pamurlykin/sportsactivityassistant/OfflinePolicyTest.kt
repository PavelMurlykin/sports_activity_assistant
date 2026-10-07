package com.pamurlykin.sportsactivityassistant

import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.xmlpull.v1.XmlPullParser

class OfflinePolicyTest {
    @Suppress("DEPRECATION")
    @Test
    fun installedAppHasNoNetworkPermissionsOrAutomaticBackup() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val info = context.packageManager.getPackageInfo(context.packageName, PackageManager.GET_PERMISSIONS)
        val forbidden = setOf(
            "android.permission.INTERNET",
            "android.permission.ACCESS_NETWORK_STATE",
            "android.permission.CHANGE_NETWORK_STATE",
            "android.permission.ACCESS_WIFI_STATE",
            "android.permission.CHANGE_WIFI_STATE",
            "android.permission.NEARBY_WIFI_DEVICES",
        )
        val unexpected = info.requestedPermissions.orEmpty().toSet().intersect(forbidden)
        assertTrue("Network permissions in installed APK: $unexpected", unexpected.isEmpty())
        val appInfo = context.applicationInfo
        assertFalse(appInfo.flags and ApplicationInfo.FLAG_ALLOW_BACKUP != 0)
    }

    @Test
    fun systemTransferRulesExcludeEveryAppStorageDomain() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val parser = context.resources.getXml(R.xml.data_extraction_rules)
        val excluded = mutableMapOf<String, MutableSet<String>>()
        var section: String? = null
        try {
            while (parser.eventType != XmlPullParser.END_DOCUMENT) {
                if (parser.eventType == XmlPullParser.START_TAG) {
                    when (parser.name) {
                        "cloud-backup", "device-transfer" -> {
                            section = parser.name
                            excluded.getOrPut(parser.name, ::mutableSetOf)
                        }
                        "exclude" -> {
                            assertEquals(".", parser.getAttributeValue(null, "path"))
                            excluded.getValue(requireNotNull(section)).add(parser.getAttributeValue(null, "domain"))
                        }
                        "include" -> error("Automatic backup must not include app data")
                    }
                } else if (parser.eventType == XmlPullParser.END_TAG && parser.name == section) {
                    section = null
                }
                parser.next()
            }
        } finally {
            parser.close()
        }
        val domains = setOf(
            "root", "file", "database", "sharedpref", "external",
            "device_root", "device_file", "device_database", "device_sharedpref",
        )
        assertEquals(setOf("cloud-backup", "device-transfer"), excluded.keys)
        excluded.values.forEach { assertEquals(domains, it) }
    }
}
