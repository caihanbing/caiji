package app.caiji.android.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.caiji.android.domain.Category
import app.caiji.android.domain.EntryType
import app.caiji.android.ui.theme.CaijiPalette

@Composable
internal fun CategoryManagerScreen(
    categories: List<Category>,
    onBack: () -> Unit,
    onSave: (EntryType, String?, String, String, (Boolean) -> Unit) -> Unit,
    onActive: (String, Boolean) -> Unit,
    onMove: (String, Int) -> Unit,
    initialType: EntryType = EntryType.EXPENSE,
    errorMessage: String? = null
) {
    var selectedType by remember(initialType) { mutableStateOf(initialType) }
    var edit by remember { mutableStateOf<Category?>(null) }
    var add by remember { mutableStateOf(false) }
    val typeCategories = categories.filter { it.type == selectedType }.sortedBy { it.sortOrder }

    Column(Modifier.fillMaxSize().background(CaijiPalette.Background)) {
        Row(
            Modifier.fillMaxWidth().padding(start = 8.dp, end = 12.dp, top = 6.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(onClick = onBack, modifier = Modifier.testTag("category-back")) {
                CaijiIcon(CaijiIconName.BACK, "返回")
            }
            Text(
                "分类管理",
                modifier = Modifier.weight(1f),
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold
            )
            TextButton(onClick = { add = true; edit = null }, modifier = Modifier.testTag("category-add")) {
                CaijiIcon(CaijiIconName.ADD, "新增分类", Modifier.size(18.dp))
                Text("添加", modifier = Modifier.padding(start = 4.dp))
            }
        }

        TypeSelector(selectedType = selectedType, tagPrefix = "category-type") { selectedType = it }
        Surface(
            color = CaijiPalette.PrimarySoft,
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            Text(
                "停用的分类仍会保留在历史账单中。",
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 11.dp),
                color = CaijiPalette.SecondaryText,
                style = MaterialTheme.typography.bodySmall
            )
        }

        if (typeCategories.isEmpty()) {
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Text("还没有分类，点击右上角添加", color = CaijiPalette.SecondaryText)
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f).fillMaxWidth().padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(typeCategories, key = { it.id }) { category ->
                    CategoryRow(
                        category = category,
                        isFirst = category.sortOrder == typeCategories.minOfOrNull { it.sortOrder },
                        onEdit = { edit = category },
                        onMove = onMove,
                        onActive = onActive
                    )
                }
            }
        }
    }

    if (add || edit != null) {
        CategoryEditorDialog(
            existing = edit,
            defaultType = selectedType,
            errorMessage = errorMessage,
            onDismiss = { add = false; edit = null },
            onSave = { type, id, name, icon, done ->
                onSave(type, id, name, icon) { saved ->
                    done(saved)
                    if (saved) { add = false; edit = null }
                }
            }
        )
    }
}

@Composable
private fun CategoryRow(
    category: Category,
    isFirst: Boolean,
    onEdit: () -> Unit,
    onMove: (String, Int) -> Unit,
    onActive: (String, Boolean) -> Unit
) {
    Surface(color = CaijiPalette.Surface, shape = RoundedCornerShape(15.dp), modifier = Modifier.fillMaxWidth()) {
        Row(
            Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Surface(
                color = CaijiPalette.SurfaceAlt,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.size(42.dp)
            ) {
                androidx.compose.foundation.layout.Box(
                    Modifier.fillMaxSize().clickable(onClick = onEdit).testTag("category-edit-${category.id}"),
                    contentAlignment = Alignment.Center
                ) { Text(categoryEmoji(category.iconKey), style = MaterialTheme.typography.titleMedium) }
            }
            Column(
                Modifier.weight(1f).clickable(onClick = onEdit),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    category.name,
                    modifier = Modifier.testTag("category-name-${category.id}"),
                    color = if (category.active) CaijiPalette.Ink else CaijiPalette.SecondaryText,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                if (!category.active) Text("已停用", color = CaijiPalette.SecondaryText, style = MaterialTheme.typography.labelSmall)
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                TextButton(
                    onClick = { onMove(category.id, category.sortOrder - 1) },
                    enabled = !isFirst,
                    modifier = Modifier.size(width = 36.dp, height = 30.dp)
                        .testTag("category-move-up-${category.id}")
                        .semantics { contentDescription = "上移分类 ${category.name}" }
                ) { Text("↑", color = CaijiPalette.Ink, style = MaterialTheme.typography.titleMedium) }
                TextButton(
                    onClick = { onMove(category.id, category.sortOrder + 1) },
                    modifier = Modifier.size(width = 36.dp, height = 30.dp)
                        .testTag("category-move-down-${category.id}")
                        .semantics { contentDescription = "下移分类 ${category.name}" }
                ) { Text("↓", color = CaijiPalette.Ink, style = MaterialTheme.typography.titleMedium) }
            }
            Switch(
                checked = category.active,
                onCheckedChange = { onActive(category.id, it) },
                modifier = Modifier.testTag("category-active-name-${category.name}")
                    .semantics { contentDescription = "启用分类 ${category.name}" }
            )
        }
    }
}

@Composable
private fun CategoryEditorDialog(
    existing: Category?,
    defaultType: EntryType,
    errorMessage: String?,
    onDismiss: () -> Unit,
    onSave: (EntryType, String?, String, String, (Boolean) -> Unit) -> Unit
) {
    var type by remember(existing?.id) { mutableStateOf(existing?.type ?: defaultType) }
    var name by remember(existing?.id) { mutableStateOf(existing?.name.orEmpty()) }
    var icon by remember(existing?.id) { mutableStateOf(existing?.iconKey ?: "other") }
    var saving by remember(existing?.id) { mutableStateOf(false) }
    var saveFailed by remember(existing?.id) { mutableStateOf(false) }
    val icons = listOf("restaurant", "shopping_bag", "train", "home", "daily", "movie", "medical", "school", "phone", "favorite", "salary", "gift", "work", "savings", "other")

    androidx.compose.material3.AlertDialog(
        onDismissRequest = { if (!saving) onDismiss() },
        title = { Text(if (existing == null) "添加分类" else "编辑分类") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
                if (existing == null) TypeSelector(type, "category-editor-type") { type = it; saveFailed = false }
                else Text(
                    if (type == EntryType.EXPENSE) "支出分类" else "收入分类",
                    modifier = Modifier.testTag("category-editor-type-fixed"),
                    color = CaijiPalette.SecondaryText,
                    style = MaterialTheme.typography.bodySmall
                )
                androidx.compose.material3.OutlinedTextField(
                    value = name,
                    onValueChange = { name = it; saveFailed = false },
                    label = { Text("分类名称") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("category-name")
                )
                Text("选择图标", color = CaijiPalette.SecondaryText, style = MaterialTheme.typography.labelMedium)
                androidx.compose.foundation.lazy.grid.LazyVerticalGrid(
                    columns = androidx.compose.foundation.lazy.grid.GridCells.Fixed(5),
                    modifier = Modifier.height(160.dp).testTag("category-icon-grid")
                ) {
                    gridItems(icons) { key ->
                        val selected = icon == key
                        Surface(
                            color = if (selected) CaijiPalette.PrimarySoft else CaijiPalette.SurfaceAlt,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.padding(3.dp).size(44.dp)
                        ) {
                            Box(Modifier.fillMaxSize().clickable { icon = key; saveFailed = false }.testTag("category-icon-$key"), contentAlignment = Alignment.Center) {
                                Text(categoryEmoji(key), style = MaterialTheme.typography.titleMedium)
                            }
                        }
                    }
                }
                if (saveFailed) Text(
                    errorMessage ?: "分类名称重复或保存失败，请检查后重试。",
                    color = CaijiPalette.Danger,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.testTag("category-save-error")
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (!saving) {
                        saving = true
                        saveFailed = false
                        onSave(type, existing?.id, name.trim(), icon) { saved ->
                            saving = false
                            saveFailed = !saved
                        }
                    }
                },
                enabled = name.trim().isNotEmpty() && !saving,
                colors = ButtonDefaults.buttonColors(containerColor = CaijiPalette.Primary, contentColor = CaijiPalette.OnPrimary),
                modifier = Modifier.testTag("category-editor-save")
            ) { Text(if (saving) "保存中" else "保存") }
        },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !saving) { Text("取消") } }
    )
}

@Composable
private fun TypeSelector(selectedType: EntryType, tagPrefix: String, onSelected: (EntryType) -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp).background(CaijiPalette.SurfaceAlt, RoundedCornerShape(12.dp)).padding(3.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        listOf(EntryType.EXPENSE to "支出", EntryType.INCOME to "收入").forEach { (type, label) ->
            val selected = selectedType == type
            Surface(
                color = if (selected) CaijiPalette.Primary else CaijiPalette.Surface,
                contentColor = if (selected) CaijiPalette.OnPrimary else CaijiPalette.SecondaryText,
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.weight(1f).height(42.dp).selectable(
                    selected = selected,
                    role = Role.Tab,
                    onClick = { onSelected(type) }
                ).testTag("$tagPrefix-${type.name}")
            ) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(label, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal)
                }
            }
        }
    }
}
