package app.caiji.android.data

import androidx.room.Room
import androidx.core.content.FileProvider
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import android.content.Context
import java.util.UUID
import android.net.Uri
import java.io.File
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class FeedbackRepositoryTest {
    @Test fun observedScreenshotsAreOrderedByTheirSavedPosition() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val database = Room.inMemoryDatabaseBuilder(context, FeedbackDatabase::class.java).build()
        val repository = FeedbackRepository(context, database)
        try {
            val now = System.currentTimeMillis()
            val item = FeedbackItemRow("ordered-images", "截图顺序", "按选择顺序分享", now, now)
            database.feedbackDao().put(item)
            database.feedbackDao().putScreenshots(
                listOf(
                    FeedbackScreenshotRow(item.id, 2, "feedback/third.jpg"),
                    FeedbackScreenshotRow(item.id, 0, "feedback/first.jpg"),
                    FeedbackScreenshotRow(item.id, 1, "feedback/second.jpg")
                )
            )

            assertEquals(listOf(0, 1, 2), repository.observeAll().first().single().screenshots.map { it.position })
        } finally {
            database.close()
        }
    }

    @Test fun feedbackRecordsPersistAcrossDatabaseReopenOutsideTheLedgerDatabase() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val databaseName = "feedback-test-${UUID.randomUUID()}.db"
        try {
            var database = Room.databaseBuilder(context, FeedbackDatabase::class.java, databaseName).build()
            val repository = FeedbackRepository(context, database)
            val now = System.currentTimeMillis()
            val item = FeedbackItemRow("feedback-test", "拖动分类", "长按后难以准确放置", now, now)
            repository.save(item, emptyList(), "feedback-test-draft")
            assertEquals(item, database.feedbackDao().get(item.id))
            database.close()

            database = Room.databaseBuilder(context, FeedbackDatabase::class.java, databaseName).build()
            assertEquals(item, database.feedbackDao().observeAll().first().single().item)
            database.feedbackDao().delete(item.id)
            assertEquals(0, database.feedbackDao().observeAll().first().size)
            database.close()
        } finally {
            context.deleteDatabase(databaseName)
        }
    }

    @Test fun selectedScreenshotIsCopiedPrivatelyAndDeletedWithItsFeedbackItem() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val databaseName = "feedback-image-test-${UUID.randomUUID()}.db"
        val source = File(context.cacheDir, "source-${UUID.randomUUID()}.jpg")
        try {
            source.writeBytes(byteArrayOf(0x01, 0x02, 0x03))
            val database = Room.databaseBuilder(context, FeedbackDatabase::class.java, databaseName).build()
            val repository = FeedbackRepository(context, database)
            val id = UUID.randomUUID().toString()
            val draftId = repository.newDraftId()
            val staged = repository.copySelectedImages(draftId, listOf(Uri.fromFile(source)), 0)
            assertEquals(1, staged.size)
            val now = System.currentTimeMillis()
            repository.save(FeedbackItemRow(id, "截图", "本机截图应受私有目录管理", now, now), staged, draftId)

            val record = database.feedbackDao().observeAll().first().single()
            val storedFile = repository.resolveScreenshot(record.screenshots.single().relativePath)
            assertTrue(storedFile.isFile)
            assertTrue(storedFile.canonicalPath.startsWith(File(context.filesDir, "feedback").canonicalPath))
            val sharedUri = FileProvider.getUriForFile(context, "${context.packageName}.feedback-files", storedFile)
            assertEquals("content", sharedUri.scheme)
            assertEquals(byteArrayOf(1, 2, 3).toList(), context.contentResolver.openInputStream(sharedUri)!!.use { it.readBytes().toList() })

            repository.delete(id, record.screenshots)
            assertFalse(storedFile.exists())
            database.close()
        } finally {
            source.delete()
            context.deleteDatabase(databaseName)
            File(context.filesDir, "feedback").deleteRecursively()
            File(context.cacheDir, "feedback-drafts").deleteRecursively()
        }
    }

    @Test fun unavailableImageUriDoesNotCreateAFeedbackRecordOrAStagedFile() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val databaseName = "feedback-invalid-image-${UUID.randomUUID()}.db"
        val database = Room.databaseBuilder(context, FeedbackDatabase::class.java, databaseName).build()
        val repository = FeedbackRepository(context, database)
        val draftId = repository.newDraftId()
        try {
            val result = runCatching {
                repository.copySelectedImages(draftId, listOf(Uri.parse("content://missing-provider/image")), 0)
            }
            assertTrue(result.isFailure)
            assertEquals(0, database.feedbackDao().observeAll().first().size)
            assertEquals(0, File(context.cacheDir, "feedback-drafts/$draftId").listFiles()?.size ?: 0)
        } finally {
            database.close()
            context.deleteDatabase(databaseName)
            repository.discardDraft(draftId)
        }
    }
}
