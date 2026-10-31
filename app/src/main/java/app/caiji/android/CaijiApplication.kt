package app.caiji.android

import android.app.Application
import androidx.room.Room
import app.caiji.android.data.LedgerDatabase
import app.caiji.android.data.RoomLedgerRepository

class CaijiApplication : Application() {
    val database: LedgerDatabase by lazy {
        Room.databaseBuilder(this, LedgerDatabase::class.java, "caiji-ledger.db").build()
    }

    val ledgerRepository: RoomLedgerRepository by lazy { RoomLedgerRepository(database) }
}
