package com.fastingshift.domain.backup

import com.fastingshift.domain.model.Fast
import com.fastingshift.domain.model.FastKind
import java.time.Instant

class HistoryFileException : IllegalArgumentException()

enum class HistorySkip { DUPLICATE, OVERLAP, NO_GOAL, LEGACY_ZERO, UNFINISHED, INVALID }

data class HistoryBatch(
    val fasts: List<Fast>,
    val skipped: Map<HistorySkip, Int> = emptyMap(),
)

object HistoryCsv {
    const val MAX_BYTES = 4 * 1024 * 1024
    internal const val MAX_ROWS = 20_000
    private val header = listOf("start_at", "target_end_at", "ended_at", "protocol_id", "kind")
    private val legacyZero = setOf("Date", "Start", "End", "Hours", "Night Eating")

    /** Only the fields needed to reconstruct a finished fast. Personal reflections stay private. */
    fun export(fasts: List<Fast>): String = CsvExport.table(
        header,
        fasts.filter { it.endedAt != null }.sortedBy { it.startAt }.map {
            listOf(it.startAt, it.targetEndAt, it.endedAt, it.protocolId, it.kind.name)
        },
    )

    fun parse(text: String, now: Instant): HistoryBatch {
        val rows = csvRows(text.removePrefix("\uFEFF")).iterator()
        if (!rows.hasNext()) throw HistoryFileException()
        val columns = rows.next().map { it.trim() }
        val isZero = columns.toSet() == legacyZero
        if (columns.distinct().size != columns.size ||
            (!isZero && !columns.containsAll(listOf("start_at", "ended_at")))) throw HistoryFileException()

        val positions = columns.withIndex().associate { it.value to it.index }
        val found = mutableListOf<Fast>()
        val skipped = mutableMapOf<HistorySkip, Int>()
        var count = 0
        for (row in rows) {
            if (++count > MAX_ROWS) throw HistoryFileException()
            fun field(name: String) = positions[name]?.let(row::getOrNull)?.trim().orEmpty()
            val reason = when {
                row.size != columns.size -> HistorySkip.INVALID
                isZero -> HistorySkip.LEGACY_ZERO
                field("ended_at").isEmpty() -> HistorySkip.UNFINISHED
                field("target_end_at").isEmpty() -> HistorySkip.NO_GOAL
                else -> {
                    val fast = try {
                        val start = timestamp(field("start_at"))
                        val end = timestamp(field("ended_at"))
                        val target = timestamp(field("target_end_at"))
                        require(start <= end && end <= now)
                        Fast(
                            startAt = start,
                            targetEndAt = target,
                            protocolId = field("protocol_id").ifEmpty { "imported" },
                            kind = field("kind").takeIf { it.isNotEmpty() }?.let(FastKind::valueOf) ?: FastKind.REGULAR,
                        ).end(end)
                    } catch (e: RuntimeException) {
                        null
                    }
                    if (fast == null) HistorySkip.INVALID else {
                        found.add(fast)
                        null
                    }
                }
            }
            if (reason != null) skipped[reason] = skipped.getOrDefault(reason, 0) + 1
        }
        return HistoryBatch(found, skipped)
    }

    // Room persists milliseconds. Reject finer timestamps instead of silently changing a period or goal.
    private fun timestamp(value: String): Instant = Instant.parse(value).also {
        require(Instant.ofEpochMilli(it.toEpochMilli()) == it)
    }

    /** Handles the quoted notes, escaped quotes and multiline fields in existing fasts.csv exports. */
    private fun csvRows(text: String): Sequence<List<String>> = sequence {
        val field = StringBuilder()
        var row = mutableListOf<String>()
        var quoted = false
        var closedQuote = false
        var i = 0
        while (i < text.length) {
            val c = text[i++]
            when {
                quoted && c == '"' && text.getOrNull(i) == '"' -> { field.append('"'); i++ }
                quoted && c == '"' -> { quoted = false; closedQuote = true }
                quoted -> field.append(c)
                c == ',' -> {
                    if (row.size >= 64) throw HistoryFileException()
                    row.add(field.toString())
                    field.setLength(0)
                    closedQuote = false
                }
                c == '\r' || c == '\n' -> {
                    if (c == '\r' && text.getOrNull(i) == '\n') i++
                    if (row.isNotEmpty() || field.isNotEmpty() || closedQuote) {
                        row.add(field.toString())
                        yield(row)
                    }
                    row = mutableListOf()
                    field.setLength(0)
                    closedQuote = false
                }
                closedQuote -> throw HistoryFileException()
                c == '"' && field.isEmpty() -> quoted = true
                c == '"' -> throw HistoryFileException()
                else -> field.append(c)
            }
        }
        if (quoted) throw HistoryFileException()
        if (row.isNotEmpty() || field.isNotEmpty() || closedQuote) {
            row.add(field.toString())
            yield(row)
        }
    }
}

/** Existing records always win. In-file conflicts keep the earliest non-overlapping candidate. */
fun HistoryBatch.against(existing: List<Fast>): HistoryBatch {
    val sorted = existing.filter { it.startAt != it.endedAt }.sortedBy { it.startAt }
    val maxEnds = ArrayList<Instant>(sorted.size)
    sorted.forEach { maxEnds.add(maxOf(it.endedAt ?: Instant.MAX, maxEnds.lastOrNull() ?: Instant.MIN)) }
    val times = existing.mapTo(mutableSetOf()) { Triple(it.startAt, it.targetEndAt, it.endedAt) }
    val accepted = mutableListOf<Fast>()
    val counts = skipped.toMutableMap()
    for (fast in fasts.sortedBy { it.startAt }) {
        val end = requireNotNull(fast.endedAt)
        // The last existing start strictly before this end. Half-open intervals may touch.
        var lo = 0
        var hi = sorted.size
        while (lo < hi) {
            val mid = (lo + hi) ushr 1
            if (sorted[mid].startAt < end) lo = mid + 1 else hi = mid
        }
        val overlapsStored = lo > 0 && maxEnds[lo - 1] > fast.startAt
        val overlapsNew = accepted.lastOrNull()?.endedAt?.isAfter(fast.startAt) == true
        val reason = when {
            Triple(fast.startAt, fast.targetEndAt, end) in times -> HistorySkip.DUPLICATE
            overlapsStored || overlapsNew -> HistorySkip.OVERLAP
            else -> null
        }
        if (reason == null) {
            times.add(Triple(fast.startAt, fast.targetEndAt, end))
            accepted.add(fast)
        } else counts[reason] = counts.getOrDefault(reason, 0) + 1
    }
    return HistoryBatch(accepted, counts)
}
