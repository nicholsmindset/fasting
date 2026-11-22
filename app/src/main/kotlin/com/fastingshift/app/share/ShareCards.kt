package com.fastingshift.app.share

import android.content.Context
import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import com.fastingshift.app.R
import com.fastingshift.app.export.kindLabelRes
import com.fastingshift.app.ui.components.hoursMinutes
import com.fastingshift.domain.history.WeeklyRecap
import com.fastingshift.domain.model.Fast
import com.fastingshift.domain.model.FastKind
import com.fastingshift.domain.protocol.Presets
import java.io.File
import java.time.Duration
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

/*
 * Share cards (moat B, "social without surveillance"): a 1080×1350 image the user can send to
 * the chat apps they already use. Drawn directly with android.graphics so it is fast, offline
 * and testable. A card only ever shows what is printed on it: no weight, symptoms, mood, notes
 * or screening answers.
 */

/** What a card shows. Kept as plain values so tests can check exactly what is shared. */
sealed interface ShareCard {
    data class FinishedFast(val length: Duration, val label: String, val date: LocalDate) : ShareCard
    data class Week(val weekStart: LocalDate, val fastingTime: Duration, val fasts: Int) : ShareCard
    data class ChallengeProgress(val title: String, val done: Int, val required: Int, val link: String) : ShareCard
}

object ShareCards {
    const val WIDTH = 1080
    const val HEIGHT = 1350
    private const val PAD = 96f

    private val bgTop = Color.rgb(9, 79, 73)
    private val bgBottom = Color.rgb(16, 122, 112)
    private val white = Color.WHITE
    private val soft = Color.argb(200, 255, 255, 255)
    private val faint = Color.argb(60, 255, 255, 255)

    /** The card for a finished fast: length, protocol or fast type, and date. */
    fun forFast(res: Resources, fast: Fast, zone: ZoneId = ZoneId.systemDefault()): ShareCard.FinishedFast {
        val label = if (fast.kind == FastKind.REGULAR) {
            Presets.byId(fast.protocolId)?.takeUnless { it.isCustom }?.name?.let { res.getString(R.string.share_fast_protocol, it) }
                ?: res.getString(R.string.share_fast_generic)
        } else {
            res.getString(kindLabelRes(fast.kind))
        }
        val end = fast.endedAt ?: fast.startAt
        return ShareCard.FinishedFast(fast.actualDuration ?: Duration.ZERO, label, end.atZone(zone).toLocalDate())
    }

    fun forWeek(recap: WeeklyRecap) = ShareCard.Week(recap.weekStart, recap.fastingTime, recap.fastsStarted)

    fun render(res: Resources, card: ShareCard): Bitmap {
        val bmp = Bitmap.createBitmap(WIDTH, HEIGHT, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        background(c)
        val locale = res.configuration.locales[0] ?: Locale.getDefault()
        val dates = DateTimeFormatter.ofLocalizedDate(FormatStyle.LONG).withLocale(locale)
        brand(c, res)
        when (card) {
            is ShareCard.FinishedFast -> {
                hero(c, res.getString(R.string.share_fast_kicker), lengthText(res, card.length), card.label)
                footerLine(c, dates.format(card.date))
            }
            is ShareCard.Week -> {
                val range = "${dates.format(card.weekStart)} – ${dates.format(card.weekStart.plusDays(6))}"
                hero(
                    c,
                    res.getString(R.string.share_week_kicker),
                    lengthText(res, card.fastingTime),
                    res.getQuantityString(R.plurals.share_week_fasts, card.fasts, card.fasts),
                )
                footerLine(c, range)
            }
            is ShareCard.ChallengeProgress -> {
                hero(c, card.title, res.getString(R.string.share_challenge_days, card.done, card.required), res.getString(R.string.share_challenge_days_label))
                progressBar(c, if (card.required == 0) 0f else card.done.toFloat() / card.required)
                footerLine(c, res.getString(R.string.share_challenge_join, card.link))
            }
        }
        return bmp
    }

    // ---------- sharing ----------

    fun shareFast(context: Context, fast: Fast) {
        val card = forFast(context.resources, fast)
        share(context, card, "fast", context.getString(R.string.share_fast_text, lengthText(context.resources, card.length), card.label))
    }

    fun shareWeek(context: Context, recap: WeeklyRecap) {
        val card = forWeek(recap)
        share(context, card, "week", context.getString(R.string.share_week_text, lengthText(context.resources, card.fastingTime)))
    }

    fun shareChallenge(context: Context, title: String, done: Int, required: Int, link: String) {
        val card = ShareCard.ChallengeProgress(title, done, required, link)
        share(context, card, "challenge", context.getString(R.string.share_challenge_text, title, link))
    }

    private fun share(context: Context, card: ShareCard, name: String, text: String) {
        val bmp = render(context.resources, card)
        val file: File = FileSharing.newFile(context, "fasting-shift-$name-${System.currentTimeMillis()}.png")
        file.outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bmp.recycle()
        FileSharing.share(context, file, "image/png", text = text)
    }

    /** "16h 20m", or "25m" under an hour. */
    fun lengthText(res: Resources, d: Duration): String =
        if (d.toHours() == 0L) res.getString(R.string.duration_m, d.toMinutes()) else d.hoursMinutes(res)

    // ---------- drawing ----------

    private fun paint(size: Float, color: Int, bold: Boolean = false) = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = size
        this.color = color
        typeface = Typeface.create(Typeface.SANS_SERIF, if (bold) Typeface.BOLD else Typeface.NORMAL)
    }

    private fun background(c: Canvas) {
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        p.shader = LinearGradient(0f, 0f, WIDTH.toFloat(), HEIGHT.toFloat(), bgTop, bgBottom, Shader.TileMode.CLAMP)
        c.drawRect(0f, 0f, WIDTH.toFloat(), HEIGHT.toFloat(), p)
        // A calm ring motif in the corner, echoing the timer.
        val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 36f; color = faint }
        c.drawCircle(WIDTH - 80f, 260f, 300f, ring)
    }

    private fun brand(c: Canvas, res: Resources) {
        c.drawText(res.getString(R.string.app_name), PAD, PAD + 40f, paint(44f, white, bold = true))
    }

    private fun layout(text: String, p: TextPaint, width: Int, maxLines: Int = 4): StaticLayout =
        StaticLayout.Builder.obtain(text, 0, text.length, p, width)
            .setAlignment(Layout.Alignment.ALIGN_NORMAL)
            .setMaxLines(maxLines)
            .setEllipsize(android.text.TextUtils.TruncateAt.END)
            .build()

    private fun draw(c: Canvas, l: StaticLayout, x: Float, y: Float) {
        c.save()
        c.translate(x, y)
        l.draw(c)
        c.restore()
    }

    /** Kicker, big value and a label, vertically centred. */
    private fun hero(c: Canvas, kicker: String, value: String, label: String) {
        val w = (WIDTH - 2 * PAD).toInt()
        val k = layout(kicker, paint(52f, soft), w, maxLines = 2)
        val v = layout(value, paint(if (value.length > 9) 150f else 190f, white, bold = true), w, maxLines = 2)
        val l = layout(label, paint(60f, white), w, maxLines = 3)
        val total = k.height + 24 + v.height + 24 + l.height
        var y = (HEIGHT - total) / 2f
        draw(c, k, PAD, y); y += k.height + 24
        draw(c, v, PAD, y); y += v.height + 24
        draw(c, l, PAD, y)
    }

    private fun progressBar(c: Canvas, fraction: Float) {
        val top = HEIGHT - 360f
        val track = RectF(PAD, top, WIDTH - PAD, top + 28f)
        c.drawRoundRect(track, 14f, 14f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = faint })
        if (fraction > 0f) {
            val fill = RectF(PAD, top, PAD + (WIDTH - 2 * PAD) * fraction.coerceIn(0f, 1f), top + 28f)
            c.drawRoundRect(fill, 14f, 14f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = white })
        }
    }

    private fun footerLine(c: Canvas, text: String) {
        val l = layout(text, paint(40f, soft), (WIDTH - 2 * PAD).toInt(), maxLines = 3)
        draw(c, l, PAD, HEIGHT - PAD - l.height)
    }
}
