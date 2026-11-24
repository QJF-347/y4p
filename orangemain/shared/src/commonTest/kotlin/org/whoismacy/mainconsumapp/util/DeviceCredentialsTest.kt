package org.whoismacy.mainconsumapp.util

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DeviceCredentialsTest {

    @Test
    fun parsesTheAdminShareBlock() {
        val credentials =
            parseDeviceCredentials(
                """
                Backend URL: https://y4p.onrender.com
                Device token: dvc_YIqqVyitSCgJN2RkVK3jmHgMGnkl9J-lcGf3wKkw0TE
                Device id: 01a20f1c-b2a5-716e-8064-771151394852
                Branch id: 01a20f06-a990-744a-8340-73d63096d5fc
                """.trimIndent(),
            )
        assertNotNull(credentials)
        assertEquals("https://y4p.onrender.com", credentials.baseUrl)
        assertEquals("dvc_YIqqVyitSCgJN2RkVK3jmHgMGnkl9J-lcGf3wKkw0TE", credentials.deviceToken)
        assertEquals("01a20f1c-b2a5-716e-8064-771151394852", credentials.deviceId)
        assertEquals("01a20f06-a990-744a-8340-73d63096d5fc", credentials.branchId)
        assertTrue(credentials.isComplete)
    }

    @Test
    fun acceptsAliasesEqualsSignsAndSemicolons() {
        val credentials =
            parseDeviceCredentials(
                "backend_url=https://staging.example.com; DeviceID = dev-1 ; token: tok-2; branch_id: br-3",
            )
        assertNotNull(credentials)
        assertEquals("https://staging.example.com", credentials.baseUrl)
        assertEquals("dev-1", credentials.deviceId)
        assertEquals("tok-2", credentials.deviceToken)
        assertEquals("br-3", credentials.branchId)
    }

    @Test
    fun acceptsSetupLinks() {
        val credentials =
            parseDeviceCredentials(
                "orange://device?base=https%3A%2F%2Fy4p.onrender.com&token=tok&device_id=dev&branch_id=br",
            )
        assertNotNull(credentials)
        assertEquals("https://y4p.onrender.com", credentials.baseUrl)
        assertEquals("tok", credentials.deviceToken)
        assertEquals("dev", credentials.deviceId)
        assertEquals("br", credentials.branchId)
    }

    @Test
    fun acceptsQueryStringWithoutScheme() {
        val credentials = parseDeviceCredentials("token=tok&device_id=dev&branch_id=br")
        assertNotNull(credentials)
        assertNull(credentials.baseUrl)
        assertEquals("tok", credentials.deviceToken)
    }

    @Test
    fun requiresTokenDeviceAndBranch() {
        assertNull(parseDeviceCredentials("Device token: tok"))
        assertNull(parseDeviceCredentials("Device id: dev\nBranch id: br"))
        assertNull(parseDeviceCredentials(""))
        assertNull(parseDeviceCredentials("hello there"))
    }

    @Test
    fun shareTextRoundTrips() {
        val original =
            DeviceCredentials(
                baseUrl = "https://y4p.onrender.com",
                deviceToken = "tok",
                deviceId = "dev",
                branchId = "br",
            )
        assertEquals(original, parseDeviceCredentials(original.toShareText()))
    }
}
