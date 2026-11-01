package app.caiji.android.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CategoryCatalogTest {
    @Test fun seedsApprovedExpenseAndIncomeCategoriesInOrder() {
        val seed = CategoryCatalog.defaults()
        assertEquals(
            listOf("餐饮", "购物", "交通", "住房", "日用", "娱乐", "医疗", "学习", "通讯", "人情", "其他"),
            seed.filter { it.type == EntryType.EXPENSE }.map { it.name }
        )
        assertEquals(listOf("工资", "奖金", "兼职", "理财", "其他"),
            seed.filter { it.type == EntryType.INCOME }.map { it.name })
    }

    @Test fun trimsCategoryNamesAndChecksActiveNamesWithinType() {
        assertEquals("餐饮", CategoryCatalog.normalizedName("  餐饮  "))
        assertTrue(CategoryCatalog.isDuplicate("餐饮", EntryType.EXPENSE, listOf(
            Category("one", EntryType.EXPENSE, " 餐饮 "),
            Category("two", EntryType.INCOME, "餐饮"),
            Category("three", EntryType.EXPENSE, "购物", active = false)
        )))
    }

    @Test fun exposesFortyEightUniqueAccessibleIconsAndRetainsEveryLegacyKey() {
        val icons = CategoryCatalog.iconKeys()

        assertEquals(48, icons.size)
        assertEquals(48, icons.map { it.key }.toSet().size)
        assertTrue(icons.all { it.label.isNotBlank() && it.group.isNotBlank() })
        assertTrue(listOf("restaurant", "shopping_bag", "train", "home", "daily", "movie", "medical", "school",
            "phone", "favorite", "salary", "gift", "work", "savings", "other").all(CategoryCatalog::isAllowedIcon))
        assertTrue(icons.all { CategoryCatalog.isAllowedIcon(it.key) })
    }
}
