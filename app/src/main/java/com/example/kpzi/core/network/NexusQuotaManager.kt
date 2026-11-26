package com.example.kpzi.core.network

import com.example.kpzi.domain.model.ApiQuotaState
import com.example.kpzi.domain.model.RateLimitStatus
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import okhttp3.Headers
import okhttp3.Response
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.min

/**
 * Manages Nexus Mods API rate limits strictly from actual HTTP response headers.
 * Implements request queueing, in-flight deduplication, HTTP 429 Retry-After handling,
 * and exponential backoff.
 */
class NexusQuotaManager {

    private val _quotaState = MutableStateFlow(ApiQuotaState())
    val quotaState: StateFlow<ApiQuotaState> = _quotaState.asStateFlow()

    private val concurrencySemaphore = Semaphore(permits = 3)
    private val inFlightRequests = ConcurrentHashMap<String, Deferred<Any?>>()

    fun updateFromHeaders(headers: Headers, httpCode: Int) {
        val dailyLimit = headers["X-RL-Daily-Limit"]?.toIntOrNull()
        val dailyRemaining = headers["X-RL-Daily-Remaining"]?.toIntOrNull()
        val dailyReset = headers["X-RL-Daily-Reset"]
        val hourlyLimit = headers["X-RL-Hourly-Limit"]?.toIntOrNull()
        val hourlyRemaining = headers["X-RL-Hourly-Remaining"]?.toIntOrNull()
        val hourlyReset = headers["X-RL-Hourly-Reset"]
        val retryAfter = headers["Retry-After"]?.toIntOrNull()

        val prev = _quotaState.value
        val resolvedDailyLimit = dailyLimit ?: prev.dailyLimit
        val resolvedDailyRemaining = dailyRemaining ?: prev.dailyRemaining
        val resolvedHourlyLimit = hourlyLimit ?: prev.hourlyLimit
        val resolvedHourlyRemaining = hourlyRemaining ?: prev.hourlyRemaining

        val computedStatus = when {
            httpCode == 429 -> RateLimitStatus.RATE_LIMITED
            resolvedDailyRemaining == 0 || resolvedHourlyRemaining == 0 -> RateLimitStatus.EXHAUSTED
            (resolvedHourlyRemaining != null && resolvedHourlyLimit != null && resolvedHourlyLimit > 0 &&
                resolvedHourlyRemaining <= (resolvedHourlyLimit * 0.15).toInt()) ||
                (resolvedDailyRemaining != null && resolvedDailyLimit != null && resolvedDailyLimit > 0 &&
                    resolvedDailyRemaining <= (resolvedDailyLimit * 0.10).toInt()) -> RateLimitStatus.LOW
            resolvedDailyLimit != null || resolvedHourlyLimit != null -> RateLimitStatus.HEALTHY
            else -> prev.status
        }

        _quotaState.value = ApiQuotaState(
            dailyLimit = resolvedDailyLimit,
            dailyRemaining = resolvedDailyRemaining,
            dailyReset = dailyReset ?: prev.dailyReset,
            hourlyLimit = resolvedHourlyLimit,
            hourlyRemaining = resolvedHourlyRemaining,
            hourlyReset = hourlyReset ?: prev.hourlyReset,
            retryAfterSeconds = if (httpCode == 429) retryAfter else null,
            lastUpdatedEpochMs = System.currentTimeMillis(),
            status = computedStatus,
        )
    }

    fun clearQuotaState() {
        _quotaState.value = ApiQuotaState()
    }

    @Suppress("UNCHECKED_CAST")
    suspend fun <T> executeDeduplicated(
        dedupKey: String,
        maxRetries: Int = 2,
        block: suspend () -> T,
    ): T {
        val existing = inFlightRequests[dedupKey] as? Deferred<T>
        if (existing != null && existing.isActive) {
            return existing.await()
        }

        val deferred = CompletableDeferred<Any?>()
        val raced = inFlightRequests.putIfAbsent(dedupKey, deferred) as? Deferred<T>
        if (raced != null && raced.isActive) {
            return raced.await()
        }

        return try {
            val result = concurrencySemaphore.withPermit {
                executeWithBackoff(maxRetries = maxRetries, block = block)
            }
            deferred.complete(result)
            result
        } catch (t: Throwable) {
            deferred.completeExceptionally(t)
            throw t
        } finally {
            inFlightRequests.remove(dedupKey, deferred)
        }
    }

    private suspend fun <T> executeWithBackoff(
        maxRetries: Int,
        block: suspend () -> T,
    ): T {
        var attempt = 0
        var backoffMs = 800L
        while (true) {
            try {
                return block()
            } catch (e: RateLimitedException) {
                if (attempt >= maxRetries) throw e
                val waitMs = ((e.retryAfterSeconds ?: 2) * 1000L).coerceAtMost(12_000L)
                delay(waitMs)
            } catch (e: IOException) {
                if (attempt >= maxRetries) throw e
                delay(backoffMs)
                backoffMs = min(backoffMs * 2, 8_000L)
            }
            attempt++
        }
    }

    fun inspectResponseOrThrow(response: Response) {
        updateFromHeaders(response.headers, response.code)
        if (response.code == 429) {
            val retryAfter = response.header("Retry-After")?.toIntOrNull()
            throw RateLimitedException(retryAfter)
        }
    }
}

class RateLimitedException(val retryAfterSeconds: Int?) :
    IOException("Nexus API rate limited (HTTP 429)${retryAfterSeconds?.let { ", retry after ${it}s" } ?: ""}")

class NexusApiException(val httpCode: Int, message: String) : IOException(message)
