package app.caiji.android.update

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

internal sealed interface UpdateState {
    data object Idle : UpdateState
    data object Checking : UpdateState
    data object UpToDate : UpdateState
    data class Available(val release: GitHubRelease) : UpdateState
    data object Failed : UpdateState
}

internal class UpdateManager(
    private val scope: CoroutineScope,
    private val currentVersion: ReleaseVersion,
    private val source: GitHubReleaseSource
) {
    private val _state = MutableStateFlow<UpdateState>(UpdateState.Idle)
    val state: StateFlow<UpdateState> = _state.asStateFlow()
    private var dismissedTag: String? = null

    fun check(manual: Boolean) {
        if (manual) _state.value = UpdateState.Checking
        scope.launch { checkResult(manual) }
    }

    private suspend fun checkResult(manual: Boolean) {
        val latest = try {
            Result.success(source.latest())
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            Result.failure(error)
        }
        latest.fold(
            onSuccess = { release ->
                _state.value = when {
                    release.version <= currentVersion -> if (manual) UpdateState.UpToDate else UpdateState.Idle
                    !manual && dismissedTag == release.tagName -> UpdateState.Idle
                    else -> UpdateState.Available(release)
                }
            },
            onFailure = { _state.value = if (manual) UpdateState.Failed else UpdateState.Idle }
        )
    }

    fun dismissAvailable() {
        val current = _state.value as? UpdateState.Available ?: return
        dismissedTag = current.release.tagName
        _state.value = UpdateState.Idle
    }

    fun downloadFailed() {
        if (_state.value is UpdateState.Available) _state.value = UpdateState.Failed
    }
}
