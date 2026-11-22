package com.g2012.wirelessprint

import android.content.Context
import android.graphics.*
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint

sealed class PrintSource {
    data class Pdf(val uri: Uri, val displayName: String) : PrintSource()
    data class Images(val uris: List<Uri>) : PrintSource()
    data class PlainText(val text: String, val title: String) : PrintSource()
    object TestPage : PrintSource()
}

data class ComposedSheetResult(
    val bitmap: Bitmap,
    val resolvedLandscape: Boolean
)

object PageComposer {

    fun getSourcePageCount(context: Context, source: PrintSource): Int {
        return when (source) {
            is PrintSource.Pdf -> {
                context.contentResolver.openFileDescriptor(source.uri, "r")?.use { pfd ->
                    PdfRenderer(pfd).use { it.pageCount }
                } ?: 0
            }
            is PrintSource.Images -> source.uris.size
            is PrintSource.PlainText -> 1
            is PrintSource.TestPage -> 1
        }
    }

    fun getTotalSheetCount(context: Context, source: PrintSource, settings: PrintSettings): Int {
        val rawPages = getSourcePageCount(context, source)
        val selected = settings.resolvePageIndices(rawPages).size
        if (selected == 0) return 0
        val nUp = settings.multiPageMode.pagesPerSheet
        return (selected + nUp - 1) / nUp
    }

    private fun getNaturalPageDimensions(context: Context, source: PrintSource, pageIndex: Int): Pair<Int, Int> {
        return when (source) {
            is PrintSource.Pdf -> {
                context.contentResolver.openFileDescriptor(source.uri, "r")?.use { pfd ->
                    PdfRenderer(pfd).use { renderer ->
                        if (pageIndex in 0 until renderer.pageCount) {
                            renderer.openPage(pageIndex).use { page -> page.width to page.height }
                        } else 595 to 842
                    }
                } ?: (595 to 842)
            }
            is PrintSource.Images -> {
                val uri = source.uris.getOrNull(pageIndex) ?: return 1000 to 1400
                val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) }
                if (opts.outWidth > 0 && opts.outHeight > 0) opts.outWidth to opts.outHeight else 1000 to 1400
            }
            is PrintSource.PlainText -> 595 to 842
            is PrintSource.TestPage -> 595 to 842
        }
    }

    fun resolveSheetIsLandscape(
        context: Context,
        source: PrintSource,
        settings: PrintSettings,
        firstPageIndexOnSheet: Int
    ): Boolean {
        return when (settings.orientation) {
            OrientationMode.PORTRAIT -> false
            OrientationMode.LANDSCAPE -> true
            OrientationMode.AUTO -> {
                val (w, h) = getNaturalPageDimensions(context, source, firstPageIndexOnSheet)
                val pageIsLandscape = w > h
                if (settings.multiPageMode == MultiPageMode.MULTIPLE_2) !pageIsLandscape else pageIsLandscape
            }
        }
    }

    fun composeSheet(
        context: Context,
        source: PrintSource,
        settings: PrintSettings,
        sheetIndex: Int,
        dpi: Int,
        forPrinting: Boolean
    ): ComposedSheetResult {
        val rawPages = getSourcePageCount(context, source)
        val selectedIndices = settings.resolvePageIndices(rawPages)
        val nUp = settings.multiPageMode.pagesPerSheet
        val pageIndicesForSheet = selectedIndices.drop(sheetIndex * nUp).take(nUp)
        val firstIdx = pageIndicesForSheet.firstOrNull() ?: 0

        val isLandscape = resolveSheetIsLandscape(context, source, settings, firstIdx)

        val portraitW = (settings.paperSize.widthInches * dpi).toInt().coerceAtLeast(300)
        val portraitH = (settings.paperSize.heightInches * dpi).toInt().coerceAtLeast(400)
        val canvasW = if (isLandscape) portraitH else portraitW
        val canvasH = if (isLandscape) portraitW else portraitH

        val workBmp = Bitmap.createBitmap(canvasW, canvasH, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(workBmp)
        canvas.drawColor(Color.WHITE)

        val margin = dpi * settings.marginMode.inches
        val innerW = canvasW - margin * 2
        val innerH = canvasH - margin * 2

        val slots: List<RectF> = when (nUp) {
            2 -> {
                val gap = dpi * 0.15f
                if (isLandscape) {
                    val slotW = (innerW - gap) / 2f
                    listOf(
                        RectF(margin, margin, margin + slotW, margin + innerH),
                        RectF(margin + slotW + gap, margin, margin + innerW, margin + innerH)
                    )
                } else {
                    val slotH = (innerH - gap) / 2f
                    listOf(
                        RectF(margin, margin, margin + innerW, margin + slotH),
                        RectF(margin, margin + slotH + gap, margin + innerW, margin + innerH)
                    )
                }
            }
            4 -> {
                val gap = dpi * 0.15f
                val slotW = (innerW - gap) / 2f
                val slotH = (innerH - gap) / 2f
                listOf(
                    RectF(margin, margin, margin + slotW, margin + slotH),
                    RectF(margin + slotW + gap, margin, margin + innerW, margin + slotH),
                    RectF(margin, margin + slotH + gap, margin + slotW, margin + innerH),
                    RectF(margin + slotW + gap, margin + slotH + gap, margin + innerW, margin + innerH)
                )
            }
            else -> listOf(RectF(margin, margin, margin + innerW, margin + innerH))
        }

        val drawPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        if (!settings.isColor) {
            val cm = ColorMatrix().apply { setSaturation(0f) }
            drawPaint.colorFilter = ColorMatrixColorFilter(cm)
        }

        for ((slotIdx, pageIdx) in pageIndicesForSheet.withIndex()) {
            val slot = slots.getOrNull(slotIdx) ?: break
            val srcBmp = renderSingleSourcePage(
                context, source, pageIdx,
                slot.width().toInt().coerceAtLeast(100),
                slot.height().toInt().coerceAtLeast(100)
            ) ?: continue

            val scale = if (settings.scaleMode == ScaleMode.FILL_PAGE) {
                maxOf(slot.width() / srcBmp.width, slot.height() / srcBmp.height)
            } else {
                minOf(slot.width() / srcBmp.width, slot.height() / srcBmp.height)
            }
            val dw = srcBmp.width * scale
            val dh = srcBmp.height * scale
            val left = if (settings.positionMode == PositionMode.CENTER) slot.left + (slot.width() - dw) / 2f else slot.left
            val top = if (settings.positionMode == PositionMode.CENTER) slot.top + (slot.height() - dh) / 2f else slot.top

            canvas.save()
            canvas.clipRect(slot)
            canvas.drawBitmap(srcBmp, null, RectF(left, top, left + dw, top + dh), drawPaint)
            canvas.restore()
            srcBmp.recycle()
        }

        if (isLandscape && forPrinting) {
            val matrix = Matrix().apply { postRotate(90f) }
            val rotated = Bitmap.createBitmap(workBmp, 0, 0, workBmp.width, workBmp.height, matrix, true)
            workBmp.recycle()
            return ComposedSheetResult(rotated, true)
        }
        return ComposedSheetResult(workBmp, isLandscape)
    }

    private fun renderSingleSourcePage(
        context: Context,
        source: PrintSource,
        pageIndex: Int,
        targetW: Int,
        targetH: Int
    ): Bitmap? {
        return when (source) {
            is PrintSource.Pdf -> {
                context.contentResolver.openFileDescriptor(source.uri, "r")?.use { pfd ->
                    PdfRenderer(pfd).use { renderer ->
                        if (pageIndex !in 0 until renderer.pageCount) return null
                        renderer.openPage(pageIndex).use { page ->
                            val scale = minOf(targetW.toFloat() / page.width, targetH.toFloat() / page.height)
                            val w = (page.width * scale).toInt().coerceAtLeast(1)
                            val h = (page.height * scale).toInt().coerceAtLeast(1)
                            val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                            Canvas(bmp).drawColor(Color.WHITE)
                            page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_PRINT)
                            bmp
                        }
                    }
                }
            }
            is PrintSource.Images -> {
                val uri = source.uris.getOrNull(pageIndex) ?: return null
                context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it) }
            }
            is PrintSource.PlainText -> {
                val bmp = Bitmap.createBitmap(targetW, targetH, Bitmap.Config.ARGB_8888)
                val c = Canvas(bmp)
                c.drawColor(Color.WHITE)
                val tp = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = Color.BLACK
                    textSize = (targetW / 28f).coerceAtLeast(18f)
                }
                val pad = (targetW * 0.05f).toInt()
                val textWidth = (targetW - pad * 2).coerceAtLeast(100)
                val layout = StaticLayout.Builder.obtain(source.text, 0, source.text.length, tp, textWidth)
                    .setAlignment(Layout.Alignment.ALIGN_NORMAL)
                    .setLineSpacing(6f, 1.15f)
                    .build()
                c.save()
                c.translate(pad.toFloat(), pad.toFloat())
                layout.draw(c)
                c.restore()
                bmp
            }
            is PrintSource.TestPage -> {
                val bmp = Bitmap.createBitmap(targetW, targetH, Bitmap.Config.ARGB_8888)
                val c = Canvas(bmp)
                c.drawColor(Color.WHITE)
                val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = Color.parseColor("#0891B2")
                    textSize = targetW * 0.062f
                    typeface = Typeface.DEFAULT_BOLD
                }
                c.drawText("Canon PIXMA G2012", targetW * 0.09f, targetH * 0.14f, p)
                p.color = Color.parseColor("#0F172A")
                p.textSize = targetW * 0.036f
                p.typeface = Typeface.DEFAULT
                c.drawText("G2012 Studio Ultra — Exact Replica Calibration", targetW * 0.09f, targetH * 0.20f, p)
                c.drawText("CMYK Nozzle & Alignment Check", targetW * 0.09f, targetH * 0.25f, p)

                val colors = listOf(Color.CYAN, Color.MAGENTA, Color.YELLOW, Color.BLACK)
                val barW = (targetW * 0.82f) / 4f
                for ((i, col) in colors.withIndex()) {
                    p.color = col
                    c.drawRect(targetW * 0.09f + i * barW, targetH * 0.31f, targetW * 0.09f + (i + 1) * barW - 12f, targetH * 0.39f, p)
                }
                p.color = Color.parseColor("#334155")
                p.style = Paint.Style.STROKE
                p.strokeWidth = 4f
                c.drawRect(targetW * 0.04f, targetH * 0.04f, targetW * 0.96f, targetH * 0.96f, p)
                bmp
            }
        }
    }
}
