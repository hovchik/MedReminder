package com.medreminder.presentation.screens.ocr

import android.Manifest
import android.util.Log
import android.view.ViewGroup
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
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
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.medreminder.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.concurrent.Executors

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

    val cameraExecutor = remember { Executors.newSingleThreadExecutor() }
    val cameraProviderRef = remember { mutableStateOf<ProcessCameraProvider?>(null) }
    val imageAnalysisRef = remember { mutableStateOf<ImageAnalysis?>(null) }

    val permissionLauncher = rememberPermissionLauncher { granted ->
        hasCameraPermission = granted
    }

    DisposableEffect(Unit) {
        onDispose {
            cameraProviderRef.value?.unbindAll()
            cameraExecutor.shutdown()
        }
    }

    // Callback to capture and process a single frame when user taps scan
    val onScanClicked: () -> Unit = {
        if (!isProcessing) {
            isProcessing = true
            val imageAnalysis = imageAnalysisRef.value
            val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

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
                                // Clear analyzer after single capture
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
                            Log.e("OCR", "Text recognition failed", e)
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
            // Camera preview - no auto-scanning, waits for user to tap scan
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
            ) {
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

                                // ImageAnalysis is created but no analyzer is set yet.
                                // Analyzer is attached only when the user taps the scan button.
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

                // Bottom area with instructions and scan button
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

                    if (isProcessing) {
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
        // Find dosage
        val dosageMatch = dosageRegex.find(line)
        if (dosageMatch != null && dosage.isBlank()) {
            dosage = dosageMatch.value
        }

        // Heuristic: medication name is often the first prominent line
        // that doesn't look like a company name or instruction
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
