package com.ansu.anime.core.diagnostics

import android.content.Context
import android.os.Build
import android.util.Log
import androidx.core.content.FileProvider
import com.ansu.anime.BuildConfig
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.ArrayDeque
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/** What a log line is about, so an exported report reads as a real timeline and not one wall of text. */
enum class LogCategory(val label: String) {
    APP("App"),
    NAVIGATION("Navigation"),
    CLICK("Click"),
    PLAYBACK("Playback"),
    NETWORK("Network"),
    UPDATE("Update"),
    CRASH("Crash"),
}

/** One captured log line. */
data class DiagnosticEntry(
    val millis: Long,
    val category: LogCategory,
    val message: String,
)

/**
 * Ansu's black box: every crash, playback event, tap, navigation move and network failure is written
 * here, kept both in a small in-memory ring (the current session, for a quick look) and appended to a
 * rotating file on disk so it survives a crash and a restart. Settings → System → Export logs turns all
 * of it into a single, self-contained text report the user can share.
 *
 * Nothing in this class may throw: it runs inside `Application.onCreate` and inside the uncaught
 * exception handler, and a logger that crashes the app it is supposed to diagnose is worthless.
 */
class Diagnostics(private val context: Context) {

    private val appContext = context.applicationContext

    private val logDir = File(appContext.filesDir, "diagnostics")
    private val logFile = File(logDir, "ansu-diagnostics.log")
    private val rotatedFile = File(logDir, "ansu-diagnostics.1.log")

    /** Single writer thread, so appending from many places never interleaves half a line. */
    private val writer = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "ansu-diagnostics").apply { isDaemon = true }
    }

    private val buffer = ArrayDeque<DiagnosticEntry>()

    // A fresh formatter per use: SimpleDateFormat is not thread-safe and this class is written to
    // from the single writer thread, the main thread and the crash handler at the same time.
    private fun timestampFormat() = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US)

    @Volatile
    var enabled: Boolean = true

    /** Set once the crash handler is installed, so the export can say a crash was captured. */
    @Volatile
    var lastCrashAt: Long = 0L
        private set

    fun log(category: LogCategory, message: String) = log(category, message, null)

    fun log(category: LogCategory, message: String, error: Throwable?) {
        if (!enabled && category != LogCategory.CRASH) return
        runCatching {
            val millis = System.currentTimeMillis()
            val entry = DiagnosticEntry(millis, category, message)
            synchronized(buffer) {
                buffer.addLast(entry)
                while (buffer.size > MAX_BUFFER) buffer.removeFirst()
            }
            val tag = "Anisu/${category.label}"
            if (error != null) Log.w(tag, message, error) else Log.i(tag, message)
            if (category == LogCategory.CRASH) {
                lastCrashAt = millis
                // A crash is the one line that must reach the disk even if the app is about to die.
                appendBlocking(entry, error)
            } else {
                writer.execute { appendBlocking(entry, error) }
            }
        }
    }

    /**
     * Installs the uncaught exception handler. The report is written synchronously before the previous
     * handler runs, so a crash that kills the process still ends up in the exported logs.
     */
    fun installCrashHandler() {
        runCatching {
            val previous = Thread.getDefaultUncaughtExceptionHandler()
            Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
                log(
                    LogCategory.CRASH,
                    "Uncaught ${throwable.javaClass.name} on thread \"${thread.name}\": ${throwable.message}",
                    throwable,
                )
                runCatching { writer.shutdown() }
                runCatching { writer.awaitTermination(2, TimeUnit.SECONDS) }
                previous?.uncaughtException(thread, throwable)
            }
        }
    }

    /** Current session's entries, oldest first. */
    fun sessionEntries(): List<DiagnosticEntry> = synchronized(buffer) { buffer.toList() }

    fun clear() {
        synchronized(buffer) { buffer.clear() }
        runCatching { writer.execute { logFile.delete(); rotatedFile.delete() } }
    }

    fun logSizeBytes(): Long = runCatching { logFile.length() + rotatedFile.length() }.getOrDefault(0L)

    /** Writes the whole diagnostic report into the cache and returns the file, or null when it failed. */
    fun buildExportFile(): File? = runCatching {
        val exportDir = File(appContext.cacheDir, "diagnostics")
        exportDir.mkdirs()
        val stamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())
        val file = File(exportDir, "ansu-diagnostics-$stamp.txt")
        file.writeText(report())
        file
    }.getOrNull()

    fun exportIntent(file: File) = runCatching {
        val uri = FileProvider.getUriForFile(appContext, "${appContext.packageName}.fileprovider", file)
        android.content.Intent(android.content.Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(android.content.Intent.EXTRA_SUBJECT, "Anisu diagnostics report")
            putExtra(android.content.Intent.EXTRA_STREAM, uri)
            addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }.getOrNull()

    private fun report(): String {
        val builder = StringBuilder()
        builder.append("Anisu diagnostics report\n")
        builder.append("Generated: ").append(timestampFormat().format(Date())).append('\n')
        builder.append("Report id: ").append(BuildConfig.VERSION_CODE).append('-').append(System.currentTimeMillis()).append("\n\n")

        builder.append("=== Build ===\n")
        builder.append("Package: ").append(BuildConfig.APPLICATION_ID).append('\n')
        builder.append("Version: ").append(BuildConfig.VERSION_NAME).append(" (").append(BuildConfig.VERSION_CODE).append(")\n")
        builder.append("Build type: ").append(BuildConfig.BUILD_TYPE).append('\n')
        builder.append("Debug build: ").append(BuildConfig.DEBUG).append("\n\n")

        builder.append("=== Device ===\n")
        builder.append("Manufacturer: ").append(Build.MANUFACTURER).append('\n')
        builder.append("Model: ").append(Build.MODEL).append('\n')
        builder.append("Device: ").append(Build.DEVICE).append('\n')
        builder.append("Android: ").append(Build.VERSION.RELEASE).append(" (API ").append(Build.VERSION.SDK_INT).append(")\n")
        builder.append("ABIs: ").append(Build.SUPPORTED_ABIS.joinToString(", ")).append('\n')
        builder.append("Locale: ").append(Locale.getDefault()).append('\n')
        builder.append("Time zone: ").append(java.util.TimeZone.getDefault().id).append("\n\n")

        builder.append("=== Memory ===\n")
        runCatching {
            val runtime = Runtime.getRuntime()
            builder.append("Max heap: ").append(runtime.maxMemory() / 1024 / 1024).append(" MB\n")
            builder.append("Used heap: ").append((runtime.totalMemory() - runtime.freeMemory()) / 1024 / 1024).append(" MB\n")
        }
        builder.append('\n')

        builder.append("=== Session log (").append(synchronized(buffer) { buffer.size }).append(" entries) ===\n")
        synchronized(buffer) {
            if (buffer.isEmpty()) builder.append("(no entries this session)\n")
            buffer.forEach { builder.append(formatEntry(it, null)) }
        }
        builder.append('\n')

        builder.append("=== Persisted log ===\n")
        val persisted = readLogTail()
        if (persisted.isBlank()) builder.append("(no persisted log file)\n") else builder.append(persisted)
        builder.append('\n')
        if (lastCrashAt > 0L) {
            builder.append("Last captured crash: ").append(timestampFormat().format(Date(lastCrashAt))).append('\n')
        }
        builder.append("\n=== End of report ===\n")
        return builder.toString()
    }

    private fun readLogTail(): String = runCatching {
        val builder = StringBuilder()
        if (rotatedFile.exists()) builder.append(rotatedFile.readText().takeLast(MAX_EXPORT_CHARS))
        if (logFile.exists()) builder.append(logFile.readText().takeLast(MAX_EXPORT_CHARS))
        builder.toString()
    }.getOrDefault("")

    private fun appendBlocking(entry: DiagnosticEntry, error: Throwable?) {
        runCatching {
            if (!logDir.exists() && !logDir.mkdirs()) return
            if (logFile.exists() && logFile.length() > MAX_FILE_BYTES) {
                rotatedFile.delete()
                logFile.renameTo(rotatedFile)
            }
            logFile.appendText(formatEntry(entry, error))
        }
    }

    private fun formatEntry(entry: DiagnosticEntry, error: Throwable?): String {
        val builder = StringBuilder()
        builder.append(timestampFormat().format(Date(entry.millis)))
            .append(" [").append(entry.category.label).append("] ")
            .append(entry.message)
            .append('\n')
        if (error != null) {
            val stack = StringWriter()
            error.printStackTrace(PrintWriter(stack))
            builder.append(stack.toString())
        }
        return builder.toString()
    }

    private companion object {
        const val MAX_BUFFER = 1500
        const val MAX_FILE_BYTES = 512L * 1024L
        const val MAX_EXPORT_CHARS = 200_000
    }
}
