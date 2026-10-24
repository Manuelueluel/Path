package it.traccia.core.common

import kotlinx.coroutines.CancellationException
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class AppResultTest {
    @Test
    fun `map transforms success value`() {
        val result: AppResult<Int> = AppResult.Success(2)
        assertEquals(AppResult.Success(4), result.map { it * 2 })
    }

    @Test
    fun `map keeps failure untouched`() {
        val failure = AppResult.Failure(AppError.Storage())
        assertEquals(failure, (failure as AppResult<Int>).map { it * 2 })
    }

    @Test
    fun `getOrNull returns null on failure`() {
        assertNull(AppResult.Failure(AppError.Network()).getOrNull())
    }

    @Test
    fun `catching wraps exceptions into failures`() {
        val result = catching(toError = { AppError.Storage(it) }) { error("boom") }
        assertTrue(result is AppResult.Failure && result.error is AppError.Storage)
    }

    @Test
    fun `catching rethrows cancellation`() {
        assertThrows<CancellationException> { catching { throw CancellationException("stop") } }
    }
}
