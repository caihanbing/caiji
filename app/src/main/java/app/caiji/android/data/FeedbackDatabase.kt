package app.caiji.android.data

import android.content.Context
import android.net.Uri
import android.webkit.MimeTypeMap
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Relation
import androidx.room.RoomDatabase
import androidx.room.Transaction
import androidx.room.withTransaction
import java.io.File
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

@Entity(tableName = "feedback_items", indices = [Index(value = ["updatedAtMillis"])])
data class FeedbackItemRow(
    @PrimaryKey val id: String,
    val title: String,
    val description: String,
    val createdAtMillis: Long,
    val updatedAtMillis: Long
)

@Entity(
    tableName = "feedback_screenshots",
    primaryKeys = ["feedbackId", "position"],
    indices = [Index(value = ["feedbackId"])],
    foreignKeys = [ForeignKey(
        entity = FeedbackItemRow::class,
        parentColumns = ["id"],
        childColumns = ["feedbackId"],
        onDelete = ForeignKey.CASCADE
    )]
)
data class FeedbackScreenshotRow(val feedbackId: String, val position: Int, val relativePath: String)

data class FeedbackItemWithScreenshots(
    @Embedded val item: FeedbackItemRow,
    @Relation(parentColumn = "id", entityColumn = "feedbackId")
    val screenshots: List<FeedbackScreenshotRow>
)

@Dao
interface FeedbackDao {
    @Transaction
    @Query("SELECT * FROM feedback_items ORDER BY updatedAtMillis DESC, id DESC")
    fun observeAll(): Flow<List<FeedbackItemWithScreenshots>>

    @Query("SELECT * FROM feedback_items WHERE id = :id LIMIT 1")
    suspend fun get(id: String): FeedbackItemRow?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun put(item: FeedbackItemRow)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun putScreenshots(images: List<FeedbackScreenshotRow>)

    @Query("DELETE FROM feedback_screenshots WHERE feedbackId = :id")
    suspend fun clearScreenshots(id: String)

    @Query("SELECT * FROM feedback_screenshots WHERE feedbackId = :id ORDER BY position ASC")
    suspend fun screenshots(id: String): List<FeedbackScreenshotRow>

    @Query("DELETE FROM feedback_items WHERE id = :id")
    suspend fun delete(id: String)
}

@Database(entities = [FeedbackItemRow::class, FeedbackScreenshotRow::class], version = 1, exportSchema = false)
abstract class FeedbackDatabase : RoomDatabase() {
    abstract fun feedbackDao(): FeedbackDao
}

class FeedbackRepository(private val context: Context, private val database: FeedbackDatabase) {
    private val root get() = File(context.filesDir, "feedback").apply { mkdirs() }
    private val draftRoot get() = File(context.cacheDir, "feedback-drafts").apply { mkdirs() }

    fun observeAll(): Flow<List<FeedbackItemWithScreenshots>> = database.feedbackDao().observeAll().map { records ->
        records.map { record -> record.copy(screenshots = record.screenshots.sortedBy { it.position }) }
    }

    fun newDraftId(): String = UUID.randomUUID().toString()

    suspend fun copySelectedImages(draftId: String, uris: List<Uri>, existingCount: Int): List<String> =
        withContext(Dispatchers.IO) {
            require(existingCount + uris.size <= MAX_SCREENSHOTS) { "最多添加 $MAX_SCREENSHOTS 张截图" }
            val draft = File(draftRoot, draftId).apply { mkdirs() }
            uris.map { uri ->
                val mime = context.contentResolver.getType(uri)
                val extension = MimeTypeMap.getSingleton().getExtensionFromMimeType(mime)?.takeIf { it.matches(Regex("[A-Za-z0-9]{1,8}")) } ?: "jpg"
                    val name = "${UUID.randomUUID()}.$extension"
                val destination = File(draft, name)
                try {
                    require(mime == null || mime.startsWith("image/")) { "只能添加图片截图" }
                    val copied = context.contentResolver.openInputStream(uri)?.use { input ->
                        destination.outputStream().use { output -> input.copyTo(output) }
                    } ?: throw IllegalArgumentException("无法读取所选图片")
                    require(copied > 0L) { "截图文件为空" }
                    "pending:$draftId/$name"
                } catch (error: Exception) {
                    destination.delete()
                    throw error
                }
            }
        }

    suspend fun save(item: FeedbackItemRow, imageRefs: List<String>, draftId: String) = withContext(Dispatchers.IO) {
        require(item.title.isNotBlank() && item.description.isNotBlank())
        require(imageRefs.size <= MAX_SCREENSHOTS)
        val draftFolder = File(draftRoot, draftId)
        val copied = mutableListOf<File>()
        val finalRefs = try {
            imageRefs.mapIndexed { index, ref ->
                if (ref.startsWith("pending:")) {
                    val stagedPath = ref.removePrefix("pending:")
                    require(stagedPath.startsWith("$draftId/")) { "截图草稿不匹配" }
                    val source = File(draftFolder, stagedPath.substringAfter('/'))
                    require(source.isFile) { "截图已失效，请重新添加" }
                    val target = File(root, "${UUID.randomUUID()}.img")
                    source.inputStream().use { input -> target.outputStream().use { output -> input.copyTo(output) } }
                    copied += target
                    FeedbackScreenshotRow(item.id, index, "feedback/${target.name}")
                } else {
                    FeedbackScreenshotRow(item.id, index, ref)
                }
            }
        } catch (error: Exception) {
            copied.forEach(File::delete)
            throw error
        }
        val previous = database.feedbackDao().screenshots(item.id)
        try {
            database.withTransaction {
                database.feedbackDao().put(item)
                database.feedbackDao().clearScreenshots(item.id)
                if (finalRefs.isNotEmpty()) database.feedbackDao().putScreenshots(finalRefs)
            }
        } catch (error: Exception) {
            copied.forEach(File::delete)
            throw error
        }
        val retained = finalRefs.map { it.relativePath }.toSet()
        previous.filterNot { it.relativePath in retained }.forEach { File(context.filesDir, it.relativePath).delete() }
        draftFolder.deleteRecursively()
    }

    suspend fun delete(id: String, images: List<FeedbackScreenshotRow>) = withContext(Dispatchers.IO) {
        database.feedbackDao().delete(id)
        images.forEach { File(context.filesDir, it.relativePath).delete() }
        File(draftRoot, id).deleteRecursively()
    }

    suspend fun discardDraft(draftId: String) = withContext(Dispatchers.IO) {
        File(draftRoot, draftId).deleteRecursively()
    }

    fun resolveScreenshot(relativePath: String): File = File(context.filesDir, relativePath)

    companion object { const val MAX_SCREENSHOTS = 5 }
}
