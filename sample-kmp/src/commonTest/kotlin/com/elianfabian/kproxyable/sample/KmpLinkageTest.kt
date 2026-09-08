package com.elianfabian.kproxyable.sample

import com.elianfabian.kproxyable.create
import kotlin.test.Test
import kotlin.test.assertEquals

class KmpLinkageTest {
    @Test
    fun testCommonServiceLinkage() {
        val service = KProxy.create<CommonService>(DemoHandler())
        val result = service.version
        assertEquals("1.0.0-DEMO", result)
    }

    @Test
    fun testLocalServiceLinkage() {
        val service = KProxy.create<KmpLocalService>(DemoHandler())
        val result = service.kmpSpecificAction("Test")
        assertEquals("Processed: Test", result)
    }
}
