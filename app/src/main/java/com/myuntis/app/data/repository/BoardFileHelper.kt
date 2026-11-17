package com.myuntis.app.data.repository

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.provider.OpenableColumns
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.ByteArrayOutputStream
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/** Prepares files for upload (shrink photos, check type and size) and handles local files. */
@Singleton
class BoardFileHelper @Inject constructor(
    @ApplicationContext private val context: Context
) {
    class Prepared(val name: String, val mime: String, val bytes: ByteArray)

    companion object {
        const val MAX_BYTES = 10L * 1024 * 1024     // server limit per file
        const val MAX_SIDE  = 1600                  // longest side of a photo in px

        private val IMAGE_EXT = setOf("jpg", "jpeg", "png", "gif", "webp", "heic", "heif")
        private val DOC_TYPES = mapOf(
            "pdf"  to "application/pdf",
            "doc"  to "application/msword",
            "docx" to "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            "xls"  to "application/vnd.ms-excel",
            "xlsx" to "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
            "ppt"  to "application/vnd.ms-powerpoint",
            "pptx" to "application/vnd.openxmlformats-officedocument.presentationml.presentation"
        )
    }

    /** Throws IllegalArgumentException with a message the user can read. */
    fun prepare(uri: Uri): Prepared {
        val name = displayName(uri)
        val ext  = name.substringAfterLast('.', "").lowercase()
        val type = context.contentResolver.getType(uri).orEmpty()
        val isImage = type.startsWith("image/") || ext in IMAGE_EXT

        if (isImage) {
            if (type == "image/gif" || ext == "gif") {
                return Prepared(name, "image/gif", readBytes(uri))
            }
            return compressImage(uri, name, isPng = type == "image/png" || ext == "png")
        }

        val mime = DOC_TYPES[ext] ?: throw IllegalArgumentException(
            "Dateityp nicht erlaubt. Erlaubt: Fotos, PDF, Word, Excel, PowerPoint."
        )
        return Prepared(name, mime, readBytes(uri))
    }

    private fun displayName(uri: Uri): String {
        context.contentResolver
            .query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { c -> if (c.moveToFirst()) c.getString(0)?.let { return it } }
        return uri.lastPathSegment?.substringAfterLast('/') ?: "datei"
    }

    private fun readBytes(uri: Uri): ByteArray {
        val stream = context.contentResolver.openInputStream(uri)
            ?: throw IllegalArgumentException("Die Datei kann nicht gelesen werden.")
        stream.use { input ->
            val out = ByteArrayOutputStream()
            val buf = ByteArray(16 * 1024)
            var total = 0L
            while (true) {
                val n = input.read(buf)
                if (n < 0) break
                total += n
                if (total > MAX_BYTES) throw IllegalArgumentException("Die Datei ist größer als 10 MB.")
                out.write(buf, 0, n)
            }
            return out.toByteArray()
        }
    }

    private fun compressImage(uri: Uri, name: String, isPng: Boolean): Prepared {
        val bitmap = decodeScaled(uri)
            ?: throw IllegalArgumentException("Das Bild kann nicht gelesen werden.")
        val out = ByteArrayOutputStream()
        // Re-encoding drops EXIF data (location, camera info)
        if (isPng) bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
        else bitmap.compress(Bitmap.CompressFormat.JPEG, 85, out)
        bitmap.recycle()

        val bytes = out.toByteArray()
        if (bytes.size > MAX_BYTES) {
            throw IllegalArgumentException("Das Bild ist auch verkleinert größer als 10 MB.")
        }
        val base = name.substringBeforeLast('.', name)
        return if (isPng) Prepared("$base.png", "image/png", bytes)
        else Prepared("$base.jpg", "image/jpeg", bytes)
    }

    private fun decodeScaled(uri: Uri): Bitmap? = try {
        if (Build.VERSION.SDK_INT >= 28) {
            // ImageDecoder also applies the EXIF rotation
            val src = ImageDecoder.createSource(context.contentResolver, uri)
            ImageDecoder.decodeBitmap(src) { decoder, info, _ ->
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                val w = info.size.width
                val h = info.size.height
                val scale = MAX_SIDE.toFloat() / maxOf(w, h)
                if (scale < 1f) {
                    decoder.setTargetSize(
                        (w * scale).toInt().coerceAtLeast(1),
                        (h * scale).toInt().coerceAtLeast(1)
                    )
                }
            }
        } else {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
            var sample = 1
            while (maxOf(bounds.outWidth, bounds.outHeight) / sample > MAX_SIDE * 2) sample *= 2
            val opts = BitmapFactory.Options().apply { inSampleSize = sample }
            context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) }
        }
    } catch (_: Exception) { null }

    // ── Downloaded files ──────────────────────────────────────
    fun cacheFile(id: String, name: String): File {
        val dir = File(context.cacheDir, "board").apply { mkdirs() }
        return File(dir, id + "_" + name.replace(Regex("[^A-Za-z0-9._-]"), "_"))
    }

    /** null = saved, otherwise an error text. */
    fun saveToDownloads(file: File, name: String, mime: String): String? {
        if (Build.VERSION.SDK_INT < 29) {
            return "Speichern geht ab Android 10. Öffne die Datei und speichere sie dort."
        }
        return try {
            val values = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, name)
                put(MediaStore.Downloads.MIME_TYPE, mime)
                put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/MyUntis")
            }
            val resolver = context.contentResolver
            val target = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                ?: return "Speichern fehlgeschlagen."
            resolver.openOutputStream(target)?.use { out ->
                file.inputStream().use { it.copyTo(out) }
            }
            null
        } catch (e: Exception) {
            e.localizedMessage ?: "Speichern fehlgeschlagen."
        }
    }
}