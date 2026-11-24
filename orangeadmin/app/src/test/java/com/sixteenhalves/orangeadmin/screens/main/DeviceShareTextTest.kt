package com.sixteenhalves.orangeadmin.screens.main

import com.sixteenhalves.orangeadmin.repository.DeviceRegistration
import org.junit.Assert.assertEquals
import org.junit.Test

class DeviceShareTextTest {

    @Test
    fun shareTextUsesTheLabelledBlockPosSetupParses() {
        val device =
            DeviceRegistration(
                code = "ABC123",
                token = "dvc_example",
                deviceId = "device-uuid",
                branchId = "branch-uuid",
            )
        assertEquals(
            "Backend URL: https://y4p.onrender.com\n" +
                "Device token: dvc_example\n" +
                "Device id: device-uuid\n" +
                "Branch id: branch-uuid",
            device.shareText(),
        )
    }
}
