package com.fastingshift.app

import android.graphics.pdf.PdfDocument
import org.robolectric.annotation.Implementation
import org.robolectric.annotation.Implements

/**
 * Robolectric has no PdfDocument backend: give it non-zero native handles so the real page
 * bookkeeping (start/finish page, page list) runs. Drawing goes to Robolectric's legacy canvas.
 */
@Implements(PdfDocument::class)
class ShadowPdfDocument {
    @Implementation
    protected fun nativeCreateDocument(): Long = 1L

    companion object {
        @JvmStatic
        @Implementation
        protected fun nativeStartPage(
            nativeDocument: Long,
            pageWidth: Int,
            pageHeight: Int,
            contentLeft: Int,
            contentTop: Int,
            contentRight: Int,
            contentBottom: Int,
        ): Long {
            // Back each page with a real (Robolectric legacy) canvas so drawing calls resolve.
            val canvas = android.graphics.Canvas()
            pageCanvases += canvas
            return android.graphics.Canvas::class.java.getMethod("getNativeCanvasWrapper").invoke(canvas) as Long
        }

        private val pageCanvases = mutableListOf<android.graphics.Canvas>()
    }
}
