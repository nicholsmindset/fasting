package com.fastingshift.app.share

import android.content.ClipData
import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import java.io.File

/**
 * Hands a file the user chose to share to the Android share sheet. Files live in
 * cache/shared/ (see res/xml/shared_files.xml) and are readable only by the app the user
 * picks, through a temporary content:// grant. Nothing is uploaded by Fasting Shift itself.
 */
object FileSharing {
    const val DIR = "shared"

    fun authority(context: Context) = "${context.packageName}.files"

    /** A file in the shareable cache folder. Shared files older than a day are cleared first. */
    fun newFile(context: Context, name: String): File {
        purgeOld(context)
        return File(File(context.cacheDir, DIR).apply { mkdirs() }, name)
    }

    /** Deletes shared files older than a day; also run at app start so reports don't linger. */
    fun purgeOld(context: Context) {
        val cutoff = System.currentTimeMillis() - KEEP_MS
        File(context.cacheDir, DIR).listFiles()?.filter { it.lastModified() < cutoff }?.forEach { it.delete() }
    }

    private const val KEEP_MS = 24L * 60 * 60 * 1000

    fun intent(context: Context, file: File, mime: String, text: String? = null, subject: String? = null): Intent {
        val uri = FileProvider.getUriForFile(context, authority(context), file)
        val send = Intent(Intent.ACTION_SEND)
            .setType(mime)
            .putExtra(Intent.EXTRA_STREAM, uri)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        send.clipData = ClipData.newRawUri(file.name, uri)
        text?.let { send.putExtra(Intent.EXTRA_TEXT, it) }
        subject?.let { send.putExtra(Intent.EXTRA_SUBJECT, it) }
        return Intent.createChooser(send, null)
    }

    /** Opens the share sheet. Never crashes if no app can receive it. */
    fun share(context: Context, file: File, mime: String, text: String? = null, subject: String? = null) {
        runCatching { context.startActivity(intent(context, file, mime, text, subject)) }
    }
}
