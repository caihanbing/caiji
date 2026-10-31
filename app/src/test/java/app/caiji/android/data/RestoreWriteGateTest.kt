package app.caiji.android.data

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class RestoreWriteGateTest {
    @Test fun restoreWaitsForAnInFlightWriteThenRejectsNewWritesUntilItEnds() = runBlocking {
        val gate = RestoreWriteGate()
        val writeStarted = CompletableDeferred<Unit>()
        val finishWrite = CompletableDeferred<Unit>()
        val write = launch {
            gate.withUserWrite {
                writeStarted.complete(Unit)
                finishWrite.await()
            }
        }
        writeStarted.await()

        val restore = async { gate.beginRestoreSession() }
        yield()
        assertFalse(restore.isCompleted)
        finishWrite.complete(Unit)
        write.join()
        restore.await()

        assertThrows(IllegalStateException::class.java) {
            runBlocking { gate.withUserWrite { "must not write" } }
        }
        assertEquals("restore", gate.withRestoreWrite { "restore" })
        gate.endRestoreSession()
        assertEquals("user", gate.withUserWrite { "user" })
        assertTrue(restore.isCompleted)
    }
}
