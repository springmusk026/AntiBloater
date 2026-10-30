package com.spring.musk.antibloater.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ProfileTest {
    @Test fun readsVersion2() {
        val p = Profile.parse("""{"app":"AntiBloater","format":2,"packages":[{"name":"com.a.b","label":"B","action":"Disable"}]}""")!!
        assertEquals(Profile.Item("com.a.b", "B", Action.Disable), p.packages.single())
    }

    @Test fun readsVersion1() {
        val p = Profile.parse("""{"version":1,"device":"Pixel","apps":[{"pkg":"com.a.b","name":"B","at":1}]}""")!!
        assertEquals("Pixel", p.device)
        assertEquals(Profile.Item("com.a.b", "B", Action.Uninstall), p.packages.single())
    }

    @Test fun rejectsOtherFiles() {
        assertNull(Profile.parse("not json"))
        assertNull(Profile.parse("""{"packages":[]}"""))
    }

    @Test fun validatesNames() {
        assertEquals(true, Packages.isValidName("com.samsung.android.bixby.agent"))
        assertEquals(false, Packages.isValidName("com.a.b; reboot"))
        assertEquals(false, Packages.isValidName("android"))
    }

    @Test fun stripsLinks() {
        val e = Entry(description = "Google Search box (https://play.google.com/store/apps/details?id=x)\nRuns in the background.\nSee https://example.com for more.\n")
        assertEquals("Google Search box\nRuns in the background.\nSee for more.", e.readable)
    }
}
