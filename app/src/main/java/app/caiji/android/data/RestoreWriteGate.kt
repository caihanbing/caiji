package app.caiji.android.data

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Serializes ledger mutations and freezes them between restore confirmation and completion. */
internal class RestoreWriteGate {
    private val mutex = Mutex()
    private var restoreActive = false

    suspend fun <T> withUserWrite(block: suspend () -> T): T = mutex.withLock {
        check(!restoreActive) { "正在恢复账本，暂不能修改" }
        block()
    }

    suspend fun beginRestoreSession() = mutex.withLock {
        check(!restoreActive) { "已有恢复操作正在进行" }
        restoreActive = true
    }

    suspend fun <T> withRestoreWrite(block: suspend () -> T): T = mutex.withLock {
        check(restoreActive) { "没有进行中的恢复操作" }
        block()
    }

    suspend fun endRestoreSession() = mutex.withLock {
        restoreActive = false
    }
}
