package com.ryan.pinehill.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.provider.OpenableColumns
import androidx.documentfile.provider.DocumentFile
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.korean.KoreanTextRecognizerOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object DocumentOcr {
    private val recognizer by lazy {
        TextRecognition.getClient(KoreanTextRecognizerOptions.Builder().build())
    }

    suspend fun extractText(context: Context, uri: Uri): String = withContext(Dispatchers.IO) {
        val mime = context.contentResolver.getType(uri).orEmpty()
        if (mime == "application/pdf" || displayName(context, uri).endsWith(".pdf", true)) {
            extractPdf(context, uri)
        } else {
            val image = InputImage.fromFilePath(context, uri)
            Tasks.await(recognizer.process(image)).text
        }
    }

    suspend fun collectFolderDocuments(context: Context, treeUri: Uri): List<Uri> = withContext(Dispatchers.IO) {
        val root = DocumentFile.fromTreeUri(context, treeUri) ?: return@withContext emptyList()
        val out = mutableListOf<Uri>()
        fun walk(node: DocumentFile, depth: Int) {
            if (depth > 4) return
            node.listFiles().forEach { file ->
                when {
                    file.isDirectory -> walk(file, depth + 1)
                    file.isFile && isSupported(file.name.orEmpty(), file.type.orEmpty()) -> out += file.uri
                }
            }
        }
        walk(root, 0)
        out.take(100)
    }

    fun displayName(context: Context, uri: Uri): String {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) return cursor.getString(0).orEmpty()
        }
        return uri.lastPathSegment.orEmpty()
    }

    private fun isSupported(name: String, type: String): Boolean =
        type.startsWith("image/") || type == "application/pdf" ||
            name.endsWith(".pdf", true) || name.endsWith(".jpg", true) ||
            name.endsWith(".jpeg", true) || name.endsWith(".png", true) || name.endsWith(".webp", true)

    private fun extractPdf(context: Context, uri: Uri): String {
        val descriptor = context.contentResolver.openFileDescriptor(uri, "r") ?: return ""
        descriptor.use { fd ->
            PdfRenderer(fd).use { renderer ->
                val builder = StringBuilder()
                val pages = minOf(renderer.pageCount, 20)
                for (i in 0 until pages) {
                    renderer.openPage(i).use { page ->
                        val maxWidth = 1800
                        val scale = minOf(2f, maxWidth.toFloat() / page.width.toFloat())
                        val bitmap = Bitmap.createBitmap(
                            (page.width * scale).toInt().coerceAtLeast(1),
                            (page.height * scale).toInt().coerceAtLeast(1),
                            Bitmap.Config.ARGB_8888
                        )
                        try {
                            page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                            val text = Tasks.await(recognizer.process(InputImage.fromBitmap(bitmap, 0))).text
                            builder.appendLine("--- page ${i + 1} ---").appendLine(text)
                        } finally {
                            bitmap.recycle()
                        }
                    }
                }
                return builder.toString()
            }
        }
    }
}
