package com.medreminder.ai.modelmanager

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import com.medreminder.ai.local.AiProviderSelector
import com.medreminder.ai.local.InstallState
import com.medreminder.ai.local.LocalAiModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.io.File
import java.io.IOException
import java.io.RandomAccessFile
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton

data class DownloadState(
    val modelId: String = "",
    val status: DownloadStatus = DownloadStatus.IDLE,
    val totalBytes: Long = 0,
    val downloadedBytes: Long = 0,
    val speedBytesPerSec: Long = 0,
    val etaSeconds: Long = 0,
    val message: String = "",
    val retryCount: Int = 0
) {
    val progressFraction: Float
        get() = if (totalBytes > 0) downloadedBytes.toFloat() / totalBytes else 0f

    val progressPercent: Int
        get() = (progressFraction * 100).toInt()

    val downloadedMb: Long
        get() = downloadedBytes / (1024 * 1024)

    val totalMb: Long
        get() = totalBytes / (1024 * 1024)

    val speedMbPerSec: Float
        get() = speedBytesPerSec / (1024f * 1024f)
}

enum class DownloadStatus {
    IDLE,
    CONNECTING,
    DOWNLOADING,
    PAUSED,
    VERIFYING,
    COMPLETED,
    FAILED,
    CANCELLED
}

@Singleton
class ModelDownloadManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val modelManager: LocalModelManager,
    private val providerSelector: AiProviderSelector
) {
    companion object {
        private const val BUFFER_SIZE = 256 * 1024           // 256 KB read buffer
        private const val PROGRESS_UPDATE_INTERVAL_MS = 250L
        private const val MAX_RETRIES = 4
        private const val CONNECT_TIMEOUT_MS = 30_000
        private const val READ_TIMEOUT_MS = 60_000
        private const val CHUNK_CONNECTIONS_WIFI = 4
        private const val CHUNK_CONNECTIONS_MOBILE = 2
        private const val MIN_CHUNK_SIZE = 2L * 1024 * 1024  // 2 MB minimum per chunk
    }

    private val _downloadState = MutableStateFlow(DownloadState())
    val downloadState: StateFlow<DownloadState> = _downloadState.asStateFlow()

    private var downloadJob: Job? = null
    @Volatile private var isPaused = false
    @Volatile private var isCancelled = false

    /**
     * Cached HuggingFace token, read once at the start of each download.
     * Avoids blocking coroutine reads inside tight download loops.
     */
    @Volatile private var cachedHfToken: String? = null

    private fun applyHuggingFaceAuth(connection: HttpURLConnection, url: String) {
        val token = cachedHfToken
        if (!token.isNullOrBlank() && url.contains("huggingface.co")) {
            connection.setRequestProperty("Authorization", "Bearer $token")
        }
    }

    private fun getModelsDir(): File {
        val dir = File(context.filesDir, "ai_models")
        dir.mkdirs()
        return dir
    }

    private fun getPartialFile(model: LocalAiModel): File {
        val modelDir = File(getModelsDir(), model.modelId)
        modelDir.mkdirs()
        return File(modelDir, "${model.modelId}.${model.fileFormat}.part")
    }

    private fun getFinalFile(model: LocalAiModel): File {
        val modelDir = File(getModelsDir(), model.modelId)
        modelDir.mkdirs()
        return File(modelDir, "${model.modelId}.${model.fileFormat}")
    }

    /**
     * Start downloading the model. Supports resume from partial downloads.
     * Uses HTTP Range headers for resumable downloads.
     */
    fun startDownload(model: LocalAiModel, scope: CoroutineScope) {
        if (downloadJob?.isActive == true) {
            return // Download already in progress
        }

        isPaused = false
        isCancelled = false

        downloadJob = scope.launch(Dispatchers.IO) {
            executeDownload(model)
        }
    }

    fun pauseDownload() {
        isPaused = true
        _downloadState.update {
            it.copy(
                status = DownloadStatus.PAUSED,
                message = "Download paused"
            )
        }
    }

    fun resumeDownload(model: LocalAiModel, scope: CoroutineScope) {
        if (downloadJob?.isActive == true && !isPaused) return

        isPaused = false
        isCancelled = false

        downloadJob = scope.launch(Dispatchers.IO) {
            executeDownload(model)
        }
    }

    fun cancelDownload(model: LocalAiModel) {
        isCancelled = true
        downloadJob?.cancel()

        // Clean up partial file
        getPartialFile(model).delete()

        _downloadState.update {
            DownloadState(
                modelId = model.modelId,
                status = DownloadStatus.CANCELLED,
                message = "Download cancelled"
            )
        }
    }

    fun isWifiConnected(): Boolean {
        return try {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            val network = cm.activeNetwork ?: return false
            val caps = cm.getNetworkCapabilities(network) ?: return false
            caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
        } catch (_: Exception) {
            false
        }
    }

    fun isNetworkAvailable(): Boolean {
        return try {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            val network = cm.activeNetwork ?: return false
            val caps = cm.getNetworkCapabilities(network) ?: return false
            caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        } catch (_: Exception) {
            false
        }
    }

    private suspend fun executeDownload(model: LocalAiModel) {
        // Read the HuggingFace token once for the duration of this download
        cachedHfToken = providerSelector.getHuggingFaceTokenOnce()

        val partialFile = getPartialFile(model)
        val finalFile = getFinalFile(model)

        // If already fully downloaded, skip to verification
        // Use a tolerance check instead of exact match
        val expectedBytes = model.sizeMb * 1024L * 1024L
        if (finalFile.exists() && expectedBytes > 0 && finalFile.length() >= expectedBytes * 0.8) {
            _downloadState.update {
                DownloadState(
                    modelId = model.modelId,
                    status = DownloadStatus.VERIFYING,
                    totalBytes = finalFile.length(),
                    downloadedBytes = finalFile.length(),
                    message = "Verifying existing file..."
                )
            }
            finishDownload(model, finalFile)
            return
        }

        // Determine resume offset from partial file
        var resumeOffset = if (partialFile.exists()) partialFile.length() else 0L

        modelManager.registerModel(model.copy(installState = InstallState.DOWNLOADING))

        var retryCount = 0
        var lastError: Exception? = null

        while (retryCount <= MAX_RETRIES && !isCancelled) {
            try {
                _downloadState.update {
                    DownloadState(
                        modelId = model.modelId,
                        status = DownloadStatus.CONNECTING,
                        downloadedBytes = resumeOffset,
                        retryCount = retryCount,
                        message = if (retryCount > 0) "Retrying (attempt ${retryCount + 1})..." else "Connecting..."
                    )
                }

                val success = downloadWithResume(model, partialFile, resumeOffset)

                if (success && !isCancelled) {
                    // Rename partial -> final
                    if (!partialFile.renameTo(finalFile)) {
                        // Fallback: copy and delete if rename fails (cross-filesystem)
                        partialFile.copyTo(finalFile, overwrite = true)
                        partialFile.delete()
                    }
                    finishDownload(model, finalFile)
                    return
                } else if (isPaused) {
                    // Save progress to DB for later resume
                    modelManager.registerModel(
                        model.copy(
                            installState = InstallState.PAUSED,
                            downloadedBytes = if (partialFile.exists()) partialFile.length() else 0L
                        )
                    )
                    return
                }

            } catch (e: IOException) {
                lastError = e
                retryCount++
                resumeOffset = if (partialFile.exists()) partialFile.length() else 0L

                if (retryCount <= MAX_RETRIES && !isCancelled) {
                    val delayMs = getRetryDelayMs(retryCount)
                    _downloadState.update {
                        it.copy(
                            status = DownloadStatus.CONNECTING,
                            retryCount = retryCount,
                            message = "Network error: ${e.message}. Retrying in ${delayMs / 1000}s..."
                        )
                    }
                    delay(delayMs)
                }
            } catch (e: CancellationException) {
                throw e // Let coroutine cancellation propagate
            } catch (e: Exception) {
                lastError = e
                break
            }
        }

        // All retries exhausted
        modelManager.updateInstallState(model.modelId, InstallState.FAILED)
        _downloadState.update {
            DownloadState(
                modelId = model.modelId,
                status = DownloadStatus.FAILED,
                downloadedBytes = if (partialFile.exists()) partialFile.length() else 0L,
                totalBytes = expectedBytes,
                retryCount = retryCount,
                message = "Download failed: ${lastError?.message ?: "Unknown error"}"
            )
        }
    }

    /**
     * Resolves the final download URL by following redirects and probing for
     * Range support and total file size via a HEAD request.
     * Returns a Triple of (resolvedUrl, totalBytes, supportsRange).
     */
    private fun probeDownload(downloadUrl: String, model: LocalAiModel): Triple<String, Long, Boolean> {
        var url = downloadUrl
        var supportsRange = false
        var totalBytes = model.sizeMb * 1024L * 1024L

        // Use a HEAD request to discover the final URL and capabilities
        var connection: HttpURLConnection? = null
        try {
            connection = (URL(url).openConnection() as HttpURLConnection).apply {
                connectTimeout = CONNECT_TIMEOUT_MS
                readTimeout = READ_TIMEOUT_MS
                requestMethod = "HEAD"
                instanceFollowRedirects = true
                setRequestProperty("User-Agent", "MedReminder/1.0")
                setRequestProperty("Range", "bytes=0-0")
            }
            applyHuggingFaceAuth(connection, url)

            val responseCode = connection.responseCode

            // Resolve redirect
            if (responseCode in 301..308) {
                val loc = connection.getHeaderField("Location")
                if (loc != null) url = loc
            }

            val finalUrl = connection.url?.toString() ?: url
            url = finalUrl

            when (responseCode) {
                HttpURLConnection.HTTP_PARTIAL, 206 -> {
                    supportsRange = true
                    val contentRange = connection.getHeaderField("Content-Range")
                    val fullSize = contentRange?.substringAfter("/", "")?.toLongOrNull()
                    if (fullSize != null && fullSize > 0) {
                        totalBytes = fullSize
                    }
                }
                HttpURLConnection.HTTP_OK -> {
                    val acceptRanges = connection.getHeaderField("Accept-Ranges")
                    supportsRange = acceptRanges?.contains("bytes", ignoreCase = true) == true
                    val cl = connection.getHeaderField("Content-Length")?.toLongOrNull()
                    if (cl != null && cl > 0) totalBytes = cl
                }
            }
        } catch (_: Exception) {
            // Probe failed; fall back to single-stream
        } finally {
            connection?.disconnect()
        }

        return Triple(url, totalBytes, supportsRange)
    }

    /**
     * Opens an HTTP connection for a specific byte range.
     * Follows one level of redirect if needed.
     */
    private fun openRangeConnection(
        url: String,
        rangeStart: Long,
        rangeEnd: Long
    ): HttpURLConnection {
        var connection = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = CONNECT_TIMEOUT_MS
            readTimeout = READ_TIMEOUT_MS
            requestMethod = "GET"
            instanceFollowRedirects = true
            setRequestProperty("User-Agent", "MedReminder/1.0")
            setRequestProperty("Accept", "*/*")
            setRequestProperty("Range", "bytes=$rangeStart-$rangeEnd")
        }
        applyHuggingFaceAuth(connection, url)

        val code = connection.responseCode
        if (code in 301..308) {
            val loc = connection.getHeaderField("Location")
            connection.disconnect()
            if (loc != null) {
                connection = (URL(loc).openConnection() as HttpURLConnection).apply {
                    connectTimeout = CONNECT_TIMEOUT_MS
                    readTimeout = READ_TIMEOUT_MS
                    requestMethod = "GET"
                    instanceFollowRedirects = true
                    setRequestProperty("User-Agent", "MedReminder/1.0")
                    setRequestProperty("Accept", "*/*")
                    setRequestProperty("Range", "bytes=$rangeStart-$rangeEnd")
                }
                applyHuggingFaceAuth(connection, loc)
            }
        }

        return connection
    }

    /**
     * Fast concurrent chunk download.
     * Splits the remaining bytes into N chunks and downloads them in parallel
     * using coroutines and multiple HTTP connections. Falls back to a single
     * stream if the server does not support Range requests.
     */
    private suspend fun downloadWithResume(
        model: LocalAiModel,
        partialFile: File,
        resumeOffset: Long
    ): Boolean = withContext(Dispatchers.IO) {
        // Step 1 – probe the server for Range support and total size
        val (resolvedUrl, totalBytes, supportsRange) = probeDownload(model.downloadUrl, model)

        if (totalBytes <= 0) {
            throw IOException("Cannot determine file size for ${model.displayName}")
        }

        val remainingBytes = totalBytes - resumeOffset

        // Decide how many parallel connections to use
        val maxConnections = if (isWifiConnected()) CHUNK_CONNECTIONS_WIFI else CHUNK_CONNECTIONS_MOBILE
        val useChunked = supportsRange && remainingBytes > MIN_CHUNK_SIZE * 2 && maxConnections > 1

        _downloadState.update {
            DownloadState(
                modelId = model.modelId,
                status = DownloadStatus.DOWNLOADING,
                totalBytes = totalBytes,
                downloadedBytes = resumeOffset,
                message = if (useChunked) "Fast downloading ${model.displayName} (${maxConnections}x)..."
                          else "Downloading ${model.displayName}..."
            )
        }

        if (!useChunked) {
            // Fallback: single-stream download (original behaviour)
            return@withContext singleStreamDownload(resolvedUrl, model, partialFile, resumeOffset, totalBytes)
        }

        // Step 2 – split into chunks and download concurrently
        val chunkCount = maxConnections.coerceAtMost(
            (remainingBytes / MIN_CHUNK_SIZE).toInt().coerceAtLeast(1)
        )
        val chunkSize = remainingBytes / chunkCount

        data class Chunk(val index: Int, val start: Long, val end: Long)
        val chunks = (0 until chunkCount).map { i ->
            val start = resumeOffset + i * chunkSize
            val end = if (i == chunkCount - 1) totalBytes - 1 else start + chunkSize - 1
            Chunk(i, start, end)
        }

        // Create temp file per chunk
        val chunkFiles = chunks.map { c ->
            File(partialFile.parent, "${partialFile.name}.chunk${c.index}")
        }

        // Shared progress tracking across all chunks
        val chunkBytesWritten = LongArray(chunkCount) { i ->
            // Resume: if chunk file exists, count existing bytes
            if (chunkFiles[i].exists()) chunkFiles[i].length() else 0L
        }

        val progressJob = launch {
            var lastSpeedCalcTime = System.currentTimeMillis()
            var lastSpeedCalcBytes = chunkBytesWritten.sum() + resumeOffset
            while (isActive) {
                delay(PROGRESS_UPDATE_INTERVAL_MS)
                val totalWritten = chunkBytesWritten.sum() + resumeOffset
                val now = System.currentTimeMillis()
                val timeDelta = now - lastSpeedCalcTime
                val speed = if (timeDelta > 0) {
                    ((totalWritten - lastSpeedCalcBytes) * 1000) / timeDelta
                } else 0L
                lastSpeedCalcTime = now
                lastSpeedCalcBytes = totalWritten
                val remaining = totalBytes - totalWritten
                val eta = if (speed > 0) remaining / speed else 0L

                _downloadState.update {
                    it.copy(
                        downloadedBytes = totalWritten,
                        speedBytesPerSec = speed,
                        etaSeconds = eta
                    )
                }
            }
        }

        var allSuccess = true
        try {
            // Launch parallel chunk downloads
            val deferreds = chunks.map { chunk ->
                async {
                    downloadChunk(
                        resolvedUrl, chunkFiles[chunk.index],
                        chunk.start, chunk.end,
                        chunkBytesWritten, chunk.index
                    )
                }
            }

            // Await all chunks
            for (d in deferreds) {
                if (!d.await()) {
                    allSuccess = false
                    break
                }
            }
        } finally {
            progressJob.cancel()
        }

        if (!allSuccess || isCancelled || isPaused) {
            // Keep chunk files for resume on next attempt
            return@withContext false
        }

        // Step 3 – merge chunk files into the partial file
        _downloadState.update {
            it.copy(message = "Merging chunks...")
        }

        RandomAccessFile(partialFile, "rw").use { raf ->
            for (i in chunks.indices) {
                raf.seek(chunks[i].start)
                chunkFiles[i].inputStream().buffered(BUFFER_SIZE).use { input ->
                    val buffer = ByteArray(BUFFER_SIZE)
                    var bytesRead: Int
                    while (input.read(buffer).also { bytesRead = it } != -1) {
                        raf.write(buffer, 0, bytesRead)
                    }
                }
                chunkFiles[i].delete()
            }
        }

        // Final progress update
        _downloadState.update {
            it.copy(
                downloadedBytes = totalBytes,
                speedBytesPerSec = 0,
                etaSeconds = 0
            )
        }

        return@withContext true
    }

    /**
     * Downloads a single chunk (byte range) of the file.
     * Updates the shared progress array atomically.
     */
    private fun downloadChunk(
        url: String,
        chunkFile: File,
        rangeStart: Long,
        rangeEnd: Long,
        chunkBytesWritten: LongArray,
        chunkIndex: Int
    ): Boolean {
        val existingBytes = if (chunkFile.exists()) chunkFile.length() else 0L
        val actualStart = rangeStart + existingBytes
        if (actualStart > rangeEnd) {
            // Chunk already complete
            chunkBytesWritten[chunkIndex] = rangeEnd - rangeStart + 1
            return true
        }

        var connection: HttpURLConnection? = null
        try {
            connection = openRangeConnection(url, actualStart, rangeEnd)

            val code = connection.responseCode
            if (code != HttpURLConnection.HTTP_PARTIAL && code != HttpURLConnection.HTTP_OK) {
                throw IOException("HTTP $code for chunk $chunkIndex")
            }

            val inputStream = connection.inputStream ?: throw IOException("No data for chunk $chunkIndex")
            // Append mode for resume
            val raf = RandomAccessFile(chunkFile, "rw")
            raf.seek(existingBytes)

            try {
                val buffer = ByteArray(BUFFER_SIZE)
                var written = existingBytes
                var bytesRead: Int

                while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                    if (isCancelled || isPaused) return false
                    raf.write(buffer, 0, bytesRead)
                    written += bytesRead
                    chunkBytesWritten[chunkIndex] = written
                }
            } finally {
                try { raf.close() } catch (_: Exception) {}
                try { inputStream.close() } catch (_: Exception) {}
            }

            return true
        } finally {
            connection?.disconnect()
        }
    }

    /**
     * Fallback single-stream download when server does not support Range
     * or the file is too small to benefit from chunking.
     */
    private suspend fun singleStreamDownload(
        url: String,
        model: LocalAiModel,
        partialFile: File,
        resumeOffset: Long,
        totalBytes: Long
    ): Boolean = withContext(Dispatchers.IO) {
        var connection: HttpURLConnection? = null
        try {
            connection = (URL(url).openConnection() as HttpURLConnection).apply {
                connectTimeout = CONNECT_TIMEOUT_MS
                readTimeout = READ_TIMEOUT_MS
                requestMethod = "GET"
                instanceFollowRedirects = true
                setRequestProperty("User-Agent", "MedReminder/1.0")
                setRequestProperty("Accept", "*/*")
                if (resumeOffset > 0) {
                    setRequestProperty("Range", "bytes=$resumeOffset-")
                }
            }
            applyHuggingFaceAuth(connection, url)

            val responseCode = connection.responseCode

            // Handle redirects manually
            if (responseCode in 301..308) {
                val redirectUrl = connection.getHeaderField("Location")
                connection.disconnect()
                if (redirectUrl != null) {
                    connection = (URL(redirectUrl).openConnection() as HttpURLConnection).apply {
                        connectTimeout = CONNECT_TIMEOUT_MS
                        readTimeout = READ_TIMEOUT_MS
                        requestMethod = "GET"
                        instanceFollowRedirects = true
                        setRequestProperty("User-Agent", "MedReminder/1.0")
                        setRequestProperty("Accept", "*/*")
                        if (resumeOffset > 0) {
                            setRequestProperty("Range", "bytes=$resumeOffset-")
                        }
                    }
                    applyHuggingFaceAuth(connection, redirectUrl)
                } else {
                    throw IOException("Redirect without Location header")
                }
            }

            val finalResponseCode = connection.responseCode
            val startOffset: Long = when (finalResponseCode) {
                HttpURLConnection.HTTP_PARTIAL -> resumeOffset
                HttpURLConnection.HTTP_OK -> {
                    if (resumeOffset > 0) partialFile.delete()
                    0L
                }
                HttpURLConnection.HTTP_UNAUTHORIZED, HttpURLConnection.HTTP_FORBIDDEN -> {
                    val needsToken = url.contains("huggingface.co") && cachedHfToken.isNullOrBlank()
                    val message = if (needsToken) {
                        "This model requires a HuggingFace access token. Add your token in Settings > Local AI > HuggingFace Token."
                    } else {
                        "Access denied (HTTP $finalResponseCode). Your HuggingFace token may be invalid, or you need to accept the model's license at huggingface.co."
                    }
                    throw IOException(message)
                }
                else -> {
                    val errorBody = try {
                        connection.errorStream?.bufferedReader()?.readText()?.take(200) ?: ""
                    } catch (_: Exception) { "" }
                    throw IOException("HTTP $finalResponseCode: ${connection.responseMessage}. $errorBody".trim())
                }
            }

            val inputStream = connection.inputStream
                ?: throw IOException("Server returned no data")
            val raf = RandomAccessFile(partialFile, "rw")

            try {
                raf.seek(startOffset)

                val buffer = ByteArray(BUFFER_SIZE)
                var bytesWritten = startOffset
                var bytesRead: Int
                var lastProgressUpdate = System.currentTimeMillis()
                var lastSpeedCalcTime = System.currentTimeMillis()
                var lastSpeedCalcBytes = bytesWritten
                var currentSpeed = 0L

                while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                    if (isCancelled) return@withContext false
                    if (isPaused) return@withContext false

                    raf.write(buffer, 0, bytesRead)
                    bytesWritten += bytesRead

                    val now = System.currentTimeMillis()
                    if (now - lastProgressUpdate >= PROGRESS_UPDATE_INTERVAL_MS) {
                        val timeDeltaMs = now - lastSpeedCalcTime
                        if (timeDeltaMs > 0) {
                            val bytesDelta = bytesWritten - lastSpeedCalcBytes
                            currentSpeed = (bytesDelta * 1000) / timeDeltaMs
                            lastSpeedCalcTime = now
                            lastSpeedCalcBytes = bytesWritten
                        }
                        val remaining = totalBytes - bytesWritten
                        val eta = if (currentSpeed > 0) remaining / currentSpeed else 0L

                        _downloadState.update {
                            it.copy(
                                downloadedBytes = bytesWritten,
                                speedBytesPerSec = currentSpeed,
                                etaSeconds = eta
                            )
                        }
                        lastProgressUpdate = now
                    }
                }

                _downloadState.update {
                    it.copy(
                        downloadedBytes = bytesWritten,
                        speedBytesPerSec = 0,
                        etaSeconds = 0
                    )
                }
            } finally {
                try { raf.close() } catch (_: Exception) {}
                try { inputStream.close() } catch (_: Exception) {}
            }

            return@withContext !isCancelled && !isPaused
        } finally {
            connection?.disconnect()
        }
    }

    private suspend fun finishDownload(model: LocalAiModel, file: File) {
        _downloadState.update {
            it.copy(
                status = DownloadStatus.VERIFYING,
                message = "Verifying download..."
            )
        }

        // Verify checksum if provided
        if (model.checksum.isNotBlank()) {
            val fileChecksum = computeSha256(file)
            if (!fileChecksum.equals(model.checksum, ignoreCase = true)) {
                file.delete()
                modelManager.updateInstallState(model.modelId, InstallState.FAILED)
                _downloadState.update {
                    DownloadState(
                        modelId = model.modelId,
                        status = DownloadStatus.FAILED,
                        message = "Checksum verification failed. File may be corrupted."
                    )
                }
                return
            }
        }

        // Verify file size is reasonable (only if we have an expected size)
        if (model.sizeMb > 0) {
            val fileSizeMb = file.length() / (1024L * 1024L)
            if (fileSizeMb < 1) {
                // File is essentially empty
                file.delete()
                modelManager.updateInstallState(model.modelId, InstallState.FAILED)
                _downloadState.update {
                    DownloadState(
                        modelId = model.modelId,
                        status = DownloadStatus.FAILED,
                        message = "Downloaded file is empty or too small (${file.length()} bytes)"
                    )
                }
                return
            }
        }

        // Mark installed
        modelManager.markInstalled(model.modelId, file.absolutePath)

        _downloadState.update {
            DownloadState(
                modelId = model.modelId,
                status = DownloadStatus.COMPLETED,
                totalBytes = file.length(),
                downloadedBytes = file.length(),
                message = "${model.displayName} downloaded successfully"
            )
        }
    }

    private fun computeSha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().buffered(BUFFER_SIZE).use { stream ->
            val buffer = ByteArray(BUFFER_SIZE)
            var bytesRead: Int
            while (stream.read(buffer).also { bytesRead = it } != -1) {
                digest.update(buffer, 0, bytesRead)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private fun getRetryDelayMs(retryCount: Int): Long {
        // Exponential backoff: 2s, 4s, 8s, 16s
        return (1L shl retryCount) * 1000L
    }

    /** Clean up partial downloads and chunk files for a given model. */
    suspend fun cleanupPartialDownload(model: LocalAiModel) {
        val partialFile = getPartialFile(model)
        // Delete chunk files
        partialFile.parentFile?.listFiles()?.filter {
            it.name.startsWith(partialFile.name) && it.name.contains(".chunk")
        }?.forEach { it.delete() }
        partialFile.delete()
        getFinalFile(model).delete()
        val modelDir = File(getModelsDir(), model.modelId)
        if (modelDir.exists() && modelDir.listFiles()?.isEmpty() == true) {
            modelDir.delete()
        }
    }

    /** Check for existing partial download and return bytes already downloaded. */
    fun getExistingDownloadBytes(model: LocalAiModel): Long {
        val partialFile = getPartialFile(model)
        return if (partialFile.exists()) partialFile.length() else 0L
    }

    fun resetState() {
        _downloadState.update { DownloadState() }
    }
}
