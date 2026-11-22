package com.fastingshift.domain.backup

import com.fastingshift.domain.model.Fast
import java.time.Instant
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull

/** Dispatch on file contents. Some Storage Access Framework providers supply a generic MIME type. */
object HistoryImport {
    fun parse(text: String, now: Instant): HistoryBatch {
        val source = text.removePrefix("\uFEFF").trimStart()
        if (!source.startsWith('{')) return HistoryCsv.parse(text, now)
        val document = try {
            Json.parseToJsonElement(source) as? JsonObject
        } catch (e: SerializationException) {
            null
        } ?: throw HistoryFileException()
        return parseZero(document, now)
    }

    // Shapes documented by the public Lightly importer and its fabricated test cases at
    // KingTBO/Lightly-Fasting-App-Project@bb33a164 (js/import.js and tests/import.test.js).
    private fun parseZero(document: JsonObject, now: Instant): HistoryBatch {
        val firestore = document["fasts"] as? JsonArray
        val biodata = document["fast_data"] as? JsonArray
        // A full Fasting Shift backup also has a "fasts" list. Its rows have no "data" wrapper.
        val wrapped = firestore?.any { (it as? JsonObject)?.get("data") is JsonObject } == true
        val records = (if (wrapped) firestore else biodata) ?: throw HistoryFileException()
        if (records.size > HistoryCsv.MAX_ROWS) throw HistoryFileException()
        val accepted = mutableListOf<Fast>()
        val counts = mutableMapOf<HistorySkip, Int>()
        for (item in records) {
            val row = item as? JsonObject
            val fields = if (wrapped) row?.get("data") as? JsonObject else row
            val endFlag = fields?.get(if (wrapped) "isEnded" else "IsEnded")
            val start = fields?.get(if (wrapped) "start" else "StartDTM")
            val end = if (wrapped) fields?.get("endWithSeconds").takeUnless { it == JsonNull } ?: fields?.get("end") else fields?.get("EndDTM")
            val goal = if (wrapped) (fields?.get("goal") as? JsonObject)?.get("hours") else fields?.get("GoalHours")
            val reason = when {
                fields == null -> HistorySkip.INVALID
                endFlag != null && (endFlag !is JsonPrimitive || endFlag.isString || endFlag.booleanOrNull == null) -> HistorySkip.INVALID
                (endFlag as? JsonPrimitive)?.booleanOrNull == false || end == null || end == JsonNull -> HistorySkip.UNFINISHED
                goal == null || goal == JsonNull -> HistorySkip.NO_GOAL
                else -> {
                    val fast = try {
                        val from = instant(start as? JsonPrimitive ?: throw IllegalArgumentException())
                        val to = instant(end as? JsonPrimitive ?: throw IllegalArgumentException())
                        require(from <= to && to <= now)
                        val goalText = (goal as? JsonPrimitive)?.content ?: throw IllegalArgumentException()
                        // A finite decimal number of hours. Bound precision before doing decimal arithmetic.
                        require(goalText.length <= 20 && goalText.all { it in '0'..'9' || it == '.' })
                        val goalMs = goalText.toBigDecimal().multiply(3_600_000.toBigDecimal()).longValueExact()
                        val target = Instant.ofEpochMilli(Math.addExact(from.toEpochMilli(), goalMs))
                        Fast(startAt = from, targetEndAt = target, protocolId = "Zero").end(to)
                    } catch (e: RuntimeException) {
                        null
                    }
                    if (fast == null) HistorySkip.INVALID else {
                        accepted.add(fast)
                        null
                    }
                }
            }
            if (reason != null) counts[reason] = counts.getOrDefault(reason, 0) + 1
        }
        return HistoryBatch(accepted, counts)
    }

    private fun instant(value: JsonPrimitive): Instant {
        val time = value.content
        if (time.all { it in '0'..'9' }) {
            return when (time.length) {
                10 -> Instant.ofEpochSecond(time.toLong())
                13 -> Instant.ofEpochMilli(time.toLong())
                else -> throw IllegalArgumentException()
            }
        }
        // ISO timestamps require an explicit UTC offset. Never assign the device's current time zone.
        val iso = if (time.getOrNull(10) == ' ') time.replaceRange(10, 11, "T") else time
        return Instant.parse(iso).also { require(Instant.ofEpochMilli(it.toEpochMilli()) == it) }
    }
}
