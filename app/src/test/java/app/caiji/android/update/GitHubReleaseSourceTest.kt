package app.caiji.android.update

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class GitHubReleaseSourceTest {
    @Test fun concurrentRequestsShareTheSameReleaseFetch() = runTest {
        val gate = CompletableDeferred<AppRelease>()
        var requests = 0
        val sourceScope = CoroutineScope(SupervisorJob() + StandardTestDispatcher(testScheduler))
        val source = GitHubReleaseSource(sourceScope) {
            requests++
            gate.await()
        }
        val first = async { source.latest() }
        val second = async { source.latest() }
        testScheduler.runCurrent()
        assertEquals(1, requests)
        val release = AppRelease("v0.5.0", ReleaseVersion.parse("v0.5.0")!!, "Notes", "https://github.com/caihanbing/caiji/releases/tag/v0.5.0", null)
        gate.complete(release)
        assertSame(release, first.await())
        assertSame(release, second.await())
        sourceScope.cancel()
    }

    @Test fun aNewRequestCanRunAfterAnEarlierRequestFails() = runTest {
        var requests = 0
        val sourceScope = CoroutineScope(SupervisorJob() + StandardTestDispatcher(testScheduler))
        val release = AppRelease("v0.5.0", ReleaseVersion.parse("v0.5.0")!!, "", "https://github.com/caihanbing/caiji/releases/tag/v0.5.0", null)
        val source = GitHubReleaseSource(sourceScope) {
            if (++requests == 1) error("offline")
            release
        }
        runCatching { source.latest() }
        assertEquals(release, source.latest())
        assertEquals(2, requests)
        sourceScope.cancel()
    }
}
