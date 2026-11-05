package app.caiji.android.update

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class GitHubReleasePayloadTest {
    @Test fun parsesPublishedReleaseAndSelectsItsApk() {
        val release = parseReleasePayload("""{"tag_name":"v0.5.0","draft":false,"prerelease":false,"body":"修复问题","html_url":"https://github.com/caihanbing/caiji/releases/tag/v0.5.0","assets":[{"name":"caiji-0.5.0-release.apk","browser_download_url":"https://github.com/caihanbing/caiji/releases/download/v0.5.0/caiji-0.5.0-release.apk"}]}""")
        assertEquals("v0.5.0", release.tagName)
        assertEquals("修复问题", release.notes)
        assertEquals("https://github.com/caihanbing/caiji/releases/download/v0.5.0/caiji-0.5.0-release.apk", release.apkUrl)
    }

    @Test fun absentApkFallsBackToReleasePageAndDraftsAreRejected() {
        val base = """{"tag_name":"v0.5.0","draft":false,"prerelease":false,"body":"","html_url":"https://github.com/caihanbing/caiji/releases/tag/v0.5.0","assets":[]}"""
        assertNull(parseReleasePayload(base).apkUrl)
        try {
            parseReleasePayload(base.replace("\"draft\":false", "\"draft\":true"))
            fail("expected draft release to be rejected")
        } catch (_: java.io.IOException) { }
    }

    @Test fun rejectsReleaseLinksOutsideGithubHttps() {
        val payload = """{"tag_name":"v0.5.0","html_url":"https://example.com/release"}"""
        try {
            parseReleasePayload(payload)
            fail("expected invalid release URL to be rejected")
        } catch (_: java.io.IOException) { }
    }
}
