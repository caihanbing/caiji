package app.caiji.android.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReleaseVersionTest {
    @Test fun comparesNumericComponentsInsteadOfLexicographicStrings() {
        assertTrue(ReleaseVersion.parse("v0.4.10")!! > ReleaseVersion.parse("v0.4.9")!!)
        assertTrue(ReleaseVersion.parse("v1.0.0")!! > ReleaseVersion.parse("v0.99.99")!!)
        assertFalse(ReleaseVersion.parse("v0.4.2")!! > ReleaseVersion.parse("v0.4.2")!!)
    }

    @Test fun rejectsTagsThatDoNotMatchTheReleaseVersionContract() {
        listOf("0.5.0", "v1.2", "v1.2.3-beta", "v01.2.3", "v1.2.3.4", "v1.x.3")
            .forEach { assertEquals(null, ReleaseVersion.parse(it)) }
    }
}
