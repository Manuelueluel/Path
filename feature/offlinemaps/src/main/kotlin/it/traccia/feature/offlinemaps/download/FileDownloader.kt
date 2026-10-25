package it.traccia.feature.offlinemaps.download

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.security.MessageDigest

/** HTTP error that retrying will not fix (e.g. the file was removed from the server). */
class PermanentDownloadException(
    message: String,
) : IOException(message)

/**
 * Downloads large files into a `.part` file, resuming from where it stopped (HTTP Range).
 * Cancelling the coroutine stops the transfer and keeps the partial file: that is how pause works.
 */
class FileDownloader(
    private val client: OkHttpClient,
) {
    /**
     * @param onProgress called with the bytes of this file written so far.
     * @throws IOException on network errors, [PermanentDownloadException] when retrying is pointless.
     */
    suspend fun download(
        url: String,
        part: File,
        expectedSize: Long,
        onProgress: suspend (Long) -> Unit,
    ) = withContext(Dispatchers.IO) {
        part.parentFile?.mkdirs()
        var existing = if (part.exists()) part.length() else 0L
        if (existing > expectedSize) {
            part.delete()
            existing = 0
        }
        if (existing == expectedSize) return@withContext
        val request =
            Request
                .Builder()
                .url(url)
                .apply { if (existing > 0) header("Range", "bytes=$existing-") }
                .build()
        client.newCall(request).execute().use { response ->
            val append = isResumed(response, url)
            val written = copy(response.body.byteStream(), part, append, if (append) existing else 0L, onProgress)
            if (written != expectedSize) throw IOException("Incomplete download: $written of $expectedSize bytes")
        }
    }

    /** True when the server honoured the range (append), false when it sent the whole file. */
    private fun isResumed(
        response: Response,
        url: String,
    ): Boolean =
        when {
            response.code == HTTP_PARTIAL -> true
            response.isSuccessful -> false
            response.code in PERMANENT_ERRORS -> throw PermanentDownloadException("HTTP ${response.code} for $url")
            else -> throw IOException("HTTP ${response.code} for $url")
        }

    private suspend fun copy(
        source: InputStream,
        part: File,
        append: Boolean,
        startAt: Long,
        onProgress: suspend (Long) -> Unit,
    ): Long {
        var written = startAt
        FileOutputStream(part, append).use { out ->
            val buffer = ByteArray(BUFFER_SIZE)
            while (true) {
                currentCoroutineContext().ensureActive()
                val read = source.read(buffer)
                if (read < 0) break
                out.write(buffer, 0, read)
                written += read
                onProgress(written)
            }
        }
        return written
    }

    private companion object {
        const val HTTP_PARTIAL = 206
        val PERMANENT_ERRORS = setOf(403, 404, 410)
        const val BUFFER_SIZE = 64 * 1024
    }
}

/** SHA-256 of a file as lowercase hex. */
fun File.sha256(): String {
    val digest = MessageDigest.getInstance("SHA-256")
    inputStream().use { input ->
        val buffer = ByteArray(1 shl 16)
        while (true) {
            val read = input.read(buffer)
            if (read < 0) break
            digest.update(buffer, 0, read)
        }
    }
    return digest.digest().joinToString("") { "%02x".format(it) }
}
