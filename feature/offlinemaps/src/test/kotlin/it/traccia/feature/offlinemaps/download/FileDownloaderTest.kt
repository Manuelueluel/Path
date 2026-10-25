package it.traccia.feature.offlinemaps.download

import kotlinx.coroutines.test.runTest
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.OkHttpClient
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.io.IOException

class FileDownloaderTest {
    private val server = MockWebServer()
    private val downloader = FileDownloader(OkHttpClient())

    @TempDir
    lateinit var dir: File

    @BeforeEach
    fun setUp() = server.start()

    @AfterEach
    fun tearDown() = server.close()

    private val content = "0123456789".repeat(100)

    private fun url() = server.url("/region.mbtiles").toString()

    @Test
    fun `downloads a whole file`() =
        runTest {
            server.enqueue(MockResponse.Builder().body(content).build())
            val part = File(dir, "a.part")
            var last = 0L
            downloader.download(url(), part, content.length.toLong()) { last = it }
            assertEquals(content, part.readText())
            assertEquals(content.length.toLong(), last)
        }

    @Test
    fun `resumes from the partial file with a range request`() =
        runTest {
            val part = File(dir, "b.part").apply { writeText(content.take(400)) }
            server.enqueue(
                MockResponse
                    .Builder()
                    .code(206)
                    .body(content.drop(400))
                    .build(),
            )
            downloader.download(url(), part, content.length.toLong()) {}
            assertEquals(content, part.readText())
            assertEquals("bytes=400-", server.takeRequest().headers["Range"])
        }

    @Test
    fun `restarts when the server ignores the range`() =
        runTest {
            val part = File(dir, "c.part").apply { writeText("garbage") }
            server.enqueue(
                MockResponse
                    .Builder()
                    .code(200)
                    .body(content)
                    .build(),
            )
            downloader.download(url(), part, content.length.toLong()) {}
            assertEquals(content, part.readText())
        }

    @Test
    fun `complete partial file needs no request`() =
        runTest {
            val part = File(dir, "d.part").apply { writeText(content) }
            downloader.download(url(), part, content.length.toLong()) {}
            assertEquals(0, server.requestCount)
        }

    @Test
    fun `missing file is a permanent error`() =
        runTest {
            server.enqueue(MockResponse.Builder().code(404).build())
            assertThrows<PermanentDownloadException> { downloader.download(url(), File(dir, "e.part"), 10) {} }
        }

    @Test
    fun `server errors can be retried`() =
        runTest {
            server.enqueue(MockResponse.Builder().code(503).build())
            val e = assertThrows<IOException> { downloader.download(url(), File(dir, "f.part"), 10) {} }
            assertFalse(e is PermanentDownloadException)
        }

    @Test
    fun `truncated transfer is reported`() =
        runTest {
            server.enqueue(MockResponse.Builder().body(content.take(10)).build())
            assertThrows<IOException> { downloader.download(url(), File(dir, "g.part"), content.length.toLong()) {} }
        }

    @Test
    fun `sha256 of a known string`() {
        val file = File(dir, "h").apply { writeText("abc") }
        assertEquals("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad", file.sha256())
    }
}
