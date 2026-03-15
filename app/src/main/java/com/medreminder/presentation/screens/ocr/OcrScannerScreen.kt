package com.medreminder.presentation.screens.ocr

import android.Manifest
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageFormat
import android.graphics.Rect
import android.graphics.YuvImage
import android.util.Log
import android.view.ViewGroup
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.TextRecognizer
import com.google.mlkit.vision.text.chinese.ChineseTextRecognizerOptions
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.medreminder.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.ByteArrayOutputStream
import java.util.concurrent.Executors

/**
 * OCR language options mapping to the recognition engine and language code.
 * ML Kit handles Latin and Chinese scripts; Tesseract handles Cyrillic, Armenian, and Farsi.
 */
enum class OcrLanguage(
    val displayName: String,
    val tesseractCode: String?  // null = use ML Kit
) {
    ENGLISH("English", null),
    SPANISH("Español", null),
    RUSSIAN("Русский", "rus"),
    CHINESE("中文", null),
    ARMENIAN("Հայերեն", "hye"),
    FARSI("فارسی", "fas");

    val usesMlKit: Boolean get() = tesseractCode == null
    val isChinese: Boolean get() = this == CHINESE
}

@androidx.annotation.OptIn(ExperimentalGetImage::class)
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OcrScannerScreen(
    onNavigateBack: () -> Unit,
    onMedicationScanned: (name: String, dosage: String) -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val coroutineScope = rememberCoroutineScope()

    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                    android.content.pm.PackageManager.PERMISSION_GRANTED
        )
    }

    var detectedText by remember { mutableStateOf("") }
    var parsedName by remember { mutableStateOf("") }
    var parsedDosage by remember { mutableStateOf("") }
    var isProcessing by remember { mutableStateOf(false) }
    var showResults by remember { mutableStateOf(false) }

    // Language selection
    var selectedLanguage by remember { mutableStateOf(OcrLanguage.ENGLISH) }
    var showLanguagePicker by remember { mutableStateOf(false) }

    // Tesseract download state
    var isDownloading by remember { mutableStateOf(false) }
    var downloadProgress by remember { mutableFloatStateOf(0f) }
    var downloadError by remember { mutableStateOf<String?>(null) }

    val cameraExecutor = remember { Executors.newSingleThreadExecutor() }
    val cameraProviderRef = remember { mutableStateOf<ProcessCameraProvider?>(null) }
    val imageAnalysisRef = remember { mutableStateOf<ImageAnalysis?>(null) }

    val tesseractManager = remember { TesseractManager(context) }

    val permissionLauncher = rememberPermissionLauncher { granted ->
        hasCameraPermission = granted
    }

    DisposableEffect(Unit) {
        onDispose {
            cameraProviderRef.value?.unbindAll()
            cameraExecutor.shutdown()
            tesseractManager.release()
        }
    }

    // Build the appropriate ML Kit recognizer based on selected language
    fun getMlKitRecognizer(): TextRecognizer {
        return if (selectedLanguage.isChinese) {
            TextRecognition.getClient(ChineseTextRecognizerOptions.Builder().build())
        } else {
            TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        }
    }

    // Convert ImageProxy to Bitmap for Tesseract
    fun imageProxyToBitmap(imageProxy: ImageProxy): Bitmap? {
        return try {
            val mediaImage = imageProxy.image ?: return null
            val planes = mediaImage.planes
            val yBuffer = planes[0].buffer
            val uBuffer = planes[1].buffer
            val vBuffer = planes[2].buffer
            val ySize = yBuffer.remaining()
            val uSize = uBuffer.remaining()
            val vSize = vBuffer.remaining()
            val nv21 = ByteArray(ySize + uSize + vSize)
            yBuffer.get(nv21, 0, ySize)
            vBuffer.get(nv21, ySize, vSize)
            uBuffer.get(nv21, ySize + vSize, uSize)
            val yuvImage = YuvImage(nv21, ImageFormat.NV21, mediaImage.width, mediaImage.height, null)
            val out = ByteArrayOutputStream()
            yuvImage.compressToJpeg(Rect(0, 0, mediaImage.width, mediaImage.height), 90, out)
            val bytes = out.toByteArray()
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        } catch (e: Exception) {
            Log.e("OCR", "Failed to convert ImageProxy to Bitmap", e)
            null
        }
    }

    // Process scan using the appropriate engine
    val onScanClicked: () -> Unit = {
        if (!isProcessing && !isDownloading) {
            val lang = selectedLanguage

            if (lang.usesMlKit) {
                // ML Kit path (Latin / Chinese)
                isProcessing = true
                val recognizer = getMlKitRecognizer()
                val imageAnalysis = imageAnalysisRef.value

                imageAnalysis?.setAnalyzer(cameraExecutor) { imageProxy ->
                    val mediaImage = imageProxy.image
                    if (mediaImage != null) {
                        val image = InputImage.fromMediaImage(
                            mediaImage,
                            imageProxy.imageInfo.rotationDegrees
                        )
                        recognizer.process(image)
                            .addOnSuccessListener { result ->
                                coroutineScope.launch(Dispatchers.Main) {
                                    imageAnalysis.clearAnalyzer()
                                    if (result.text.isNotBlank()) {
                                        detectedText = result.text
                                        val parsed = parseMedicationText(result.text)
                                        parsedName = parsed.first
                                        parsedDosage = parsed.second
                                        showResults = true
                                    }
                                    isProcessing = false
                                }
                            }
                            .addOnFailureListener { e ->
                                Log.e("OCR", "ML Kit recognition failed", e)
                                coroutineScope.launch(Dispatchers.Main) {
                                    imageAnalysis.clearAnalyzer()
                                    isProcessing = false
                                }
                            }
                            .addOnCompleteListener {
                                imageProxy.close()
                            }
                    } else {
                        imageProxy.close()
                        coroutineScope.launch(Dispatchers.Main) {
                            imageAnalysis.clearAnalyzer()
                            isProcessing = false
                        }
                    }
                }
            } else {
                // Tesseract path (Russian, Armenian, Farsi)
                val tessCode = lang.tesseractCode!!

                // Check if language data is downloaded
                if (!tesseractManager.isLanguageAvailable(tessCode)) {
                    // Need to download first
                    isDownloading = true
                    downloadProgress = 0f
                    downloadError = null
                    coroutineScope.launch {
                        val success = tesseractManager.downloadLanguageData(tessCode) { progress ->
                            downloadProgress = progress
                        }
                        isDownloading = false
                        if (!success) {
                            downloadError = context.getString(R.string.download_failed)
                        }
                    }
                } else {
                    // Data available — run Tesseract OCR
                    isProcessing = true
                    val imageAnalysis = imageAnalysisRef.value

                    imageAnalysis?.setAnalyzer(cameraExecutor) { imageProxy ->
                        val bitmap = imageProxyToBitmap(imageProxy)
                        imageProxy.close()

                        coroutineScope.launch(Dispatchers.Main) {
                            imageAnalysis.clearAnalyzer()
                        }

                        if (bitmap != null) {
                            coroutineScope.launch {
                                val initialized = tesseractManager.initEngine(tessCode)
                                if (initialized) {
                                    val text = tesseractManager.recognizeText(bitmap)
                                    launch(Dispatchers.Main) {
                                        if (text.isNotBlank()) {
                                            detectedText = text
                                            val parsed = parseMedicationText(text)
                                            parsedName = parsed.first
                                            parsedDosage = parsed.second
                                            showResults = true
                                        }
                                        isProcessing = false
                                    }
                                } else {
                                    launch(Dispatchers.Main) {
                                        isProcessing = false
                                    }
                                }
                            }
                        } else {
                            coroutineScope.launch(Dispatchers.Main) {
                                isProcessing = false
                            }
                        }
                    }
                }
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.scan_medication)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, stringResource(R.string.back))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Black.copy(alpha = 0.7f),
                    titleContentColor = Color.White,
                    navigationIconContentColor = Color.White
                )
            )
        }
    ) { padding ->
        if (!hasCameraPermission) {
            // Permission request screen
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(
                    Icons.Default.CameraAlt,
                    null,
                    modifier = Modifier.size(80.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(24.dp))
                Text(
                    stringResource(R.string.camera_permission_required),
                    style = MaterialTheme.typography.titleMedium,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(16.dp))
                Button(
                    onClick = { permissionLauncher.launch(Manifest.permission.CAMERA) },
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(stringResource(R.string.grant_permission))
                }
            }
        } else if (showResults) {
            // Results screen
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    stringResource(R.string.scanned_results),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )

                OutlinedTextField(
                    value = parsedName,
                    onValueChange = { parsedName = it },
                    label = { Text(stringResource(R.string.detected_name)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp)
                )

                OutlinedTextField(
                    value = parsedDosage,
                    onValueChange = { parsedDosage = it },
                    label = { Text(stringResource(R.string.detected_dosage)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp)
                )

                if (detectedText.isNotBlank()) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                        )
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(
                                stringResource(R.string.raw_text),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                detectedText,
                                style = MaterialTheme.typography.bodySmall,
                                maxLines = 8
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.weight(1f))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedButton(
                        onClick = { showResults = false },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.CameraAlt, null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(stringResource(R.string.rescan))
                    }
                    Button(
                        onClick = { onMedicationScanned(parsedName, parsedDosage) },
                        modifier = Modifier.weight(1f),
                        enabled = parsedName.isNotBlank(),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.Check, null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(stringResource(R.string.use_scanned_text))
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
            }
        } else {
            // Camera preview with language selector and scan button
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
            ) {
                // Camera preview
                AndroidView(
                    factory = { ctx ->
                        val previewView = PreviewView(ctx).apply {
                            layoutParams = ViewGroup.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT,
                                ViewGroup.LayoutParams.MATCH_PARENT
                            )
                            scaleType = PreviewView.ScaleType.FILL_CENTER
                        }

                        val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)
                        cameraProviderFuture.addListener({
                            try {
                                val cameraProvider = cameraProviderFuture.get()
                                cameraProviderRef.value = cameraProvider

                                val preview = Preview.Builder().build().also {
                                    it.setSurfaceProvider(previewView.surfaceProvider)
                                }

                                val imageAnalysis = ImageAnalysis.Builder()
                                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                                    .build()
                                imageAnalysisRef.value = imageAnalysis

                                cameraProvider.unbindAll()
                                cameraProvider.bindToLifecycle(
                                    lifecycleOwner,
                                    CameraSelector.DEFAULT_BACK_CAMERA,
                                    preview,
                                    imageAnalysis
                                )
                            } catch (e: Exception) {
                                Log.e("OCR", "Camera setup failed", e)
                            }
                        }, ContextCompat.getMainExecutor(ctx))

                        previewView
                    },
                    modifier = Modifier.fillMaxSize()
                )

                // Language selector chip at the top
                Box(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 16.dp)
                ) {
                    AssistChip(
                        onClick = { showLanguagePicker = true },
                        label = {
                            Text(
                                "${stringResource(R.string.ocr_language)}: ${selectedLanguage.displayName}",
                                color = Color.White
                            )
                        },
                        leadingIcon = {
                            Icon(
                                Icons.Default.Language,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(18.dp)
                            )
                        },
                        trailingIcon = {
                            Icon(
                                Icons.Default.ArrowDropDown,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(18.dp)
                            )
                        },
                        colors = AssistChipDefaults.assistChipColors(
                            containerColor = Color.Black.copy(alpha = 0.6f)
                        ),
                        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.4f)),
                        shape = RoundedCornerShape(20.dp)
                    )

                    // Language dropdown menu
                    DropdownMenu(
                        expanded = showLanguagePicker,
                        onDismissRequest = { showLanguagePicker = false }
                    ) {
                        OcrLanguage.entries.forEach { lang ->
                            DropdownMenuItem(
                                text = {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(lang.displayName)
                                        if (!lang.usesMlKit) {
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text(
                                                "Tesseract",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                },
                                onClick = {
                                    selectedLanguage = lang
                                    showLanguagePicker = false
                                    downloadError = null
                                },
                                leadingIcon = {
                                    if (lang == selectedLanguage) {
                                        Icon(
                                            Icons.Default.Check,
                                            contentDescription = null,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                            )
                        }
                    }
                }

                // Scan frame overlay
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(200.dp)
                            .border(
                                width = 2.dp,
                                color = Color.White.copy(alpha = 0.8f),
                                shape = RoundedCornerShape(16.dp)
                            )
                            .clip(RoundedCornerShape(16.dp))
                    )
                }

                // Bottom area: instructions, download progress, and scan button
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .background(Color.Black.copy(alpha = 0.6f))
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        stringResource(R.string.scan_instruction),
                        color = Color.White,
                        style = MaterialTheme.typography.bodyLarge,
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    // Download progress for Tesseract languages
                    if (isDownloading) {
                        Text(
                            stringResource(R.string.downloading_language_data),
                            color = Color.White,
                            fontSize = 14.sp
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        LinearProgressIndicator(
                            progress = { downloadProgress },
                            modifier = Modifier
                                .fillMaxWidth(0.7f)
                                .height(4.dp),
                            color = MaterialTheme.colorScheme.primary,
                            trackColor = Color.White.copy(alpha = 0.3f)
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            "${(downloadProgress * 100).toInt()}%",
                            color = Color.White.copy(alpha = 0.7f),
                            fontSize = 12.sp
                        )
                    } else if (downloadError != null) {
                        Text(
                            downloadError!!,
                            color = Color(0xFFFF6B6B),
                            fontSize = 14.sp
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedButton(
                            onClick = { downloadError = null },
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = Color.White
                            )
                        ) {
                            Text(stringResource(R.string.try_again))
                        }
                    } else if (isProcessing) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(56.dp),
                            color = Color.White,
                            strokeWidth = 3.dp
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            stringResource(R.string.scanning),
                            color = Color.White.copy(alpha = 0.7f),
                            fontSize = 14.sp
                        )
                    } else {
                        // Scan button
                        FilledIconButton(
                            onClick = onScanClicked,
                            modifier = Modifier.size(72.dp),
                            shape = CircleShape,
                            colors = IconButtonDefaults.filledIconButtonColors(
                                containerColor = MaterialTheme.colorScheme.primary
                            )
                        ) {
                            Icon(
                                Icons.Default.CameraAlt,
                                contentDescription = stringResource(R.string.scan_medication),
                                modifier = Modifier.size(32.dp)
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            stringResource(R.string.tap_to_scan),
                            color = Color.White.copy(alpha = 0.7f),
                            fontSize = 14.sp
                        )
                    }
                }
            }
        }
    }
}

/**
 * Parses raw OCR text to extract medication name and dosage.
 * Uses common patterns found on medication labels and prescriptions.
 */
fun parseMedicationText(rawText: String): Pair<String, String> {
    val lines = rawText.lines().map { it.trim() }.filter { it.isNotBlank() }

    var name = ""
    var dosage = ""

    // Pattern for dosage: number followed by unit (e.g., 500mg, 10 mg, 250 ml)
    val dosageRegex = Regex(
        """(\d+\.?\d*)\s*(mg|g|ml|mcg|iu|units?|tablets?|capsules?|drops?|puffs?)\b""",
        RegexOption.IGNORE_CASE
    )

    for (line in lines) {
        val dosageMatch = dosageRegex.find(line)
        if (dosageMatch != null && dosage.isBlank()) {
            dosage = dosageMatch.value
        }

        if (name.isBlank() && line.length in 3..50) {
            val lowerLine = line.lowercase()
            val skipPatterns = listOf(
                "take", "use", "apply", "store", "keep", "warning", "caution",
                "manufactured", "distributed", "lot", "exp", "ndc", "rx only",
                "each", "active", "inactive", "ingredients", "dosage", "directions",
                "www.", "http", ".com", "inc.", "ltd.", "corp."
            )
            if (skipPatterns.none { lowerLine.contains(it) }) {
                if (dosageMatch != null && line.contains(dosageMatch.value)) {
                    val beforeDosage = line.substringBefore(dosageMatch.value).trim()
                    if (beforeDosage.isNotBlank() && beforeDosage.length >= 3) {
                        name = beforeDosage
                    }
                } else {
                    name = line
                }
            }
        }
    }

    if (name.isBlank() && lines.isNotEmpty()) {
        name = lines.first().take(50)
    }

    name = name.replace(Regex("""[,;:.]$"""), "").trim()

    return name to dosage
}

@Composable
fun rememberPermissionLauncher(
    onResult: (Boolean) -> Unit
): androidx.activity.result.ActivityResultLauncher<String> {
    val launcher = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        onResult(isGranted)
    }
    return launcher
}
