package org.lightfire.vteme.component.upload

import android.os.Handler
import android.os.Looper
import okhttp3.MediaType
import okhttp3.RequestBody
import okio.*

internal class ProgressRequestBody(
    private val delegate: RequestBody,
    private val callback: ProgressCallback,
) : RequestBody() {
    override fun contentType(): MediaType? = delegate.contentType()
    override fun contentLength(): Long = delegate.contentLength()

    override fun writeTo(sink: BufferedSink) {
        val countingSink = CountingSink(sink).buffer()
        delegate.writeTo(countingSink)
        countingSink.flush()
    }

    private inner class CountingSink(delegate: Sink) : ForwardingSink(delegate) {
        private val handler = Handler(Looper.getMainLooper())
        private val total = contentLength()
        private var uploaded = 0L

        override fun write(source: Buffer, byteCount: Long) {
            super.write(source, byteCount)
            uploaded += byteCount

            handler.post { callback.onProgress(uploaded, total) }
        }
    }
}

interface ProgressCallback {
    fun onProgress(bytesUploaded: Long, totalBytes: Long)
}