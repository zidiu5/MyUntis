package com.myuntis.app.ui.screens.board

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import android.util.LruCache
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.myuntis.app.data.network.model.BoardAttachment
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File

data class ViewerState(val items: List<BoardAttachment>, val index: Int)

fun BoardAttachment.isImage() = mime.startsWith("image/")
fun BoardAttachment.isPdf()   = mime == "application/pdf"
fun BoardAttachment.canPreview() = isImage() || isPdf()

private sealed interface Load<out T> {
    object Loading : Load<Nothing>
    object Failed : Load<Nothing>
    class Ready<T>(val value: T) : Load<T>
}

// ── Decoding ──────────────────────────────────────────────────
suspend fun decodeSampled(file: File, maxSide: Int): Bitmap? = withContext(Dispatchers.IO) {
    runCatching {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.path, bounds)
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= maxSide) sample *= 2
        BitmapFactory.decodeFile(file.path, BitmapFactory.Options().apply { inSampleSize = sample })
    }.getOrNull()
}

private object ThumbCache {
    private val cache = object : LruCache<String, Bitmap>(16 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Bitmap) = value.byteCount
    }
    fun get(id: String): Bitmap? = cache.get(id)
    fun put(id: String, bitmap: Bitmap) { cache.put(id, bitmap) }
}

// ── Thumbnail inside a post ───────────────────────────────────
@Composable
fun ImageThumb(
    att: BoardAttachment,
    loadFile: suspend (BoardAttachment) -> File?,
    modifier: Modifier,
    onClick: () -> Unit
) {
    val state by produceState<Load<ImageBitmap>>(
        initialValue = ThumbCache.get(att.id)?.let { Load.Ready(it.asImageBitmap()) } ?: Load.Loading,
        key1 = att.id
    ) {
        if (value is Load.Ready) return@produceState
        val bitmap = loadFile(att)?.let { decodeSampled(it, 500) }
        value = if (bitmap != null) {
            ThumbCache.put(att.id, bitmap)
            Load.Ready(bitmap.asImageBitmap())
        } else Load.Failed
    }

    Box(
        modifier
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        when (val s = state) {
            is Load.Ready -> Image(
                s.value, att.name, Modifier.fillMaxSize(), contentScale = ContentScale.Crop
            )
            Load.Failed -> Icon(
                Icons.Outlined.BrokenImage, "Bild konnte nicht geladen werden",
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Load.Loading -> CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
        }
    }
}

// ── Full-screen viewer ────────────────────────────────────────
@Composable
fun AttachmentViewer(
    items: List<BoardAttachment>,
    startIndex: Int,
    loadFile: suspend (BoardAttachment) -> File?,
    onOpenExternal: (BoardAttachment) -> Unit,
    onSave: (BoardAttachment) -> Unit,
    onDismiss: () -> Unit
) {
    if (items.isEmpty()) { onDismiss(); return }
    var index by remember { mutableIntStateOf(startIndex.coerceIn(0, items.lastIndex)) }
    val att = items[index]

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize(), color = Color.Black) {
            Column(Modifier.fillMaxSize()) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onDismiss) { Icon(Icons.Outlined.Close, "Schließen", tint = Color.White) }
                    Column(Modifier.weight(1f)) {
                        Text(att.name, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.titleSmall)
                        if (items.size > 1) {
                            Text("${index + 1} von ${items.size}", color = Color.White.copy(alpha = 0.7f),
                                style = MaterialTheme.typography.labelSmall)
                        }
                    }
                    if (items.size > 1) {
                        IconButton(onClick = { if (index > 0) index-- }, enabled = index > 0) {
                            Icon(Icons.Outlined.ChevronLeft, "Vorherige",
                                tint = Color.White.copy(alpha = if (index > 0) 1f else 0.3f))
                        }
                        IconButton(onClick = { if (index < items.lastIndex) index++ }, enabled = index < items.lastIndex) {
                            Icon(Icons.Outlined.ChevronRight, "Nächste",
                                tint = Color.White.copy(alpha = if (index < items.lastIndex) 1f else 0.3f))
                        }
                    }
                    IconButton(onClick = { onSave(att) }) {
                        Icon(Icons.Outlined.Download, "In Downloads speichern", tint = Color.White)
                    }
                    IconButton(onClick = { onOpenExternal(att) }) {
                        Icon(Icons.Outlined.OpenInNew, "In anderer App öffnen", tint = Color.White)
                    }
                }

                Box(Modifier.weight(1f).fillMaxWidth()) {
                    key(att.id) {
                        val file by produceState<Load<File>>(Load.Loading, att.id) {
                            value = loadFile(att)?.let { Load.Ready(it) } ?: Load.Failed
                        }
                        when (val f = file) {
                            Load.Loading -> CircularProgressIndicator(Modifier.align(Alignment.Center), color = Color.White)
                            Load.Failed  -> ViewerMessage("Die Datei konnte nicht geladen werden.")
                            is Load.Ready -> if (att.isImage()) ImagePreview(f.value, att.name)
                            else PdfPreview(f.value)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun BoxScope.ViewerMessage(text: String) {
    Text(
        text, color = Color.White, textAlign = TextAlign.Center,
        modifier = Modifier.align(Alignment.Center).padding(32.dp)
    )
}

// ── Image with pinch zoom ─────────────────────────────────────
@Composable
private fun BoxScope.ImagePreview(file: File, name: String) {
    val state by produceState<Load<ImageBitmap>>(Load.Loading, file) {
        value = decodeSampled(file, 2400)?.let { Load.Ready(it.asImageBitmap()) } ?: Load.Failed
    }
    when (val s = state) {
        Load.Loading  -> CircularProgressIndicator(Modifier.align(Alignment.Center), color = Color.White)
        Load.Failed   -> ViewerMessage("Das Bild kann nicht angezeigt werden.")
        is Load.Ready -> ZoomableImage(s.value, name)
    }
}

@Composable
private fun ZoomableImage(bitmap: ImageBitmap, description: String) {
    var scale by remember(bitmap) { mutableFloatStateOf(1f) }
    var offset by remember(bitmap) { mutableStateOf(Offset.Zero) }

    Box(
        Modifier.fillMaxSize().pointerInput(bitmap) {
            detectTransformGestures { _, pan, zoom, _ ->
                scale = (scale * zoom).coerceIn(1f, 6f)
                offset = if (scale <= 1f) Offset.Zero else offset + pan
            }
        },
        contentAlignment = Alignment.Center
    ) {
        Image(
            bitmap, description,
            Modifier.fillMaxSize().graphicsLayer {
                scaleX = scale; scaleY = scale
                translationX = offset.x; translationY = offset.y
            },
            contentScale = ContentScale.Fit
        )
    }
}

// ── PDF: pages rendered with Android's PdfRenderer ────────────
private class PdfHolder(val renderer: PdfRenderer, val pfd: ParcelFileDescriptor) {
    val lock = Mutex()        // PdfRenderer allows only one open page at a time
    fun close() {
        runCatching { renderer.close() }
        runCatching { pfd.close() }
    }
}

@Composable
private fun BoxScope.PdfPreview(file: File) {
    val holder by produceState<Load<PdfHolder>>(Load.Loading, file) {
        value = withContext(Dispatchers.IO) {
            runCatching {
                val pfd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
                try {
                    Load.Ready(PdfHolder(PdfRenderer(pfd), pfd))
                } catch (e: Exception) {
                    pfd.close(); throw e
                }
            }.getOrElse { Load.Failed }
        }
    }

    DisposableEffect(holder) {
        val h = holder
        onDispose { if (h is Load.Ready) h.value.close() }
    }

    when (val h = holder) {
        Load.Loading -> CircularProgressIndicator(Modifier.align(Alignment.Center), color = Color.White)
        Load.Failed  -> ViewerMessage("Dieses PDF kann hier nicht angezeigt werden.\nTippe oben auf „In anderer App öffnen“.")
        is Load.Ready -> BoxWithConstraints(Modifier.fillMaxSize()) {
            val widthPx = with(LocalDensity.current) { maxWidth.roundToPx() }
            val renderWidth = (widthPx * 1.5f).toInt().coerceIn(600, 1800)   // sharper than screen width
            LazyColumn(
                Modifier.fillMaxSize(),
                contentPadding = PaddingValues(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(h.value.renderer.pageCount) { i -> PdfPage(h.value, i, renderWidth) }
            }
        }
    }
}

@Composable
private fun PdfPage(holder: PdfHolder, index: Int, renderWidth: Int) {
    val page by produceState<Load<ImageBitmap>>(Load.Loading, holder, index) {
        value = withContext(Dispatchers.IO) {
            holder.lock.withLock {
                runCatching {
                    val p = holder.renderer.openPage(index)
                    try {
                        val scale = renderWidth.toFloat() / p.width
                        val bitmap = Bitmap.createBitmap(
                            renderWidth, (p.height * scale).toInt().coerceAtLeast(1), Bitmap.Config.ARGB_8888
                        )
                        bitmap.eraseColor(android.graphics.Color.WHITE)
                        p.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                        Load.Ready(bitmap.asImageBitmap())
                    } finally {
                        p.close()
                    }
                }.getOrElse { Load.Failed }
            }
        }
    }

    when (val s = page) {
        is Load.Ready -> Image(s.value, "Seite ${index + 1}", Modifier.fillMaxWidth(), contentScale = ContentScale.FillWidth)
        else -> Box(
            Modifier.fillMaxWidth().aspectRatio(0.707f).background(Color.White.copy(alpha = 0.08f)),
            contentAlignment = Alignment.Center
        ) {
            if (s == Load.Failed) Text("Seite ${index + 1} fehlt", color = Color.White)
            else CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp, color = Color.White)
        }
    }
}