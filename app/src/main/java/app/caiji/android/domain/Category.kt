package app.caiji.android.domain

data class Category(
    val id: String,
    val type: EntryType,
    val name: String,
    val iconKey: String = "other",
    val sortOrder: Int = 0,
    val active: Boolean = true
)

object CategoryCatalog {
    private val expenses = listOf(
        "餐饮" to "restaurant", "购物" to "shopping_bag", "交通" to "train",
        "住房" to "home", "日用" to "daily", "娱乐" to "movie",
        "医疗" to "medical", "学习" to "school", "通讯" to "phone",
        "人情" to "favorite", "其他" to "other"
    )
    private val incomes = listOf(
        "工资" to "salary", "奖金" to "gift", "兼职" to "work",
        "理财" to "savings", "其他" to "other"
    )

    private val iconKeys = (expenses + incomes).map { it.second }.toSet()

    fun isAllowedIcon(iconKey: String): Boolean = iconKey in iconKeys

    fun defaults(): List<Category> =
        expenses.mapIndexed { index, (name, icon) -> Category("seed-expense-$index", EntryType.EXPENSE, name, icon, index) } +
            incomes.mapIndexed { index, (name, icon) -> Category("seed-income-$index", EntryType.INCOME, name, icon, index) }

    fun normalizedName(name: String): String = name.trim()

    fun isDuplicate(
        name: String,
        type: EntryType,
        existing: Iterable<Category>,
        exceptId: String? = null
    ): Boolean {
        val normalized = normalizedName(name)
        return normalized.isNotEmpty() && existing.any {
            it.id != exceptId && it.active && it.type == type && normalizedName(it.name) == normalized
        }
    }
}
