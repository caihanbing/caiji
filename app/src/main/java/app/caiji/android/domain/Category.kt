package app.caiji.android.domain

data class Category(
    val id: String,
    val type: EntryType,
    val name: String,
    val iconKey: String = "other",
    val sortOrder: Int = 0,
    val active: Boolean = true
)

data class CategoryIcon(val key: String, val label: String, val group: String)

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

    private val icons = listOf(
        CategoryIcon("restaurant", "餐饮", "饮食"), CategoryIcon("coffee", "咖啡", "饮食"),
        CategoryIcon("tea", "茶饮", "饮食"), CategoryIcon("fruit", "水果", "饮食"),
        CategoryIcon("dessert", "甜品", "饮食"), CategoryIcon("grocery", "生鲜", "饮食"),
        CategoryIcon("shopping_bag", "购物", "生活"), CategoryIcon("clothes", "服饰", "生活"),
        CategoryIcon("beauty", "美妆", "生活"), CategoryIcon("haircut", "理发", "生活"),
        CategoryIcon("daily", "日用", "生活"), CategoryIcon("favorite", "人情", "生活"),
        CategoryIcon("medical", "医疗", "生活"), CategoryIcon("school", "教育", "生活"),
        CategoryIcon("phone", "通讯", "生活"), CategoryIcon("digital", "数码", "生活"),
        CategoryIcon("insurance", "保险", "生活"),
        CategoryIcon("tax", "税费", "生活"), CategoryIcon("refund", "报销", "生活"),
        CategoryIcon("train", "公共交通", "出行"), CategoryIcon("bus", "公交", "出行"),
        CategoryIcon("taxi", "打车", "出行"), CategoryIcon("car", "汽车", "出行"),
        CategoryIcon("fuel", "加油", "出行"), CategoryIcon("parking", "停车", "出行"),
        CategoryIcon("bicycle", "骑行", "出行"), CategoryIcon("flight", "航空", "出行"),
        CategoryIcon("travel", "旅行", "出行"), CategoryIcon("hotel", "住宿", "居家"),
        CategoryIcon("home", "住房", "居家"), CategoryIcon("utilities", "水电燃气", "居家"),
        CategoryIcon("repair", "维修", "居家"), CategoryIcon("furniture", "家具", "居家"),
        CategoryIcon("movie", "影音娱乐", "休闲"), CategoryIcon("game", "游戏", "休闲"),
        CategoryIcon("music", "音乐", "休闲"), CategoryIcon("fitness", "健身", "休闲"),
        CategoryIcon("pet", "宠物", "家庭"), CategoryIcon("baby", "育儿", "家庭"),
        CategoryIcon("family", "家庭", "家庭"), CategoryIcon("other", "其他", "常用"),
        CategoryIcon("salary", "工资", "收入"), CategoryIcon("gift", "奖金", "收入"),
        CategoryIcon("work", "兼职", "收入"), CategoryIcon("savings", "理财", "收入"),
        CategoryIcon("rent_income", "房租", "收入"), CategoryIcon("interest", "利息", "收入"),
        CategoryIcon("allowance", "津贴", "收入")
    )
    private val iconKeys = icons.map { it.key }.toSet()

    fun iconKeys(): List<CategoryIcon> = icons

    fun iconLabel(iconKey: String): String = icons.firstOrNull { it.key == iconKey }?.label ?: "其他"

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
