package it.traccia.feature.gpx

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.core.content.FileProvider
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import javax.inject.Inject

/** File access for GPX import and export (content URIs, share cache). */
class GpxFiles
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
    ) {
        suspend fun read(uri: Uri): GpxDocument =
            withContext(Dispatchers.IO) {
                val input = context.contentResolver.openInputStream(uri) ?: throw IOException("Cannot open $uri")
                input.use(GpxParser::parse)
            }

        /** Display name of a picked or shared file, without the extension. */
        suspend fun displayName(uri: Uri): String? =
            withContext(Dispatchers.IO) {
                context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                    if (cursor.moveToFirst()) cursor.getString(0)?.substringBeforeLast('.') else null
                }
            }

        suspend fun write(
            uri: Uri,
            text: String,
        ) = withContext(Dispatchers.IO) {
            val output = context.contentResolver.openOutputStream(uri, "wt") ?: throw IOException("Cannot write $uri")
            output.use { it.write(text.toByteArray()) }
        }

        /** Writes [text] in the share cache and returns a content URI other apps can read. */
        suspend fun shareable(
            fileName: String,
            text: String,
        ): Uri =
            withContext(Dispatchers.IO) {
                val dir = File(context.cacheDir, "exports").apply { mkdirs() }
                // Only the latest export is kept.
                dir.listFiles()?.forEach { it.delete() }
                val file = File(dir, fileName).apply { writeText(text) }
                FileProvider.getUriForFile(context, "${context.packageName}.gpx", file)
            }
    }
