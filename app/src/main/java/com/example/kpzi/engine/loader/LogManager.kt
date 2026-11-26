package com.example.kpzi.engine.loader

import android.content.Context
import android.net.Uri
import com.example.kpzi.domain.model.LogEntry
import com.example.kpzi.domain.model.LogLevel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.atomic.AtomicLong

/**
 * Clean-room LogManager: reads SMAPI-latest.txt / crash logs and Kpzi runtime logs
 * from the local filesystem or SAF URI, parses multi-line SMAPI entries, and supports
 * search, level filtering, reload, and export.
 */
class LogManager(
    private val context: Context,
    private val assetManager: AssetManager,
) {
    private val idCounter = AtomicLong(1L)
    private val timeFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())

    private val _logs = MutableStateFlow<List<LogEntry>>(emptyList())
    val logs: StateFlow<List<LogEntry>> = _logs.asStateFlow()

    private val _activeLogSource = MutableStateFlow("kpzi-latest.log")
    val activeLogSource: StateFlow<String> = _activeLogSource.asStateFlow()

    private val _isReloading = MutableStateFlow(false)
    val isReloading: StateFlow<Boolean> = _isReloading.asStateFlow()

    private val kpziLogFile: File
        get() = File(assetManager.logsDir, "kpzi-latest.log")

    private val importedSmapiLogFile: File
        get() = File(assetManager.logsDir, "SMAPI-latest.txt")

    init {
        info("LogManager", "เริ่มต้นระบบตรวจสอบบันทึกการทำงาน Kpzi Mod Manager")
    }

    fun trace(source: String, msg: String) = append(source, LogLevel.TRACE, msg)
    fun debug(source: String, msg: String) = append(source, LogLevel.DEBUG, msg)
    fun info(source: String, msg: String) = append(source, LogLevel.INFO, msg)
    fun warn(source: String, msg: String) = append(source, LogLevel.WARN, msg)
    fun error(source: String, msg: String) = append(source, LogLevel.ERROR, msg)
    fun alert(source: String, msg: String) = append(source, LogLevel.ALERT, msg)

    private fun append(source: String, level: LogLevel, message: String) {
        val entry = LogEntry(
            id = idCounter.getAndIncrement(),
            timestamp = timeFormat.format(Date()),
            source = source,
            level = level,
            message = message,
        )
        _logs.update { current -> (listOf(entry) + current).take(MAX_IN_MEMORY_LOGS) }
        runCatching {
            kpziLogFile.appendText("[${entry.timestamp} ${entry.level.tag} ${entry.source}] ${entry.message}\n")
        }
    }

    /**
     * Reads the SMAPI log file from the local filesystem (or persisted SAF URI if configured)
     * and merges with local Kpzi logs, prioritizing SMAPI-latest.txt when present.
     */
    suspend fun reloadLocalLogs(safLogUri: String? = null): Int = withContext(Dispatchers.IO) {
        _isReloading.value = true
        try {
            // 1. Try persisted SAF URI first if user previously linked SMAPI-latest.txt
            if (!safLogUri.isNullOrBlank()) {
                val uriLines = runCatching {
                    context.contentResolver.openInputStream(Uri.parse(safLogUri))
                        ?.bufferedReader()
                        ?.use { it.readLines() }
                }.getOrNull()
                if (!uriLines.isNullOrEmpty()) {
                    val parsed = parseSmapiLines(uriLines)
                    if (parsed.isNotEmpty()) {
                        runCatching { importedSmapiLogFile.writeText(uriLines.joinToString("\n")) }
                        val merged = mergeWithKpziLog(parsed)
                        _logs.value = merged
                        _activeLogSource.value = "SMAPI-latest.txt (SAF)"
                        return@withContext parsed.size
                    }
                }
            }

            // 2. Scan candidate local filesystem locations for SMAPI logs
            val smapiFile = findLocalSmapiLogFile()
            if (smapiFile != null) {
                val lines = runCatching { smapiFile.readLines() }.getOrNull().orEmpty()
                val parsed = parseSmapiLines(lines)
                if (parsed.isNotEmpty()) {
                    val merged = mergeWithKpziLog(parsed)
                    _logs.value = merged
                    _activeLogSource.value = smapiFile.name
                    return@withContext parsed.size
                }
            }

            // 3. Fallback to Kpzi's own local log file on disk
            if (kpziLogFile.isFile && kpziLogFile.length() > 0L) {
                val kpziLines = runCatching { kpziLogFile.readLines() }.getOrNull().orEmpty()
                val parsed = parseSmapiLines(kpziLines)
                if (parsed.isNotEmpty()) {
                    _logs.value = parsed.reversed().take(MAX_IN_MEMORY_LOGS)
                    _activeLogSource.value = kpziLogFile.name
                    return@withContext parsed.size
                }
            }
            0
        } finally {
            _isReloading.value = false
        }
    }

    suspend fun importSmapiLogFromUri(uri: Uri): Int = withContext(Dispatchers.IO) {
        val lines = context.contentResolver.openInputStream(uri)?.bufferedReader()?.use {
            it.readLines()
        } ?: return@withContext 0

        val parsed = parseSmapiLines(lines)
        if (parsed.isNotEmpty()) {
            // Persist a local filesystem copy so future app opens load it immediately
            runCatching { importedSmapiLogFile.writeText(lines.joinToString("\n")) }
            _activeLogSource.value = importedSmapiLogFile.name
            val merged = mergeWithKpziLog(parsed)
            _logs.value = merged
            info("LogManager", "นำเข้าและบันทึกไฟล์ SMAPI Log สำเร็จ (${parsed.size} รายการ)")
        }
        parsed.size
    }

    fun clearLogs() {
        _logs.value = emptyList()
        runCatching { kpziLogFile.writeText("") }
        runCatching {
            if (importedSmapiLogFile.exists()) importedSmapiLogFile.delete()
        }
        _activeLogSource.value = kpziLogFile.name
        info("LogManager", "ล้างบันทึกเรียบร้อยแล้ว")
    }

    fun buildExportText(): String {
        return _logs.value.reversed().joinToString("\n") {
            "[${it.timestamp} ${it.level.tag} ${it.source}] ${it.message}"
        }
    }

    private fun mergeWithKpziLog(smapiEntries: List<LogEntry>): List<LogEntry> {
        val kpziEntries = if (kpziLogFile.isFile) {
            runCatching { parseSmapiLines(kpziLogFile.readLines()) }.getOrDefault(emptyList())
        } else {
            emptyList()
        }
        return (smapiEntries.reversed() + kpziEntries.reversed()).take(MAX_IN_MEMORY_LOGS)
    }

    private fun findLocalSmapiLogFile(): File? {
        val extRoot = context.getExternalFilesDir(null)
        val candidates = listOfNotNull(
            importedSmapiLogFile,
            File(assetManager.logsDir, "SMAPI-crash.txt"),
            extRoot?.let { File(it, "ErrorLogs/SMAPI-latest.txt") },
            extRoot?.let { File(it, "SMAPI-latest.txt") },
            File(context.filesDir, "ErrorLogs/SMAPI-latest.txt"),
            File(context.filesDir, "SMAPI-latest.txt"),
        )
        return candidates.firstOrNull { it.isFile && it.canRead() && it.length() > 0L }
    }

    fun parseSmapiLines(lines: List<String>): List<LogEntry> {
        val result = mutableListOf<LogEntry>()
        var lastEntry: LogEntry? = null
        val fallbackTime = timeFormat.format(Date())

        for (raw in lines) {
            val trimmedEnd = raw.trimEnd()
            if (trimmedEnd.isBlank()) continue
            val trimmed = trimmedEnd.trimStart()
            val match = SMAPI_LINE_REGEX.find(trimmed)
            if (match != null) {
                val time = match.groupValues[1]
                val lvl = runCatching { LogLevel.valueOf(match.groupValues[2]) }.getOrDefault(LogLevel.INFO)
                val src = match.groupValues[3].trim()
                val msg = match.groupValues[4].trim()
                val entry = LogEntry(
                    id = idCounter.getAndIncrement(),
                    timestamp = time,
                    source = src,
                    level = lvl,
                    message = msg,
                )
                result.add(entry)
                lastEntry = entry
            } else if (lastEntry != null && (raw.startsWith(" ") || raw.startsWith("\t") || trimmed.startsWith("at "))) {
                // Attach indented stack traces or continuation lines to previous entry
                val combined = "${lastEntry.message}\n$trimmed"
                val updated = lastEntry.copy(message = combined)
                result[result.lastIndex] = updated
                lastEntry = updated
            } else {
                val lvl = when {
                    trimmed.contains("error", ignoreCase = true) || trimmed.contains("exception", ignoreCase = true) -> LogLevel.ERROR
                    trimmed.contains("warn", ignoreCase = true) -> LogLevel.WARN
                    else -> LogLevel.INFO
                }
                val entry = LogEntry(
                    id = idCounter.getAndIncrement(),
                    timestamp = fallbackTime,
                    source = "SMAPI",
                    level = lvl,
                    message = trimmed,
                )
                result.add(entry)
                lastEntry = entry
            }
        }
        return result
    }

    private companion object {
        const val MAX_IN_MEMORY_LOGS = 1000
        val SMAPI_LINE_REGEX = Regex("""^\[(\d{2}:\d{2}:\d{2})\s+(TRACE|DEBUG|INFO|WARN|ERROR|ALERT)\s+([^\]]+)]\s*(.*)$""")
    }
}
