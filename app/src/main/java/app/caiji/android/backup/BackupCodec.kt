package app.caiji.android.backup

import app.caiji.android.data.LedgerEntry
import app.caiji.android.data.LedgerRestoreSink
import app.caiji.android.data.LedgerLimits
import app.caiji.android.data.MonthTotals
import app.caiji.android.domain.Category
import app.caiji.android.domain.CategoryCatalog
import app.caiji.android.domain.EntryType
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.FilterInputStream
import java.io.FilterOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.math.BigInteger
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.security.GeneralSecurityException
import java.security.DigestInputStream
import java.security.MessageDigest
import java.security.SecureRandom
import java.time.LocalDate
import javax.crypto.AEADBadTagException
import javax.crypto.Cipher
import javax.crypto.CipherInputStream
import javax.crypto.CipherOutputStream
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

class BackupException(message: String, cause: Throwable? = null) : Exception(message, cause)

data class BackupSummary(
    val categoryCount: Int,
    val entryCount: Long,
    val expenseFen: BigInteger,
    val incomeFen: BigInteger,
    val contentDigest: String
)

data class BackupContents(val categories: List<Category>, val entries: List<LedgerEntry>) {
    val totals: MonthTotals get() = MonthTotals.from(entries)
    val expenseFen: BigInteger get() = totals.expenseFen
    val incomeFen: BigInteger get() = totals.incomeFen
}

/** Versioned authenticated backup with streaming file IO and bounded record fields. */
class BackupCodec(private val random: SecureRandom = SecureRandom()) {
    suspend fun export(password: String, categories: List<Category>, entries: List<LedgerEntry>): ByteArray =
        ByteArrayOutputStream().also { exportTo(password, categories, entries, it) }.toByteArray()

    suspend fun exportTo(password: String, categories: List<Category>, entries: List<LedgerEntry>, output: OutputStream) {
        exportTo(password, categories, entries.size.toLong(), output) { emit -> entries.forEach { emit(it) } }
    }

    suspend fun exportTo(
        password: String,
        categories: List<Category>,
        entryCount: Long,
        output: OutputStream,
        streamEntries: suspend (suspend (LedgerEntry) -> Unit) -> Unit
    ) {
        validatePassword(password)
        validateCategories(categories)
        if (entryCount < 0L) throw BackupException("账单数量无效")
        val salt = ByteArray(SALT_BYTES).also(random::nextBytes)
        val nonce = ByteArray(NONCE_BYTES).also(random::nextBytes)
        val header = encodeHeader(KDF_ITERATIONS, salt, nonce)
        try {
            output.write(header)
            output.flush()
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, deriveKey(password, salt), GCMParameterSpec(GCM_TAG_BITS, nonce))
            cipher.updateAAD(header)
            val encrypted = CipherOutputStream(NonClosingOutputStream(output), cipher)
            DataOutputStream(encrypted.buffered()).use { out -> writeContents(out, categories, entryCount, streamEntries) }
        } catch (error: BackupException) {
            throw error
        } catch (error: GeneralSecurityException) {
            throw BackupException("无法加密备份文件", error)
        } catch (error: IOException) {
            throw BackupException("备份数据编码失败", error)
        }
    }

    /** Compatibility helper for unit tests and small in-memory callers. */
    suspend fun import(file: ByteArray, password: String): BackupContents =
        decode(ByteArrayInputStream(file), password, sink = null, collectEntries = true).let {
            BackupContents(it.categories, it.entries)
        }

    /** Reads, authenticates, and validates a complete file without retaining its entries. */
    suspend fun preview(input: InputStream, password: String): BackupSummary =
        decode(input, password, sink = null, collectEntries = false).summary

    /** Streams validated records into a Room transaction. A late authentication failure rolls it back. */
    suspend fun restore(input: InputStream, password: String, sink: LedgerRestoreSink): BackupSummary =
        decode(input, password, sink = sink, collectEntries = false).summary

    private suspend fun decode(input: InputStream, password: String, sink: LedgerRestoreSink?, collectEntries: Boolean): Decoded {
        validatePassword(password)
        val header = ByteArray(HEADER_BYTES)
        val salt: ByteArray
        val nonce: ByteArray
        val iterations: Int
        try {
            DataInputStream(input).readFully(header)
            DataInputStream(ByteArrayInputStream(header)).use { headerInput ->
                val magic = ByteArray(MAGIC.size).also(headerInput::readFully)
                if (!magic.contentEquals(MAGIC)) throw BackupException("文件不是财记备份")
                if (headerInput.readInt() != FORMAT_VERSION) throw BackupException("不支持此备份文件版本")
                iterations = headerInput.readInt()
                if (iterations !in MIN_KDF_ITERATIONS..MAX_KDF_ITERATIONS) throw BackupException("备份参数无效")
                if (headerInput.readUnsignedByte() != SALT_BYTES || headerInput.readUnsignedByte() != NONCE_BYTES) {
                    throw BackupException("备份参数无效")
                }
                salt = ByteArray(SALT_BYTES).also(headerInput::readFully)
                nonce = ByteArray(NONCE_BYTES).also(headerInput::readFully)
            }
        } catch (error: BackupException) {
            throw error
        } catch (error: Exception) {
            throw BackupException("备份文件格式错误", error)
        }

        val cipher = try {
            Cipher.getInstance("AES/GCM/NoPadding").apply {
                init(Cipher.DECRYPT_MODE, deriveKey(password, salt, iterations), GCMParameterSpec(GCM_TAG_BITS, nonce))
                updateAAD(header)
            }
        } catch (error: GeneralSecurityException) {
            throw BackupException("无法解密备份文件", error)
        }

        return try {
            val decrypted = CipherInputStream(NonClosingInputStream(input), cipher)
            val digest = MessageDigest.getInstance("SHA-256")
            DataInputStream(DigestInputStream(decrypted.buffered(), digest).buffered()).use { payload ->
                val categoryCount = payload.readLong().boundedListCount("分类")
                if (categoryCount == 0) throw BackupException("备份缺少分类")
                val categories = ArrayList<Category>()
                repeat(categoryCount) {
                    categories += Category(
                        readString(payload), EntryType.valueOf(readString(payload)), readString(payload),
                        readString(payload), payload.readInt(), payload.readBoolean()
                    )
                }
                validateCategories(categories)
                sink?.replaceCategories(categories)
                val byId = categories.associateBy(Category::id)

                val entryCount = payload.readLong().boundedNonNegative("账单")
                if (collectEntries && entryCount > Int.MAX_VALUE) throw BackupException("内存导入仅支持有限大小的备份")
                val collectedEntries = if (collectEntries) ArrayList<LedgerEntry>() else null
                val batch = if (sink != null) ArrayList<LedgerEntry>(ENTRY_BATCH_SIZE) else null
                var expense = BigInteger.ZERO
                var income = BigInteger.ZERO
                var entryIndex = 0L
                while (entryIndex < entryCount) {
                    val entry = LedgerEntry(
                        id = readString(payload),
                        type = EntryType.valueOf(readString(payload)),
                        amountFen = payload.readLong(),
                        categoryId = readString(payload),
                        occurredOn = LocalDate.parse(readString(payload)),
                        note = if (payload.readBoolean()) readString(payload) else null,
                        createdAtMillis = payload.readLong(),
                        updatedAtMillis = payload.readLong()
                    )
                    validateEntry(entry, byId)
                    if (entry.type == EntryType.EXPENSE) expense += BigInteger.valueOf(entry.amountFen)
                    else income += BigInteger.valueOf(entry.amountFen)
                    collectedEntries?.add(entry)
                    batch?.add(entry)
                    if (batch != null && batch.size == ENTRY_BATCH_SIZE) {
                        sink?.appendEntries(batch.toList())
                        batch.clear()
                    }
                    entryIndex++
                }
                if (batch?.isNotEmpty() == true) sink?.appendEntries(batch)
                if (payload.read() != -1) throw BackupException("备份内容存在无效尾部数据")
                val contentDigest = digest.digest().joinToString("") { "%02x".format(it.toInt() and 0xff) }
                val summary = BackupSummary(categoryCount, entryCount, expense, income, contentDigest)
                Decoded(categories, collectedEntries.orEmpty(), summary)
            }
        } catch (error: BackupException) {
            throw error
        } catch (error: Exception) {
            if (error.hasBadTag()) throw BackupException("密码不正确，或备份文件已损坏", error)
            throw BackupException("备份内容格式错误", error)
        }
    }

    private suspend fun writeContents(
        out: DataOutputStream,
        categories: List<Category>,
        entryCount: Long,
        streamEntries: suspend (suspend (LedgerEntry) -> Unit) -> Unit
    ) {
        out.writeLong(categories.size.toLong())
        val categoriesById = categories.associateBy(Category::id)
        categories.sortedWith(compareBy<Category> { it.type.name }.thenBy { it.sortOrder }.thenBy { it.id }).forEach { category ->
            writeString(out, category.id)
            writeString(out, category.type.name)
            writeString(out, category.name)
            writeString(out, category.iconKey)
            out.writeInt(category.sortOrder)
            out.writeBoolean(category.active)
        }
        out.writeLong(entryCount)
        var written = 0L
        streamEntries { entry ->
            validateEntry(entry, categoriesById)
            writeString(out, entry.id)
            writeString(out, entry.type.name)
            out.writeLong(entry.amountFen)
            writeString(out, entry.categoryId)
            writeString(out, entry.occurredOn.toString())
            out.writeBoolean(entry.note != null)
            entry.note?.let { writeString(out, it) }
            out.writeLong(entry.createdAtMillis)
            out.writeLong(entry.updatedAtMillis)
            written++
        }
        if (written != entryCount) throw BackupException("账单在备份期间发生变化")
    }

    private fun encodeHeader(iterations: Int, salt: ByteArray, nonce: ByteArray): ByteArray =
        ByteArrayOutputStream(HEADER_BYTES).also { bytes ->
            DataOutputStream(bytes).use { output ->
                output.write(MAGIC)
                output.writeInt(FORMAT_VERSION)
                output.writeInt(iterations)
                output.writeByte(salt.size)
                output.writeByte(nonce.size)
                output.write(salt)
                output.write(nonce)
            }
        }.toByteArray()

    private fun validateCategories(categories: List<Category>) {
        if (categories.isEmpty() || categories.map { it.id }.any(String::isBlank) ||
            categories.map { it.id }.distinct().size != categories.size) throw BackupException("分类编号无效或重复")
        if (categories.any {
                it.name.isBlank() || it.name != it.name.trim() || it.name.length > LedgerLimits.MAX_TEXT_CHARS ||
                    !CategoryCatalog.isAllowedIcon(it.iconKey) || it.sortOrder < 0 ||
                    !isEncodableString(it.id) || !isEncodableString(it.name) || !isEncodableString(it.iconKey)
            }) throw BackupException("分类数据无效")
        val activeNames = categories.filter { it.active }.map { it.type to CategoryCatalog.normalizedName(it.name) }
        if (activeNames.distinct().size != activeNames.size) throw BackupException("启用分类名称重复")
    }

    private fun validateEntry(entry: LedgerEntry, categoriesById: Map<String, Category>) {
        if (entry.id.isBlank() || !isEncodableString(entry.id) || !isEncodableString(entry.categoryId) ||
            entry.amountFen <= 0L || entry.occurredOn.isAfter(LocalDate.now()) ||
            (entry.note?.length ?: 0) > LedgerLimits.MAX_TEXT_CHARS || entry.note?.let(::isEncodableString) == false) {
            throw BackupException("账单金额、日期或备注无效")
        }
        val category = categoriesById[entry.categoryId] ?: throw BackupException("账单引用了不存在的分类")
        if (category.type != entry.type) throw BackupException("账单和分类收支类型不匹配")
    }

    private fun writeString(out: DataOutputStream, value: String) {
        val bytes = value.toByteArray(Charsets.UTF_8)
        if (bytes.size > MAX_STRING_BYTES) throw BackupException("备份文本字段过长")
        out.writeInt(bytes.size)
        out.write(bytes)
    }

    private fun readString(input: DataInputStream): String {
        val size = input.readInt()
        if (size !in 0..MAX_STRING_BYTES) throw BackupException("备份文本字段长度无效")
        val bytes = ByteArray(size).also(input::readFully)
        return try {
            Charsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString()
        } catch (error: Exception) {
            throw BackupException("备份文本编码无效", error)
        }
    }

    private fun isEncodableString(value: String): Boolean = value.toByteArray(Charsets.UTF_8).size <= MAX_STRING_BYTES

    private fun validatePassword(password: String) {
        if (password.codePointCount(0, password.length) < 8) throw BackupException("备份密码至少需要 8 个字符")
        if (password.toByteArray(Charsets.UTF_8).size > MAX_PASSWORD_BYTES) throw BackupException("备份密码过长")
    }

    private fun deriveKey(password: String, salt: ByteArray, iterations: Int = KDF_ITERATIONS): SecretKeySpec {
        val chars = password.toCharArray()
        val spec = PBEKeySpec(chars, salt, iterations, KEY_BITS)
        chars.fill('\u0000')
        return try {
            SecretKeySpec(SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded, "AES")
        } catch (error: GeneralSecurityException) {
            throw BackupException("无法生成备份加密密钥", error)
        } finally {
            spec.clearPassword()
        }
    }

    private fun Long.boundedListCount(label: String): Int {
        if (this !in 0..Int.MAX_VALUE.toLong()) throw BackupException("${label}数量无效")
        return toInt()
    }

    private fun Long.boundedNonNegative(label: String): Long {
        if (this < 0L) throw BackupException("${label}数量无效")
        return this
    }

    private fun Throwable.hasBadTag(): Boolean = generateSequence(this) { it.cause }.any { it is AEADBadTagException }

    private data class Decoded(val categories: List<Category>, val entries: List<LedgerEntry>, val summary: BackupSummary)

    private class NonClosingInputStream(input: InputStream) : FilterInputStream(input) {
        override fun close() = Unit
    }

    private class NonClosingOutputStream(output: OutputStream) : FilterOutputStream(output) {
        override fun close() = flush()
    }

    companion object {
        private val MAGIC = "CAIJI".toByteArray(Charsets.US_ASCII)
        private const val FORMAT_VERSION = 2
        private const val KDF_ITERATIONS = 600_000
        private const val MIN_KDF_ITERATIONS = 600_000
        private const val MAX_KDF_ITERATIONS = 1_000_000
        private const val SALT_BYTES = 16
        private const val NONCE_BYTES = 12
        private const val KEY_BITS = 256
        private const val GCM_TAG_BITS = 128
        private const val HEADER_BYTES = 5 + 4 + 4 + 1 + 1 + SALT_BYTES + NONCE_BYTES
        private const val MAX_STRING_BYTES = LedgerLimits.MAX_TEXT_CHARS * 4
        private const val MAX_PASSWORD_BYTES = 4_096
        private const val ENTRY_BATCH_SIZE = 500
    }
}
