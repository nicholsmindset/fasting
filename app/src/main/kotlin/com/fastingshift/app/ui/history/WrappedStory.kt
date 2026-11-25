package com.fastingshift.app.ui.history

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.fastingshift.app.R
import com.fastingshift.app.share.FileSharing
import com.fastingshift.app.share.ShareCards
import com.fastingshift.domain.history.AnnualRecap
import com.fastingshift.domain.history.SundayRecap
import java.text.NumberFormat
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val Forest = Color(0xFF071F1C)
private val Cream = Color(0xFFF2EDE4)
private val Mint = Color(0xFF83DAB8)
private data class Slide(val label: String, val value: String, val note: String)

@Composable
internal fun WrappedEntry(year: Int, onOpen: () -> Unit) {
    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Forest)) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.wrapped_title), style = MaterialTheme.typography.headlineMedium, color = Cream, fontFamily = FontFamily.Serif)
            Text(stringResource(R.string.wrapped_intro), color = Cream)
            Button(onClick = onOpen, colors = ButtonDefaults.buttonColors(containerColor = Mint, contentColor = Forest)) {
                Text(stringResource(R.string.wrapped_open, year))
            }
        }
    }
}

@Composable
internal fun AnnualStory(recap: AnnualRecap, onSeen: () -> Unit, onShared: () -> Unit, onClose: () -> Unit) {
    val locale = LocalConfiguration.current.locales[0]
    val res = LocalContext.current.resources
    val dates = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(locale)
    val count = NumberFormat.getIntegerInstance(locale)
    val plan = recap.topWindow
    val cards = listOf(
        Slide(stringResource(R.string.wrapped_finishes), count.format(recap.completed), stringResource(R.string.wrapped_finishes_note)),
        Slide(stringResource(R.string.wrapped_longest), ShareCards.lengthText(res, recap.longestSession), stringResource(R.string.wrapped_longest_note)),
        Slide(stringResource(R.string.wrapped_protocol), plan ?: stringResource(if (recap.personalPlan) R.string.wrapped_private_plan else R.string.wrapped_varied), stringResource(R.string.wrapped_protocol_note)),
        Slide(stringResource(R.string.wrapped_streak), count.format(recap.bestPlanDays), stringResource(R.string.wrapped_streak_note)),
        Slide(stringResource(R.string.wrapped_november), NumberFormat.getPercentInstance(locale).format(recap.novemberTimerPercent / 100.0), stringResource(R.string.wrapped_november_note)),
        Slide(stringResource(R.string.wrapped_style), if (recap.repeatedWindow && plan != null) stringResource(R.string.wrapped_familiar, plan) else stringResource(R.string.wrapped_flexible), stringResource(R.string.wrapped_style_note)),
        Slide(stringResource(R.string.wrapped_return), count.format(recap.recordedDays), stringResource(R.string.wrapped_return_note)),
    )
    val pager = rememberPagerState { cards.size }
    val scope = rememberCoroutineScope()
    val period = stringResource(R.string.wrapped_annual_date, recap.year, dates.format(recap.through))
    LaunchedEffect(recap.year) { onSeen() }
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(Modifier.fillMaxSize().background(Forest).safeDrawingPadding().padding(16.dp)) {
            TextButton(onClick = onClose, modifier = Modifier.align(Alignment.End)) { Text(stringResource(R.string.wrapped_done), color = Mint) }
            HorizontalPager(state = pager, modifier = Modifier.weight(1f).fillMaxWidth()) { i ->
                Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.Center) {
                    CapturedRecap(cards[i], period, stringResource(R.string.wrapped_share), onShared)
                }
            }
            Text(stringResource(R.string.wrapped_slide_position, pager.currentPage + 1, cards.size),
                modifier = Modifier.align(Alignment.CenterHorizontally), color = Cream)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                TextButton(onClick = { scope.launch { pager.animateScrollToPage(pager.currentPage - 1) } }, enabled = pager.currentPage > 0) {
                    Text(stringResource(R.string.wrapped_back), color = if (pager.currentPage > 0) Mint else Cream.copy(alpha = .45f))
                }
                if (pager.currentPage < cards.lastIndex) TextButton(onClick = { scope.launch { pager.animateScrollToPage(pager.currentPage + 1) } }) {
                    Text(stringResource(R.string.wrapped_next), color = Mint)
                }
            }
        }
    }
}

@Composable
internal fun SundayMini(recap: SundayRecap, onSeen: () -> Unit, onShared: () -> Unit) {
    val res = LocalContext.current.resources
    val dates = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(LocalConfiguration.current.locales[0])
    val average = recap.average
    val prior = recap.previousAverage
    val difference = if (average != null && prior != null) average.minus(prior).toMinutes() else null
    val comparison = when {
        prior == null -> stringResource(R.string.wrapped_week_no_prior)
        difference == null -> stringResource(R.string.wrapped_week_prior, ShareCards.lengthText(res, prior))
        difference == 0L -> stringResource(R.string.wrapped_week_same)
        else -> stringResource(if (difference > 0) R.string.wrapped_week_up else R.string.wrapped_week_down,
            ShareCards.lengthText(res, java.time.Duration.ofMinutes(kotlin.math.abs(difference))))
    }
    val note = if (average != null) stringResource(R.string.wrapped_week_avg, ShareCards.lengthText(res, average))
        else stringResource(R.string.wrapped_week_empty)
    LaunchedEffect(recap.weekStart) { onSeen() }
    CapturedRecap(Slide(stringResource(R.string.wrapped_week), stringResource(R.string.wrapped_week_total, recap.completed),
        "$note\n$comparison"), stringResource(R.string.wrapped_week_dates, dates.format(recap.weekStart), dates.format(recap.weekStart.plusDays(6))),
        stringResource(R.string.wrapped_share_week), onShared)
}

/** Captures only the visible branded story artwork. Buttons and underlying History never enter the bitmap. */
@Composable
private fun CapturedRecap(slide: Slide, period: String, action: String, onShared: () -> Unit) {
    val layer = rememberGraphicsLayer()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Column(Modifier.fillMaxWidth().testTag("recap-artwork").drawWithContent {
            layer.record { this@drawWithContent.drawContent() }
            drawLayer(layer)
        }.background(Forest).padding(22.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(9.dp, 24.dp).background(Mint, RoundedCornerShape(2.dp)))
                Spacer(Modifier.width(10.dp))
                Text(stringResource(R.string.wrapped_brand), color = Mint, style = MaterialTheme.typography.titleSmall)
            }
            Text(period, color = Cream, style = MaterialTheme.typography.bodyMedium)
            HorizontalDivider(color = Mint.copy(alpha = .5f))
            Text(slide.label, Modifier.semantics { heading() }, color = Cream, style = MaterialTheme.typography.titleMedium)
            Text(slide.value, color = Mint, style = MaterialTheme.typography.displaySmall, fontFamily = FontFamily.Serif)
            Text(slide.note, color = Cream, style = MaterialTheme.typography.bodyLarge)
        }
        FilledTonalButton(enabled = !busy, onClick = {
            if (busy) return@FilledTonalButton
            scope.launch {
                busy = true
                failed = false
                try {
                    val bitmap = layer.toImageBitmap().asAndroidBitmap()
                    val file = withContext(Dispatchers.IO) {
                        val out = FileSharing.newFile(context, "recap-${UUID.randomUUID()}.png")
                        out.outputStream().use { check(bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)) }
                        out
                    }
                    context.startActivity(FileSharing.intent(context, file, "image/png"))
                    onShared()
                } catch (e: CancellationException) {
                    throw e
                } catch (error: Exception) {
                    android.util.Log.w("RecapShare", "Could not render or open the recap image", error)
                    failed = true
                } finally {
                    busy = false
                }
            }
        }) { Text(action) }
        if (failed) Text(stringResource(R.string.wrapped_share_error), color = MaterialTheme.colorScheme.error)
    }
}
