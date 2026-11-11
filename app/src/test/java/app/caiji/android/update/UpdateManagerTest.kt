package app.caiji.android.update

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class UpdateManagerTest {
    private fun release(tag: String) = AppRelease(
        tag, ReleaseVersion.parse(tag)!!, "更新说明", "https://gitee.com/caihanbingo/caiji/releases/tag/$tag", null
    )

    @Test fun autoCheckSilentlyIgnoresCurrentVersionButManualCheckReportsLatest() = runTest {
        val scope = CoroutineScope(SupervisorJob() + StandardTestDispatcher(testScheduler))
        val manager = UpdateManager(this, ReleaseVersion.parse("v0.4.2")!!, ReleaseSource { release("v0.4.2") })
        manager.check(manual = false)
        testScheduler.runCurrent()
        assertEquals(UpdateState.Idle, manager.state.value)
        manager.check(manual = true)
        testScheduler.runCurrent()
        assertEquals(UpdateState.UpToDate, manager.state.value)
        scope.coroutineContext[kotlinx.coroutines.Job]?.cancel()
    }

    @Test fun dismissingAnUpdateSuppressesSameTagForAutoButManualCheckCanShowItAgain() = runTest {
        val scope = CoroutineScope(SupervisorJob() + StandardTestDispatcher(testScheduler))
        val manager = UpdateManager(this, ReleaseVersion.parse("v0.4.2")!!, ReleaseSource { release("v0.5.0") })
        manager.check(manual = false)
        testScheduler.runCurrent()
        assertTrue(manager.state.value is UpdateState.Available)
        manager.dismissAvailable()
        manager.check(manual = false)
        testScheduler.runCurrent()
        assertEquals(UpdateState.Idle, manager.state.value)
        manager.check(manual = true)
        testScheduler.runCurrent()
        assertTrue(manager.state.value is UpdateState.Available)
        scope.coroutineContext[kotlinx.coroutines.Job]?.cancel()
    }

    @Test fun failedAutomaticCheckStaysSilentAndManualFailureIsVisible() = runTest {
        val scope = CoroutineScope(SupervisorJob() + StandardTestDispatcher(testScheduler))
        val manager = UpdateManager(this, ReleaseVersion.parse("v0.4.2")!!, ReleaseSource { error("offline") })
        manager.check(manual = false)
        testScheduler.runCurrent()
        assertEquals(UpdateState.Idle, manager.state.value)
        manager.check(manual = true)
        testScheduler.runCurrent()
        assertEquals(UpdateState.Failed, manager.state.value)
        scope.coroutineContext[kotlinx.coroutines.Job]?.cancel()
    }

    @Test fun aValidGiteeReleaseAvoidsFallbackEvenWhenItIsNotNewer() = runTest {
        var fallbackCalls = 0
        val manager = UpdateManager(this, ReleaseVersion.parse("v0.5.10")!!,
            OrderedReleaseSource(ReleaseSource { release("v0.5.10") }, ReleaseSource { fallbackCalls++; release("v0.5.11") }))

        manager.check(manual = true)
        testScheduler.runCurrent()

        assertEquals(UpdateState.UpToDate, manager.state.value)
        assertEquals(0, fallbackCalls)
    }

    @Test fun failedGiteeCheckFallsBackToGithubRelease() = runTest {
        var fallbackCalls = 0
        val manager = UpdateManager(this, ReleaseVersion.parse("v0.5.10")!!,
            OrderedReleaseSource(ReleaseSource { error("Gitee unavailable") },
                ReleaseSource { fallbackCalls++; release("v0.5.11") }))

        manager.check(manual = true)
        testScheduler.runCurrent()

        assertTrue(manager.state.value is UpdateState.Available)
        assertEquals(1, fallbackCalls)
        assertEquals("v0.5.11", (manager.state.value as UpdateState.Available).release.tagName)
    }

    @Test fun cancellationFromGiteeDoesNotFallBackToGithub() = runTest {
        var fallbackCalls = 0
        val source = OrderedReleaseSource(ReleaseSource { throw kotlinx.coroutines.CancellationException("cancel") },
            ReleaseSource { fallbackCalls++; release("v0.5.11") })
        try {
            source.latest()
            fail("Expected cancellation")
        } catch (_: kotlinx.coroutines.CancellationException) { }
        assertEquals(0, fallbackCalls)
    }
}
