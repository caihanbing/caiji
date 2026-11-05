package app.caiji.android.update

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdateManagerTest {
    private fun release(tag: String) = GitHubRelease(
        tag, ReleaseVersion.parse(tag)!!, "更新说明", "https://github.com/caihanbing/caiji/releases/tag/$tag", null
    )

    @Test fun autoCheckSilentlyIgnoresCurrentVersionButManualCheckReportsLatest() = runTest {
        val scope = CoroutineScope(SupervisorJob() + StandardTestDispatcher(testScheduler))
        val manager = UpdateManager(this, ReleaseVersion.parse("v0.4.2")!!, GitHubReleaseSource(scope) { release("v0.4.2") })
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
        val manager = UpdateManager(this, ReleaseVersion.parse("v0.4.2")!!, GitHubReleaseSource(scope) { release("v0.5.0") })
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
        val manager = UpdateManager(this, ReleaseVersion.parse("v0.4.2")!!, GitHubReleaseSource(scope) { error("offline") })
        manager.check(manual = false)
        testScheduler.runCurrent()
        assertEquals(UpdateState.Idle, manager.state.value)
        manager.check(manual = true)
        testScheduler.runCurrent()
        assertEquals(UpdateState.Failed, manager.state.value)
        scope.coroutineContext[kotlinx.coroutines.Job]?.cancel()
    }
}
