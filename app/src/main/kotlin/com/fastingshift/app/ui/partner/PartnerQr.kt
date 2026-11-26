package com.fastingshift.app.ui.partner

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.fastingshift.app.R
import com.google.zxing.BarcodeFormat
import com.google.zxing.common.BitMatrix
import com.google.zxing.qrcode.QRCodeWriter
import java.time.Duration
import java.time.Instant
import kotlinx.coroutines.delay

private fun PartnerWeek.expiry(): Instant? = inviteExpiresAt?.let {
    runCatching { Instant.parse(it) }.getOrNull()
}

internal fun PartnerWeek.shareableInviteLink(now: Instant): String? {
    if (status != "pending" || partner != null || expiry()?.isAfter(now) != true) return null
    return inviteCode?.takeIf(PartnerLinks::valid)?.let(PartnerLinks::link)
}

/** Recheck on resume and at the expiry boundary, even if the host has lost connectivity. */
@Composable
internal fun rememberShareableInvite(week: PartnerWeek): String? {
    var link by remember(week) { mutableStateOf(week.shareableInviteLink(Instant.now())) }
    val owner = LocalLifecycleOwner.current
    LaunchedEffect(week, owner) {
        owner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            val now = Instant.now()
            link = week.shareableInviteLink(now)
            val expires = week.expiry()
            if (link != null && expires != null) {
                delay(Duration.between(now, expires).toMillis().coerceAtLeast(1L) + 1L)
                link = week.shareableInviteLink(Instant.now())
            }
        }
    }
    return link
}

/** ZXing returns the minimum module grid, including the standard four-module white quiet zone. */
internal fun partnerQrModules(link: String): BitMatrix =
    QRCodeWriter().encode(link, BarcodeFormat.QR_CODE, 0, 0)

@Composable
internal fun PartnerQrDialog(link: String, onDismiss: () -> Unit) {
    val modules = remember(link) { partnerQrModules(link) }
    val label = stringResource(R.string.partner_qr_title)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(label) },
        text = {
            Column(
                Modifier.verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Canvas(Modifier.widthIn(max = 280.dp).fillMaxWidth().aspectRatio(1f)
                    .testTag("partner_qr_image")
                    .semantics { role = Role.Image; contentDescription = label }) {
                    drawRect(Color.White)
                    val unit = (size.minDimension / modules.width).toInt()
                    val left = ((size.width - unit * modules.width) / 2).toInt()
                    val top = ((size.height - unit * modules.height) / 2).toInt()
                    val square = Size(unit.toFloat(), unit.toFloat())
                    for (y in 0 until modules.height) {
                        for (x in 0 until modules.width) {
                            if (modules[x, y]) drawRect(Color.Black,
                                Offset((left + x * unit).toFloat(), (top + y * unit).toFloat()), square)
                        }
                    }
                }
                Text(stringResource(R.string.partner_qr_instruction))
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.partner_dismiss)) }
        },
    )
}
