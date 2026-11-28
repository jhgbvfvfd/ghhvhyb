package com.example.kpzi.notifications

data class PlaytimeBreakdown(
    val months: Long,
    val days: Long,
    val hours: Long,
    val minutes: Long,
    val seconds: Long,
)

object PlaytimeFormatter {
    private const val SECONDS_PER_MINUTE = 60L
    private const val SECONDS_PER_HOUR = 3_600L
    private const val SECONDS_PER_DAY = 86_400L
    private const val SECONDS_PER_MONTH = 30L * SECONDS_PER_DAY

    fun breakdown(totalSeconds: Long): PlaytimeBreakdown {
        val safe = totalSeconds.coerceAtLeast(0L)
        val months = safe / SECONDS_PER_MONTH
        val remAfterMonths = safe % SECONDS_PER_MONTH
        val days = remAfterMonths / SECONDS_PER_DAY
        val remAfterDays = remAfterMonths % SECONDS_PER_DAY
        val hours = remAfterDays / SECONDS_PER_HOUR
        val remAfterHours = remAfterDays % SECONDS_PER_HOUR
        val minutes = remAfterHours / SECONDS_PER_MINUTE
        val seconds = remAfterHours % SECONDS_PER_MINUTE
        return PlaytimeBreakdown(
            months = months,
            days = days,
            hours = hours,
            minutes = minutes,
            seconds = seconds,
        )
    }

    fun formatThaiBreakdown(totalSeconds: Long, includeSeconds: Boolean = false): String {
        val b = breakdown(totalSeconds)
        return buildString {
            append("${b.months} เดือน ")
            append("${b.days} วัน ")
            append("${b.hours} ชม. ")
            append("${b.minutes} นาที")
            if (includeSeconds) {
                append(" ${b.seconds} วิ")
            }
        }
    }

    fun formatBreakdown(
        totalSeconds: Long,
        includeSeconds: Boolean = false,
        isEnglish: Boolean = false,
    ): String {
        if (!isEnglish) return formatThaiBreakdown(totalSeconds, includeSeconds)
        val b = breakdown(totalSeconds)
        return buildString {
            append("${b.months} mo ")
            append("${b.days}d ")
            append("${b.hours}h ")
            append("${b.minutes}m")
            if (includeSeconds) {
                append(" ${b.seconds}s")
            }
        }
    }

    fun formatCompactThai(totalSeconds: Long): String {
        val b = breakdown(totalSeconds)
        val parts = mutableListOf<String>()
        if (b.months > 0) parts.add("${b.months} เดือน")
        if (b.days > 0 || b.months > 0) parts.add("${b.days} วัน")
        if (b.hours > 0 || parts.isNotEmpty()) parts.add("${b.hours} ชม.")
        parts.add("${b.minutes} นาที")
        return parts.joinToString(" ")
    }

    fun formatCompact(totalSeconds: Long, isEnglish: Boolean = false): String {
        if (!isEnglish) return formatCompactThai(totalSeconds)
        val b = breakdown(totalSeconds)
        val parts = mutableListOf<String>()
        if (b.months > 0) parts.add("${b.months} mo")
        if (b.days > 0 || b.months > 0) parts.add("${b.days}d")
        if (b.hours > 0 || parts.isNotEmpty()) parts.add("${b.hours}h")
        parts.add("${b.minutes}m")
        return parts.joinToString(" ")
    }

    fun formatStopwatch(totalSeconds: Long): String {
        val safe = totalSeconds.coerceAtLeast(0L)
        val hours = safe / SECONDS_PER_HOUR
        val minutes = (safe % SECONDS_PER_HOUR) / SECONDS_PER_MINUTE
        val seconds = safe % SECONDS_PER_MINUTE
        return String.format("%02d:%02d:%02d", hours, minutes, seconds)
    }
}
