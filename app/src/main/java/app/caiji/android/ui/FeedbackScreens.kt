package app.caiji.android.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import app.caiji.android.data.FeedbackItemRow
import app.caiji.android.data.FeedbackItemWithScreenshots
import app.caiji.android.data.FeedbackRepository
import app.caiji.android.data.FeedbackScreenshotRow
import app.caiji.android.ui.theme.CaijiPalette
import app.caiji.android.ui.theme.CaijiThemeId
import java.io.File
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
internal fun FeedbackListScreen(
    records: List<FeedbackItemWithScreenshots>,
    onNew: () -> Unit,
    onEdit: (FeedbackItemWithScreenshots) -> Unit,
    onShare: (FeedbackItemWithScreenshots) -> Unit,
    onDelete: (FeedbackItemWithScreenshots) -> Unit,
    repository: FeedbackRepository,
    onBack: () -> Unit
) {
    Column(Modifier.fillMaxSize().background(CaijiPalette.Background)) {
        CaijiPageTopBar(
            title = "问题反馈", onBack = onBack, backTag = "feedback-list-back", actionTag = "feedback-new",
            action = { TextButton(onClick = onNew) { Text("＋ 新建", color = CaijiPalette.Primary) } }
        )
        Text("只保存在本机，可分享给开发者。", Modifier.padding(horizontal = 20.dp, vertical = 4.dp), color = CaijiPalette.SecondaryText,
            style = MaterialTheme.typography.bodySmall)
        if (records.isEmpty()) {
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("还没有问题记录", color = CaijiPalette.Ink, style = MaterialTheme.typography.titleMedium)
                    Text("记录使用中的问题或优化建议", color = CaijiPalette.SecondaryText, style = MaterialTheme.typography.bodyMedium)
                    TextButton(onClick = onNew) { Text("新建问题", color = CaijiPalette.Primary) }
                }
            }
        } else LazyColumn(Modifier.weight(1f).fillMaxWidth(), contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
            items(records, key = { it.item.id }) { record ->
                Card(colors = CardDefaults.cardColors(containerColor = CaijiPalette.Surface), shape = RoundedCornerShape(18.dp),
                    modifier = Modifier.fillMaxWidth().testTag("feedback-card-${record.item.id}").clickable { onEdit(record) }) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(record.item.title, color = CaijiPalette.Ink, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        Text(record.item.description, color = CaijiPalette.SecondaryText, style = MaterialTheme.typography.bodySmall,
                            maxLines = 3, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                        if (record.screenshots.isNotEmpty()) Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            record.screenshots.take(3).forEach { image ->
                                FeedbackThumbnail(repository.resolveScreenshot(image.relativePath), Modifier.size(76.dp, 58.dp))
                            }
                            if (record.screenshots.size > 3) Box(Modifier.size(76.dp, 58.dp).background(CaijiPalette.SurfaceAlt, RoundedCornerShape(10.dp)),
                                contentAlignment = Alignment.Center) { Text("+${record.screenshots.size - 3}", color = CaijiPalette.SecondaryText) }
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(formatFeedbackTime(record.item.updatedAtMillis), Modifier.weight(1f), color = CaijiPalette.SecondaryText,
                                style = MaterialTheme.typography.labelSmall)
                            TextButton(onClick = { onShare(record) }, modifier = Modifier.testTag("feedback-share-${record.item.id}")) { Text("分享") }
                            TextButton(onClick = { onEdit(record) }) { Text("编辑") }
                            TextButton(onClick = { onDelete(record) }) { Text("删除", color = CaijiPalette.Danger) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun FeedbackEditorScreen(
    repository: FeedbackRepository,
    existing: FeedbackItemWithScreenshots?,
    draftId: String,
    onCancel: () -> Unit,
    onSave: (String, String, List<String>, (Boolean, String?) -> Unit) -> Unit
) {
    var title by rememberSaveable(draftId) { mutableStateOf(existing?.item?.title.orEmpty()) }
    var description by rememberSaveable(draftId) { mutableStateOf(existing?.item?.description.orEmpty()) }
    var imageRefs by rememberSaveable(draftId) { mutableStateOf(existing?.screenshots?.sortedBy { it.position }?.map { it.relativePath }.orEmpty()) }
    var saving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var deleteImagePath by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    BackHandler(enabled = saving) { }
    BackHandler(enabled = !saving, onBack = onCancel)

    val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(FeedbackRepository.MAX_SCREENSHOTS)) { uris ->
        if (uris.isNotEmpty()) scope.launch {
            runCatching { repository.copySelectedImages(draftId, uris, imageRefs.size) }
                .onSuccess { imageRefs = imageRefs + it; error = null }
                .onFailure { error = it.message ?: "无法添加截图" }
        }
    }
    deleteImagePath?.let { path -> AlertDialog(
        onDismissRequest = { deleteImagePath = null }, title = { Text("移除这张截图？") },
        confirmButton = { TextButton(onClick = { imageRefs = imageRefs - path; deleteImagePath = null }) { Text("移除", color = CaijiPalette.Danger) } },
        dismissButton = { TextButton(onClick = { deleteImagePath = null }) { Text("取消") } }
    ) }
    Column(Modifier.fillMaxSize().background(CaijiPalette.Background)) {
        CaijiPageTopBar(
            title = if (existing == null) "新建反馈" else "编辑反馈",
            onBack = onCancel,
            backTag = "feedback-editor-cancel",
            actionTag = "feedback-save-slot",
            backEnabled = !saving,
            action = {
                TextButton(onClick = {
                if (title.isBlank() || description.isBlank()) { error = "请填写标题和问题描述"; return@TextButton }
                saving = true
                onSave(title.trim(), description.trim(), imageRefs) { saved, message ->
                    saving = false
                    error = if (saved) null else message ?: "保存失败，请重试"
                }
                }, enabled = !saving && title.isNotBlank() && description.isNotBlank(), modifier = Modifier.testTag("feedback-save")) {
                    Text(if (saving) "保存中" else "保存", color = CaijiPalette.Primary)
                }
            }
        )
        Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedTextField(title, { title = it; error = null }, Modifier.fillMaxWidth().testTag("feedback-title"),
                label = { Text("标题") }, singleLine = true, keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next))
            OutlinedTextField(description, { description = it; error = null }, Modifier.fillMaxWidth().height(150.dp).testTag("feedback-description"),
                label = { Text("问题描述") }, minLines = 4, keyboardOptions = KeyboardOptions(imeAction = ImeAction.Default))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("截图（选填）", Modifier.weight(1f), style = MaterialTheme.typography.titleSmall, color = CaijiPalette.Ink)
                Text("${imageRefs.size}/${FeedbackRepository.MAX_SCREENSHOTS}", color = CaijiPalette.SecondaryText, style = MaterialTheme.typography.labelSmall)
            }
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).testTag("feedback-image-strip"),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                imageRefs.forEachIndexed { index, ref ->
                    val file = runCatching { repository.resolveEditorImage(draftId, ref) }
                        .getOrElse { File(context.cacheDir, "missing-feedback-image") }
                    Box {
                        FeedbackThumbnail(file, Modifier.size(92.dp, 72.dp).testTag("feedback-preview-$index"))
                        Text("×", Modifier.align(Alignment.TopEnd).clickable(enabled = !saving) { deleteImagePath = ref }
                            .background(CaijiPalette.Surface, RoundedCornerShape(20.dp)).padding(horizontal = 6.dp), color = CaijiPalette.Danger)
                    }
                }
                if (imageRefs.size < FeedbackRepository.MAX_SCREENSHOTS) {
                    Surface(Modifier.size(92.dp, 72.dp).clickable(enabled = !saving) {
                        imagePicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                    }.testTag("feedback-add-image"), color = CaijiPalette.SurfaceAlt, shape = RoundedCornerShape(10.dp)) {
                        Box(contentAlignment = Alignment.Center) { Text("＋\n添加截图", color = CaijiPalette.SecondaryText, textAlign = androidx.compose.ui.text.style.TextAlign.Center) }
                    }
                }
            }
            Text("图片仅保存在本机，分享时才会发送给你选择的应用。", color = CaijiPalette.SecondaryText, style = MaterialTheme.typography.bodySmall)
            error?.let { Text(it, color = CaijiPalette.Danger, style = MaterialTheme.typography.bodySmall) }
            Spacer(Modifier.height(12.dp))
        }
    }
}

@Composable
internal fun ThemePickerScreen(selected: CaijiThemeId, onSelect: (CaijiThemeId) -> Unit, onBack: () -> Unit) {
    Column(Modifier.fillMaxSize().background(CaijiPalette.Background)) {
        CaijiPageTopBar(title = "换肤", onBack = onBack, backTag = "theme-back", actionTag = "theme-no-action")
        PeriodSummaryCard("本月结余", app.caiji.android.data.LedgerPeriodTotals(java.math.BigInteger.valueOf(13_800), java.math.BigInteger.valueOf(66_600)))
        Text("选择后立即应用", Modifier.padding(horizontal = 20.dp, vertical = 4.dp), color = CaijiPalette.SecondaryText,
            style = MaterialTheme.typography.bodySmall)
        LazyColumn(Modifier.weight(1f), contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(CaijiPalette.options, key = { it.key }) { option ->
                val chosen = option == selected
                Surface(color = CaijiPalette.Surface, shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth().clickable { onSelect(option) }.testTag("theme-${option.key}")) {
                    Row(Modifier.padding(horizontal = 14.dp, vertical = 13.dp), verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Surface(Modifier.size(34.dp), color = CaijiPalette.preview(option).first(), shape = RoundedCornerShape(10.dp)) {}
                        Text(option.label, Modifier.weight(1f), color = CaijiPalette.Ink, fontWeight = if (chosen) FontWeight.SemiBold else FontWeight.Normal)
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            CaijiPalette.preview(option).forEach { color -> Surface(Modifier.size(14.dp), color = color, shape = RoundedCornerShape(8.dp)) {} }
                        }
                        Text(if (chosen) "✓" else "", Modifier.width(20.dp), color = CaijiPalette.Primary)
                    }
                }
            }
        }
    }
}

@Composable
private fun FeedbackThumbnail(file: File, modifier: Modifier = Modifier) {
    val bitmap by produceState<Bitmap?>(initialValue = null, file.absolutePath) {
        value = withContext(Dispatchers.IO) {
            if (!file.isFile) null else {
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeFile(file.absolutePath, bounds)
                var sample = 1
                while (bounds.outWidth / sample > 360 || bounds.outHeight / sample > 360) sample *= 2
                BitmapFactory.decodeFile(file.absolutePath, BitmapFactory.Options().apply { inSampleSize = sample })
            }
        }
    }
    Surface(modifier, color = CaijiPalette.SurfaceAlt, shape = RoundedCornerShape(10.dp)) {
        if (bitmap != null) Image(bitmap!!.asImageBitmap(), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        else Box(contentAlignment = Alignment.Center) { Text("图片不可用", color = CaijiPalette.SecondaryText, style = MaterialTheme.typography.labelSmall) }
    }
}

private fun formatFeedbackTime(value: Long): String = DateTimeFormatter.ofPattern("yyyy年M月d日 HH:mm")
    .withZone(ZoneId.systemDefault()).format(Instant.ofEpochMilli(value))

internal fun shareFeedback(context: Context, record: FeedbackItemWithScreenshots, repository: FeedbackRepository) {
    val uris = record.screenshots.mapNotNull { image ->
        repository.resolveScreenshot(image.relativePath).takeIf(File::isFile)?.let { file ->
            FileProvider.getUriForFile(context, "${context.packageName}.feedback-files", file)
        }
    }
    val text = "${record.item.title}\n\n${record.item.description}"
    val send = android.content.Intent(if (uris.isEmpty()) android.content.Intent.ACTION_SEND else android.content.Intent.ACTION_SEND_MULTIPLE).apply {
        type = if (uris.isEmpty()) "text/plain" else "image/*"
        putExtra(android.content.Intent.EXTRA_TEXT, text)
        if (uris.isNotEmpty()) {
            putParcelableArrayListExtra(android.content.Intent.EXTRA_STREAM, ArrayList(uris))
            clipData = android.content.ClipData.newUri(context.contentResolver, record.item.title, uris.first()).apply {
                uris.drop(1).forEach { addItem(android.content.ClipData.Item(it)) }
            }
            addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }
    context.startActivity(android.content.Intent.createChooser(send, "分享问题反馈"))
}
