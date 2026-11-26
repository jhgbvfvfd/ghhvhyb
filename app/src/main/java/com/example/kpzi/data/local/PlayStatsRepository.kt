package com.example.kpzi.data.local

import kotlinx.coroutines.flow.Flow

class PlayStatsRepository(private val playSessionDao: PlaySessionDao) {

    val recentSessions: Flow<List<PlaySessionEntity>> = playSessionDao.observeRecent()

    suspend fun recordCompletedSession(
        startedAtMs: Long,
        endedAtMs: Long,
        durationSeconds: Long,
        activeModCount: Int,
        launchMode: String,
    ) {
        if (durationSeconds <= 0L) return
        playSessionDao.insert(
            PlaySessionEntity(
                startedAtMs = startedAtMs,
                endedAtMs = endedAtMs,
                durationSeconds = durationSeconds,
                activeModCount = activeModCount,
                launchMode = launchMode,
            ),
        )
    }

    suspend fun clearHistory() {
        playSessionDao.clearAll()
    }
}
