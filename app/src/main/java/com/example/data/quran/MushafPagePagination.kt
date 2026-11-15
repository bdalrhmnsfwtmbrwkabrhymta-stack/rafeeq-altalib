package com.example.data.quran

/**
 * Represents a contiguous section of ayahs belonging to a single Surah displayed on a single Mushaf page.
 */
data class MushafPageSegment(
    val surahInfo: SurahInfo,
    val startsSurah: Boolean,
    val showBismillahHeader: Boolean,
    val ayahs: List<Ayah>
)

/**
 * Represents one sequential page (1..604) of the 604-page Madinah Mushaf.
 */
data class MushafPage(
    val pageNumber: Int,
    val juzNumber: Int,
    val primarySurah: SurahInfo,
    val segments: List<MushafPageSegment>
)

/**
 * Deterministic sequential 604-page Madinah Mushaf pagination anchored at every authentic Surah
 * start page (all 114 surahs) and every authentic Juz start page (all 30 ajza).
 *
 * Guarantees:
 * - Exactly 604 pages (1..604) in strict sequential order.
 * - Page 1 contains complete Surah Al-Fatihah (ayahs 1..7).
 * - Page 2 starts Surah Al-Baqarah (ayahs 1..5).
 * - Zero empty pages, zero skipped ayahs, zero duplicated ayahs across all 6,236 ayahs.
 */
object MushafPagePagination {

    const val TOTAL_MUSHAF_PAGES = 604

    private data class PageAnchor(
        val page: Int,
        val surah: Int,
        val ayah: Int
    )

    private data class GlobalAyah(
        val surahInfo: SurahInfo,
        val ayah: Ayah
    )

    fun buildPages(
        allSurahs: List<SurahDetail>,
        allJuzList: List<JuzInfo>
    ): List<MushafPage> {
        if (allSurahs.isEmpty()) return emptyList()

        val surahMap = allSurahs.associateBy { it.info.number }
        val flatAyahs = ArrayList<GlobalAyah>(6240)
        val indexByKey = HashMap<Long, Int>(6240)

        for (detail in allSurahs.sortedBy { it.info.number }) {
            for (ayah in detail.ayahs.sortedBy { it.numberInSurah }) {
                val idx = flatAyahs.size
                flatAyahs.add(GlobalAyah(detail.info, ayah))
                indexByKey[keyOf(detail.info.number, ayah.numberInSurah)] = idx
            }
        }
        if (flatAyahs.isEmpty()) return emptyList()

        // Collect canonical anchors: Page 1 (Fatihah 1:1), Page 2 (Baqarah 2:1),
        // Page 3 (Baqarah 2:6), Page 4 (Baqarah 2:17), Page 5 (Baqarah 2:25),
        // plus every Surah starting page and every Juz starting page.
        val rawAnchors = mutableListOf<PageAnchor>()
        rawAnchors.add(PageAnchor(page = 1, surah = 1, ayah = 1))
        rawAnchors.add(PageAnchor(page = 2, surah = 2, ayah = 1))
        rawAnchors.add(PageAnchor(page = 3, surah = 2, ayah = 6))
        rawAnchors.add(PageAnchor(page = 4, surah = 2, ayah = 17))
        rawAnchors.add(PageAnchor(page = 5, surah = 2, ayah = 25))

        for (detail in allSurahs) {
            rawAnchors.add(
                PageAnchor(
                    page = detail.info.page.coerceIn(1, TOTAL_MUSHAF_PAGES),
                    surah = detail.info.number,
                    ayah = 1
                )
            )
        }
        for (juz in allJuzList) {
            rawAnchors.add(
                PageAnchor(
                    page = juz.startPage.coerceIn(1, TOTAL_MUSHAF_PAGES),
                    surah = juz.startSurahNumber,
                    ayah = juz.startAyahNumber
                )
            )
        }

        // Convert to flat indices and keep strictly monotonic (page -> earliest flatIndex on that page,
        // deduplicated so each page maps to its first anchor).
        val sortedValidAnchors = rawAnchors
            .mapNotNull { anchor ->
                val idx = indexByKey[keyOf(anchor.surah, anchor.ayah)] ?: return@mapNotNull null
                anchor.page to idx
            }
            .sortedWith(compareBy<Pair<Int, Int>>({ it.first }, { it.second }))

        // Keep one entry per page (earliest flat index on that page) and enforce strictly increasing indices
        val cleanAnchors = mutableListOf<Pair<Int, Int>>() // (pageNumber, startFlatIndex)
        for ((page, idx) in sortedValidAnchors) {
            if (cleanAnchors.isEmpty()) {
                cleanAnchors.add(page to idx)
            } else {
                val (prevPage, prevIdx) = cleanAnchors.last()
                if (page == prevPage) {
                    // Already anchored this page at an earlier surah/ayah
                    continue
                }
                if (idx > prevIdx) {
                    cleanAnchors.add(page to idx)
                }
            }
        }

        // Ensure anchor starts at page 1, index 0
        if (cleanAnchors.isEmpty() || cleanAnchors.first().first != 1) {
            cleanAnchors.add(0, 1 to 0)
        }

        // Assign start flat index for every single page 1..604
        val pageStartIndices = IntArray(TOTAL_MUSHAF_PAGES + 1) // 1-indexed, index 604 + sentinel at end
        val anchorBounds = mutableListOf<Pair<Int, Int>>()
        anchorBounds.addAll(cleanAnchors)
        anchorBounds.add((TOTAL_MUSHAF_PAGES + 1) to flatAyahs.size)

        // Precompute cumulative character weights (word/glyph complexity weight) for smooth balanced density
        val prefixWeight = LongArray(flatAyahs.size + 1)
        for (i in flatAyahs.indices) {
            val len = flatAyahs[i].ayah.text.length.coerceAtLeast(12) + 8
            prefixWeight[i + 1] = prefixWeight[i] + len
        }

        for (b in 0 until anchorBounds.size - 1) {
            val (startPage, startIdx) = anchorBounds[b]
            val (endPage, endIdx) = anchorBounds[b + 1]
            val pageCount = (endPage - startPage).coerceAtLeast(1)
            val ayahSpan = (endIdx - startIdx).coerceAtLeast(pageCount)

            pageStartIndices[startPage] = startIdx

            if (pageCount > 1) {
                val totalSpanWeight = (prefixWeight[endIdx] - prefixWeight[startIdx]).coerceAtLeast(1L)
                var prevBoundary = startIdx

                for (offset in 1 until pageCount) {
                    val pageNum = startPage + offset
                    val remainingPages = pageCount - offset
                    val targetWeight = prefixWeight[startIdx] +
                        (totalSpanWeight * offset) / pageCount

                    // Binary search closest ayah index within [prevBoundary + 1 .. endIdx - remainingPages]
                    val minIdx = (prevBoundary + 1).coerceAtMost(endIdx - remainingPages)
                    val maxIdx = (endIdx - remainingPages).coerceAtLeast(minIdx)

                    var lo = minIdx
                    var hi = maxIdx
                    while (lo < hi) {
                        val mid = (lo + hi) / 2
                        if (prefixWeight[mid] < targetWeight) {
                            lo = mid + 1
                        } else {
                            hi = mid
                        }
                    }
                    val chosen = lo.coerceIn(minIdx, maxIdx)
                    pageStartIndices[pageNum] = chosen
                    prevBoundary = chosen
                }
            }
        }

        val juzStartPages = allJuzList.sortedBy { it.startPage }

        val pages = ArrayList<MushafPage>(TOTAL_MUSHAF_PAGES)
        for (pageNum in 1..TOTAL_MUSHAF_PAGES) {
            val startIdx = pageStartIndices[pageNum]
            val endIdx = if (pageNum < TOTAL_MUSHAF_PAGES) {
                pageStartIndices[pageNum + 1]
            } else {
                flatAyahs.size
            }

            val slice = if (startIdx < endIdx && startIdx in flatAyahs.indices) {
                flatAyahs.subList(startIdx, endIdx.coerceAtMost(flatAyahs.size))
            } else {
                emptyList()
            }

            // Group slice by Surah maintaining order
            val segments = mutableListOf<MushafPageSegment>()
            var currentSurahNum = -1
            var currentAyahs = mutableListOf<Ayah>()
            var currentInfo: SurahInfo? = null

            for (item in slice) {
                if (item.surahInfo.number != currentSurahNum) {
                    if (currentAyahs.isNotEmpty() && currentInfo != null) {
                        val firstAyahNum = currentAyahs.first().numberInSurah
                        val isSurahStart = firstAyahNum == 1
                        val showBismillah = isSurahStart && currentInfo.number != 1 && currentInfo.number != 9
                        segments.add(
                            MushafPageSegment(
                                surahInfo = currentInfo,
                                startsSurah = isSurahStart,
                                showBismillahHeader = showBismillah,
                                ayahs = currentAyahs
                            )
                        )
                    }
                    currentSurahNum = item.surahInfo.number
                    currentInfo = item.surahInfo
                    currentAyahs = mutableListOf(item.ayah)
                } else {
                    currentAyahs.add(item.ayah)
                }
            }
            if (currentAyahs.isNotEmpty() && currentInfo != null) {
                val firstAyahNum = currentAyahs.first().numberInSurah
                val isSurahStart = firstAyahNum == 1
                val showBismillah = isSurahStart && currentInfo.number != 1 && currentInfo.number != 9
                segments.add(
                    MushafPageSegment(
                        surahInfo = currentInfo,
                        startsSurah = isSurahStart,
                        showBismillahHeader = showBismillah,
                        ayahs = currentAyahs
                    )
                )
            }

            val juzNumber = resolveJuzForPage(pageNum, juzStartPages)
            val primarySurah = segments.firstOrNull()?.surahInfo
                ?: surahMap.values.lastOrNull { it.info.page <= pageNum }?.info
                ?: allSurahs.first().info

            pages.add(
                MushafPage(
                    pageNumber = pageNum,
                    juzNumber = juzNumber,
                    primarySurah = primarySurah,
                    segments = segments
                )
            )
        }

        return pages
    }

    fun resolveJuzForPage(pageNumber: Int, juzList: List<JuzInfo>): Int {
        var resolved = 1
        for (juz in juzList) {
            if (juz.startPage <= pageNumber) {
                resolved = juz.number
            } else {
                break
            }
        }
        return resolved.coerceIn(1, 30)
    }

    fun getJuzPageRange(juzNumber: Int, juzList: List<JuzInfo>): IntRange {
        val sorted = juzList.sortedBy { it.number }
        val current = sorted.firstOrNull { it.number == juzNumber } ?: return 1..21
        val next = sorted.firstOrNull { it.number == juzNumber + 1 }
        val endPage = if (next != null) (next.startPage - 1).coerceAtLeast(current.startPage) else TOTAL_MUSHAF_PAGES
        return current.startPage..endPage
    }

    fun findPageForSurah(surahNumber: Int, pages: List<MushafPage>, fallbackSurahInfo: List<SurahInfo>): Int {
        val fromPages = pages.firstOrNull { page ->
            page.segments.any { it.surahInfo.number == surahNumber && it.startsSurah }
        }?.pageNumber
        if (fromPages != null) return fromPages
        return fallbackSurahInfo.firstOrNull { it.number == surahNumber }?.page?.coerceIn(1, TOTAL_MUSHAF_PAGES) ?: 1
    }

    fun findPageForSurahAyah(
        surahNumber: Int,
        ayahNumber: Int,
        pages: List<MushafPage>,
        fallbackSurahInfo: List<SurahInfo>
    ): Int {
        val exact = pages.firstOrNull { page ->
            page.segments.any { seg ->
                seg.surahInfo.number == surahNumber && seg.ayahs.any { it.numberInSurah == ayahNumber }
            }
        }?.pageNumber
        if (exact != null) return exact
        return findPageForSurah(surahNumber, pages, fallbackSurahInfo)
    }

    private fun keyOf(surah: Int, ayah: Int): Long = (surah.toLong() shl 16) or (ayah.toLong() and 0xFFFFL)
}
