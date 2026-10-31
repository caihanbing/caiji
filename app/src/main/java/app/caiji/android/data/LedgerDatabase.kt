package app.caiji.android.data

import android.database.Cursor
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase
import androidx.room.Transaction

@Entity(tableName = "categories", indices = [Index(value = ["type", "sortOrder"]), Index(value = ["name"])])
data class CategoryRow(
    @PrimaryKey val id: String,
    val type: String,
    val name: String,
    val iconKey: String,
    val sortOrder: Int,
    val active: Boolean
)

@Entity(tableName = "entries", indices = [Index(value = ["occurredOn", "createdAtMillis"]), Index(value = ["categoryId"])],
    foreignKeys = [ForeignKey(entity = CategoryRow::class, parentColumns = ["id"], childColumns = ["categoryId"], onDelete = ForeignKey.RESTRICT)])
data class EntryRow(
    @PrimaryKey val id: String,
    val type: String,
    val amountFen: Long,
    val categoryId: String,
    val occurredOn: String,
    val note: String?,
    val createdAtMillis: Long,
    val updatedAtMillis: Long
)

@Dao
interface LedgerDao {
    @Query("SELECT * FROM categories ORDER BY type ASC, sortOrder ASC, name COLLATE NOCASE ASC")
    suspend fun categories(): List<CategoryRow>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertCategories(categories: List<CategoryRow>)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertCategory(category: CategoryRow)

    @Query("UPDATE categories SET name = :name, iconKey = :iconKey WHERE id = :id")
    suspend fun updateCategory(id: String, name: String, iconKey: String): Int

    @Query("UPDATE categories SET active = :active WHERE id = :id")
    suspend fun setCategoryActive(id: String, active: Boolean): Int

    @Query("UPDATE categories SET sortOrder = :sortOrder WHERE id = :id")
    suspend fun setCategoryOrder(id: String, sortOrder: Int): Int

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertEntry(entry: EntryRow)

    @Query("UPDATE entries SET type = :type, amountFen = :amountFen, categoryId = :categoryId, occurredOn = :occurredOn, note = :note, updatedAtMillis = :updatedAtMillis WHERE id = :id")
    suspend fun updateEntry(id: String, type: String, amountFen: Long, categoryId: String, occurredOn: String, note: String?, updatedAtMillis: Long): Int

    @Query("DELETE FROM entries WHERE id = :id")
    suspend fun deleteEntry(id: String): Int

    @Query("SELECT * FROM entries WHERE occurredOn >= :startDate AND occurredOn < :endDate ORDER BY occurredOn DESC, createdAtMillis DESC, id DESC")
    suspend fun entriesBetween(startDate: String, endDate: String): List<EntryRow>

    @Query("SELECT * FROM entries ORDER BY occurredOn DESC, createdAtMillis DESC, id DESC")
    suspend fun allEntries(): List<EntryRow>

    @Query("SELECT * FROM entries WHERE id = :id LIMIT 1")
    suspend fun entryById(id: String): EntryRow?

    @Query("SELECT * FROM entries ORDER BY occurredOn DESC, createdAtMillis DESC, id DESC")
    fun entriesCursor(): Cursor

    @Query("SELECT COUNT(*) FROM entries")
    suspend fun entryCount(): Long

    @Query("SELECT COUNT(*) FROM categories")
    suspend fun categoryCount(): Int

    @Query("SELECT * FROM categories WHERE id = :id LIMIT 1")
    suspend fun categoryById(id: String): CategoryRow?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertEntries(entries: List<EntryRow>)

    @Query("DELETE FROM entries")
    suspend fun clearEntries()

    @Query("DELETE FROM categories")
    suspend fun clearCategories()
}

@Database(entities = [CategoryRow::class, EntryRow::class], version = 1, exportSchema = true)
abstract class LedgerDatabase : RoomDatabase() {
    abstract fun ledgerDao(): LedgerDao

    @Transaction
    open suspend fun seedCategories() {
        ledgerDao().insertCategories(app.caiji.android.domain.CategoryCatalog.defaults().map {
            CategoryRow(it.id, it.type.name, it.name, it.iconKey, it.sortOrder, it.active)
        })
    }

}
