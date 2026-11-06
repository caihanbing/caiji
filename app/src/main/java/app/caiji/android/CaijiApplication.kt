package app.caiji.android

import android.app.Application
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.room.Room
import app.caiji.android.data.LedgerDatabase
import app.caiji.android.data.RoomLedgerRepository
import app.caiji.android.data.FeedbackDatabase
import app.caiji.android.data.FeedbackRepository
import app.caiji.android.data.SalaryDatabase
import app.caiji.android.data.RoomSalaryRepository
import app.caiji.android.update.GitHubReleaseSource
import app.caiji.android.update.ReleaseVersion
import app.caiji.android.update.UpdateManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class CaijiApplication : Application(), DefaultLifecycleObserver {
    private val updateScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    internal val updateManager: UpdateManager by lazy {
        UpdateManager(
            scope = updateScope,
            currentVersion = requireNotNull(ReleaseVersion.parse("v${BuildConfig.VERSION_NAME}")),
            source = GitHubReleaseSource(updateScope)
        )
    }

    override fun onCreate() {
        super<Application>.onCreate()
        ProcessLifecycleOwner.get().lifecycle.addObserver(this)
    }

    override fun onStart(owner: LifecycleOwner) {
        updateManager.check(manual = false)
    }

    val database: LedgerDatabase by lazy {
        Room.databaseBuilder(this, LedgerDatabase::class.java, "caiji-ledger.db").build()
    }

    val ledgerRepository: RoomLedgerRepository by lazy { RoomLedgerRepository(database) }
    val feedbackDatabase: FeedbackDatabase by lazy {
        Room.databaseBuilder(this, FeedbackDatabase::class.java, "caiji-feedback.db").build()
    }
    val salaryDatabase: SalaryDatabase by lazy {
        Room.databaseBuilder(this, SalaryDatabase::class.java, "caiji-salary.db")
            .addMigrations(SalaryDatabase.MIGRATION_1_2)
            .build()
    }
    val salaryRepository by lazy { RoomSalaryRepository(salaryDatabase) }
    val feedbackRepository: FeedbackRepository by lazy { FeedbackRepository(this, feedbackDatabase) }
}
