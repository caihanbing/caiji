package app.caiji.android.data

import androidx.room.*
import androidx.room.migration.Migration
import java.time.LocalDate
import java.util.UUID

@Entity(tableName = "salary_income_types", indices = [Index(value = ["name"], unique = true)])
data class SalaryIncomeTypeRow(
    @PrimaryKey val id: String,
    val name: String,
    val isCash: Boolean,
    val builtIn: Boolean,
    val sortOrder: Int,
    @ColumnInfo(defaultValue = "'other'") val iconKey: String = "other",
    @ColumnInfo(defaultValue = "1") val active: Boolean = true
)

@Entity(tableName = "salary_entries", indices = [Index(value = ["occurredOn", "createdAtMillis"]), Index(value = ["incomeTypeId"])],
    foreignKeys = [ForeignKey(entity = SalaryIncomeTypeRow::class, parentColumns = ["id"], childColumns = ["incomeTypeId"], onDelete = ForeignKey.RESTRICT)])
data class SalaryEntryRow(
    @PrimaryKey val id: String, val occurredOn: String, val incomeTypeId: String,
    val grossFen: Long, val unionFen: Long, val pensionFen: Long, val medicalFen: Long,
    val unemploymentFen: Long, val housingFen: Long, val annuityFen: Long, val taxFen: Long,
    val createdAtMillis: Long, val updatedAtMillis: Long
)

@Dao
interface SalaryDao {
    @Query("SELECT * FROM salary_income_types ORDER BY sortOrder, name COLLATE NOCASE") suspend fun incomeTypes(): List<SalaryIncomeTypeRow>
    @Query("SELECT * FROM salary_income_types WHERE id = :id LIMIT 1") suspend fun incomeType(id: String): SalaryIncomeTypeRow?
    @Insert(onConflict = OnConflictStrategy.ABORT) suspend fun insertIncomeType(type: SalaryIncomeTypeRow)
    @Insert(onConflict = OnConflictStrategy.ABORT) suspend fun insertIncomeTypes(types: List<SalaryIncomeTypeRow>)
    @Query("UPDATE salary_income_types SET name = :name, iconKey = :iconKey WHERE id = :id")
    suspend fun updateIncomeType(id: String, name: String, iconKey: String): Int
    @Query("UPDATE salary_income_types SET active = :active WHERE id = :id")
    suspend fun setIncomeTypeActive(id: String, active: Boolean): Int
    @Query("UPDATE salary_income_types SET name = :name, iconKey = :iconKey, active = :active, sortOrder = :sortOrder WHERE id = :id")
    suspend fun updateIncomeTypeDirectory(id: String, name: String, iconKey: String, active: Boolean, sortOrder: Int): Int
    @Query("UPDATE salary_income_types SET sortOrder = :sortOrder WHERE id = :id")
    suspend fun setIncomeTypeSortOrder(id: String, sortOrder: Int): Int
    @Query("SELECT e.*, t.isCash AS isCash FROM salary_entries e JOIN salary_income_types t ON e.incomeTypeId = t.id ORDER BY e.occurredOn DESC, e.createdAtMillis DESC, e.id DESC") suspend fun entries(): List<SalaryEntryJoinedRow>
    @Query("SELECT e.*, t.isCash AS isCash FROM salary_entries e JOIN salary_income_types t ON e.incomeTypeId = t.id WHERE e.id = :id LIMIT 1") suspend fun entry(id: String): SalaryEntryJoinedRow?
    @Insert(onConflict = OnConflictStrategy.ABORT) suspend fun insertEntry(entry: SalaryEntryRow)
    @Query("UPDATE salary_entries SET occurredOn=:occurredOn, incomeTypeId=:incomeTypeId, grossFen=:grossFen, unionFen=:unionFen, pensionFen=:pensionFen, medicalFen=:medicalFen, unemploymentFen=:unemploymentFen, housingFen=:housingFen, annuityFen=:annuityFen, taxFen=:taxFen, updatedAtMillis=:updatedAtMillis WHERE id=:id")
    suspend fun updateEntry(id: String, occurredOn: String, incomeTypeId: String, grossFen: Long, unionFen: Long, pensionFen: Long, medicalFen: Long, unemploymentFen: Long, housingFen: Long, annuityFen: Long, taxFen: Long, updatedAtMillis: Long): Int
    @Query("DELETE FROM salary_entries WHERE id=:id") suspend fun deleteEntry(id: String): Int
    @Query("SELECT COUNT(*) FROM salary_income_types") suspend fun incomeTypeCount(): Int
    @Query("SELECT COUNT(*) FROM salary_entries") suspend fun salaryEntryCount(): Int
}

data class SalaryEntryJoinedRow(
    val id: String, val occurredOn: String, val incomeTypeId: String, val grossFen: Long, val unionFen: Long,
    val pensionFen: Long, val medicalFen: Long, val unemploymentFen: Long, val housingFen: Long,
    val annuityFen: Long, val taxFen: Long, val createdAtMillis: Long, val updatedAtMillis: Long, val isCash: Boolean
)

@Database(entities = [SalaryIncomeTypeRow::class, SalaryEntryRow::class], version = 3, exportSchema = true)
abstract class SalaryDatabase : RoomDatabase() {
    abstract fun salaryDao(): SalaryDao

    companion object {
        @JvmField
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE salary_income_types ADD COLUMN active INTEGER NOT NULL DEFAULT 1")
            }
        }

        @JvmField
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE salary_income_types ADD COLUMN iconKey TEXT NOT NULL DEFAULT 'other'")
                SalaryIncomeTypes.defaults.forEach { type ->
                    db.execSQL(
                        "UPDATE salary_income_types SET iconKey = ? WHERE id = ?",
                        arrayOf(type.iconKey, type.id)
                    )
                }
            }
        }
    }

    @Transaction open suspend fun seedIncomeTypes() {
        if (salaryDao().incomeTypeCount() == 0) salaryDao().insertIncomeTypes(SalaryIncomeTypes.defaults.map { it.toRow() })
    }
}

object SalaryIncomeTypes {
    private val cashNames = setOf("工资（交行 到账时间）", "餐补（翼支付 到账时间）", "加班费（翼支付 到账时间）", "泰康养老（交行 到账时间）", "出差补贴（交行 到账时间）", "退税（中行 到账时间）")
    val defaults = listOf(
        "工资（交行 到账时间）", "餐补（翼支付 到账时间）", "加班费（翼支付 到账时间）", "美团积分（美团 到账时间）",
        "劳保积分（平台 发放时间）", "节假日积分（平台 发放时间）", "扶贫积分（平台 发放时间）", "清凉积分（平台 发放时间）",
        "竞赛积分（平台 发放时间）", "健步走积分（平台 发放时间）", "洗衣卡（小程序 到账时间）", "鲜范面包卡（小程序 领取时间）",
        "泰康养老（交行 到账时间）", "携程通（携程通 发放时间）", "电影卡（小程序 绑定时间）", "出差补贴（交行 到账时间）", "退税（中行 到账时间）"
    ).mapIndexed { index, name ->
        SalaryIncomeType("builtin-" + (index + 1), name, name in cashNames, true, index, defaultIconKey(index))
    }

    internal fun defaultIconKey(index: Int): String = when (index) {
        0 -> "salary"
        1 -> "restaurant"
        2 -> "work"
        in 3..9 -> "gift"
        10 -> "daily"
        11 -> "dessert"
        12 -> "insurance"
        13 -> "travel"
        14 -> "movie"
        15 -> "allowance"
        16 -> "refund"
        else -> "other"
    }

    fun defaultForName(name: String): SalaryIncomeType? = defaults.firstOrNull { it.name == name }
}

fun SalaryIncomeType.toRow() = SalaryIncomeTypeRow(id, name, isCash, builtIn, sortOrder, iconKey, active)
fun SalaryIncomeTypeRow.toDomain() = SalaryIncomeType(id, name, isCash, builtIn, sortOrder, iconKey, active)
fun SalaryEntryJoinedRow.toDomain() = SalaryEntry(id, LocalDate.parse(occurredOn), incomeTypeId, grossFen, unionFen, pensionFen, medicalFen, unemploymentFen, housingFen, annuityFen, taxFen, isCash, createdAtMillis, updatedAtMillis)
fun SalaryEntry.toRow() = SalaryEntryRow(id, occurredOn.toString(), incomeTypeId, grossFen, unionFen, pensionFen, medicalFen, unemploymentFen, housingFen, annuityFen, taxFen, createdAtMillis, updatedAtMillis)
