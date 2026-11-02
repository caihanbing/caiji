package app.caiji.android

import android.app.Application
import androidx.room.Room
import app.caiji.android.data.LedgerDatabase
import app.caiji.android.data.RoomLedgerRepository
import app.caiji.android.data.FeedbackDatabase
import app.caiji.android.data.FeedbackRepository

class CaijiApplication : Application() {
    val database: LedgerDatabase by lazy {
        Room.databaseBuilder(this, LedgerDatabase::class.java, "caiji-ledger.db").build()
    }

    val ledgerRepository: RoomLedgerRepository by lazy { RoomLedgerRepository(database) }
    val feedbackDatabase: FeedbackDatabase by lazy {
        Room.databaseBuilder(this, FeedbackDatabase::class.java, "caiji-feedback.db").build()
    }
    val feedbackRepository: FeedbackRepository by lazy { FeedbackRepository(this, feedbackDatabase) }
}
