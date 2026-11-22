package com.g2012.wirelessprint

enum class PaperSize(val label: String, val gutenprintName: String, val widthInches: Float, val heightInches: Float) {
    A4("A4", "A4", 8.27f, 11.69f),
    LETTER("Letter", "Letter", 8.5f, 11.0f),
    LEGAL("Legal", "Legal", 8.5f, 14.0f),
    A5("A5", "A5", 5.83f, 8.27f),
    PHOTO_4X6("4x6", "w288h432", 4.0f, 6.0f)
}

enum class PrintQuality(val label: String, val shortLabel: String, val dpi: Int) {
    DRAFT("Draft (Fast 150 DPI)", "Draft", 150),
    NORMAL("Normal (200 DPI)", "Normal", 200),
    HIGH("High (300 DPI)", "High", 300)
}

enum class PageFilter(val label: String) {
    ALL("All"),
    ODD("Odd"),
    EVEN("Even"),
    RANGE("Range")
}

enum class MultiPageMode(val label: String, val shortLabel: String, val pagesPerSheet: Int) {
    SINGLY("1 Page / Sheet", "1-up", 1),
    MULTIPLE_2("2 Pages / Sheet", "2-up", 2),
    MULTIPLE_4("4 Pages / Sheet", "4-up", 4)
}

enum class DuplexMode(val label: String) {
    ONE_SIDE("One-Sided"),
    MANUAL("Manual Duplex")
}

enum class OrientationMode(val label: String, val shortLabel: String) {
    AUTO("Auto (Smart)", "Auto"),
    PORTRAIT("Portrait", "Portrait"),
    LANDSCAPE("Landscape", "Landscape")
}

enum class MarginMode(val label: String, val shortLabel: String, val inches: Float) {
    NONE("Borderless (0mm)", "0mm", 0.0f),
    NARROW("Narrow (5mm)", "5mm", 0.2f),
    NORMAL("Normal (11mm)", "11mm", 0.45f)
}

enum class ScaleMode(val label: String) {
    SCALE_TO_FIT("Fit Page"),
    FILL_PAGE("Fill / Crop")
}

enum class PositionMode(val label: String) {
    CENTER("Center"),
    TOP_LEFT("Top-Left")
}

data class PrintSettings(
    val isColor: Boolean = false,
    val paperSize: PaperSize = PaperSize.A4,
    val mediaType: String = "Plain",
    val quality: PrintQuality = PrintQuality.NORMAL,
    val orientation: OrientationMode = OrientationMode.AUTO,
    val multiPageMode: MultiPageMode = MultiPageMode.SINGLY,
    val duplexMode: DuplexMode = DuplexMode.ONE_SIDE,
    val marginMode: MarginMode = MarginMode.NARROW,
    val scaleMode: ScaleMode = ScaleMode.SCALE_TO_FIT,
    val positionMode: PositionMode = PositionMode.CENTER,
    val pageFilter: PageFilter = PageFilter.ALL,
    val customRange: String = "",
    val reverseOrder: Boolean = false,
    val copies: Int = 1,
    val collate: Boolean = true,
    val directUsb: Boolean = false
) {
    fun resolvePageIndices(totalPages: Int): List<Int> {
        if (totalPages <= 0) return emptyList()
        val list = when (pageFilter) {
            PageFilter.ALL -> (0 until totalPages).toList()
            PageFilter.ODD -> (0 until totalPages).filter { (it + 1) % 2 == 1 }
            PageFilter.EVEN -> (0 until totalPages).filter { (it + 1) % 2 == 0 }
            PageFilter.RANGE -> parseCustomRange(customRange, totalPages)
        }
        return if (reverseOrder) list.reversed() else list
    }

    private fun parseCustomRange(input: String, totalPages: Int): List<Int> {
        val result = linkedSetOf<Int>()
        for (part in input.split(",")) {
            val t = part.trim()
            if (t.isEmpty()) continue
            if (t.contains("-")) {
                val b = t.split("-")
                val s = b.getOrNull(0)?.trim()?.toIntOrNull() ?: continue
                val e = b.getOrNull(1)?.trim()?.toIntOrNull() ?: continue
                val lo = minOf(s, e).coerceIn(1, totalPages)
                val hi = maxOf(s, e).coerceIn(1, totalPages)
                for (p in lo..hi) result.add(p - 1)
            } else {
                val p = t.toIntOrNull() ?: continue
                if (p in 1..totalPages) result.add(p - 1)
            }
        }
        return if (result.isEmpty()) (0 until totalPages).toList() else result.toList()
    }
}
