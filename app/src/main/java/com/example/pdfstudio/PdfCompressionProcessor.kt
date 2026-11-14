package com.example.pdfstudio

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color as AndroidColor
import androidx.compose.runtime.Composable
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.graphics.image.JPEGFactory
import com.tom_roush.pdfbox.pdmodel.graphics.image.PDImageXObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.max
import kotlin.math.roundToInt

object PdfCompressionEngine {
    suspend fun compressPdf(
        context: Context,
        sourceFile: File,
        level: CompressionLevel,
        organizedPages: List<OrganizePageItem>? = null,
        stripMetadata: Boolean = true,
        onProgress: (Float) -> Unit
    ): Result<CompressionResult> = withContext(Dispatchers.IO) {
        PdfEngineHelper.ensurePdfBoxInit(context)
        try {
            val originalSize = sourceFile.length()
            val outFile = File(
                PdfEngineHelper.getStudioExportsDir(context),
                "${sourceFile.nameWithoutExtension}_compressed_${System.currentTimeMillis()}.pdf"
            )

            // إذا قام المستخدم بتنظيم/تدوير/حذف صفحات قبل الضغط، نجهز المستند المعدل أولاً
            val workingInputFile: File = if (!organizedPages.isNullOrEmpty()) {
                val staged = File(
                    PdfEngineHelper.getStudioCacheDir(context),
                    "staged_${System.currentTimeMillis()}.pdf"
                )
                PDDocument.load(sourceFile).use { src ->
                    PDDocument().use { dst ->
                        organizedPages.forEach { item ->
                            if (item.sourcePageIndex in 0 until src.numberOfPages) {
                                val imported = dst.importPage(src.getPage(item.sourcePageIndex))
                                imported.rotation = ((imported.rotation + item.rotationDegrees) % 360 + 360) % 360
                            }
                        }
                        dst.save(staged)
                    }
                }
                staged
            } else {
                sourceFile
            }

            val pageCount: Int
            PDDocument.load(workingInputFile).use { doc ->
                pageCount = doc.numberOfPages
                if (stripMetadata || level == CompressionLevel.MAX) {
                    doc.documentCatalog.metadata = null
                }

                val pages = doc.pages
                for (index in 0 until pageCount) {
                    val page = pages.get(index)
                    val resources = page.resources
                    if (resources != null) {
                        val xNames = resources.xObjectNames?.toList().orEmpty()
                        for (name in xNames) {
                            val xObj = try { resources.getXObject(name) } catch (_: Exception) { null }
                            if (xObj is PDImageXObject && !xObj.isStencil && xObj.softMask == null) {
                                try {
                                    val srcBmp = xObj.image
                                    if (srcBmp != null) {
                                        val maxSide = max(srcBmp.width, srcBmp.height)
                                        val shouldProcess = when (level) {
                                            CompressionLevel.LIGHT -> maxSide > level.maxDimensionPx
                                            CompressionLevel.BALANCED,
                                            CompressionLevel.MAX -> maxSide > 180
                                        }
                                        if (shouldProcess) {
                                            val scale = if (maxSide > level.maxDimensionPx) {
                                                level.maxDimensionPx.toFloat() / maxSide.toFloat()
                                            } else {
                                                1f
                                            }
                                            val targetW = max(1, (srcBmp.width * scale).roundToInt())
                                            val targetH = max(1, (srcBmp.height * scale).roundToInt())
                                            val scaled = if (scale < 0.99f) {
                                                Bitmap.createScaledBitmap(srcBmp, targetW, targetH, true)
                                            } else {
                                                srcBmp
                                            }
                                            val flatRgb = Bitmap.createBitmap(targetW, targetH, Bitmap.Config.RGB_565)
                                            val canvas = Canvas(flatRgb)
                                            canvas.drawColor(AndroidColor.WHITE)
                                            canvas.drawBitmap(scaled, 0f, 0f, null)

                                            val jpegObj = JPEGFactory.createFromImage(doc, flatRgb, level.jpegQuality)
                                            resources.put(name, jpegObj)

                                            flatRgb.recycle()
                                            if (scaled !== srcBmp) scaled.recycle()
                                        }
                                        srcBmp.recycle()
                                    }
                                } catch (_: OutOfMemoryError) {
                                    System.gc()
                                } catch (_: Exception) {
                                }
                            }
                        }
                    }
                    onProgress(((index + 1).toFloat() / max(1, pageCount).toFloat()) * 0.9f)
                }

                doc.save(outFile)
            }
            if (workingInputFile !== sourceFile) workingInputFile.delete()
            onProgress(1f)

            val compressedSize = outFile.length()
            // قاعدة الأمان الذكية: الاحتفاظ بالملف الأصلي إذا لم ينقص الحجم
            if ((organizedPages == null && compressedSize >= originalSize) || compressedSize == 0L) {
                workingInputFile.takeIf { it.exists() }?.copyTo(outFile, overwrite = true)
                    ?: sourceFile.copyTo(outFile, overwrite = true)
                Result.success(
                    CompressionResult(
                        outputFilePath = outFile.absolutePath,
                        originalSizeBytes = originalSize,
                        compressedSizeBytes = originalSize,
                        wasKeptOriginal = true,
                        pageCount = pageCount
                    )
                )
            } else {
                Result.success(
                    CompressionResult(
                        outputFilePath = outFile.absolutePath,
                        originalSizeBytes = originalSize,
                        compressedSizeBytes = compressedSize,
                        wasKeptOriginal = false,
                        pageCount = pageCount
                    )
                )
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}

@Composable
fun PdfCompressionScreen(
    initialUriString: String? = null,
    onBack: () -> Unit
) {
    PdfCompressionOrganizeScreen(
        initialUriString = initialUriString,
        onBack = onBack
    )
}
