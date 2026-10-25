package it.traccia.feature.offlinemaps.catalog

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import it.traccia.core.common.AppError
import it.traccia.core.common.AppResult
import it.traccia.core.common.catching
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import okhttp3.CacheControl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException
import javax.inject.Inject
import javax.inject.Qualifier
import javax.inject.Singleton

/** URL of the published catalog.json. */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class CatalogUrl

interface CatalogSource {
    /** Last known catalog (possibly from the on-disk copy), null until one was ever loaded. */
    val catalog: StateFlow<Catalog?>

    /** Downloads the catalog again; on failure the last known one stays available. */
    suspend fun refresh(): AppResult<Catalog>
}

@Singleton
class CatalogRepository
    @Inject
    constructor(
        @ApplicationContext context: Context,
        private val client: OkHttpClient,
        @param:CatalogUrl private val url: String,
    ) : CatalogSource {
        private val cacheFile = File(context.filesDir, "offline/catalog.json")
        private val _catalog = MutableStateFlow(readCached())
        override val catalog: StateFlow<Catalog?> = _catalog.asStateFlow()

        override suspend fun refresh(): AppResult<Catalog> =
            withContext(Dispatchers.IO) {
                catching(toError = ::toError) {
                    val text =
                        client
                            .newCall(
                                Request
                                    .Builder()
                                    .url(url)
                                    .cacheControl(CacheControl.FORCE_NETWORK)
                                    .build(),
                            ).execute()
                            .use { response ->
                                if (!response.isSuccessful) throw IOException("Catalog HTTP ${response.code}")
                                response.body.string()
                            }
                    val parsed = parseCatalog(text)
                    cacheFile.parentFile?.mkdirs()
                    cacheFile.writeText(text)
                    _catalog.value = parsed
                    parsed
                }
            }

        private fun readCached(): Catalog? =
            if (cacheFile.exists()) {
                catching { parseCatalog(cacheFile.readText()) }.let { (it as? AppResult.Success)?.value }
            } else {
                null
            }

        private fun toError(e: Throwable): AppError =
            when (e) {
                is IOException -> AppError.Network(e)
                is SerializationException, is IllegalArgumentException -> AppError.Unexpected(e)
                else -> AppError.Unexpected(e)
            }
    }

private val json = Json { ignoreUnknownKeys = true }

fun parseCatalog(text: String): Catalog = json.decodeFromString(Catalog.serializer(), text)
