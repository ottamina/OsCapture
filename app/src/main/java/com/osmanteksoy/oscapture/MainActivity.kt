package com.osmanteksoy.oscapture

import android.Manifest
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.osmanteksoy.oscapture.ui.theme.OsCaptureTheme
import kotlinx.coroutines.delay
import java.nio.ByteBuffer
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.concurrent.Executors
import kotlin.math.abs

// ─────────────────────────────────────────────
//  Constants
// ─────────────────────────────────────────────
private const val TAG = "OsCapture"
private const val FILENAME_FORMAT = "yyyy-MM-dd-HH-mm-ss-SSS"

// ─────────────────────────────────────────────
//  State Machine
// ─────────────────────────────────────────────
//
//  IDLE ──(start button)──► WAITING_FOR_MOTION
//  WAITING_FOR_MOTION ──(motion detected)──► MOTION_ACTIVE
//  MOTION_ACTIVE ──(no motion for 100ms)──► STILLNESS_TIMER
//  STILLNESS_TIMER ──(motion detected)──► MOTION_ACTIVE  (reset timer)
//  STILLNESS_TIMER ──(timer expires)──► LOCKED  (take photo)
//  LOCKED ──(motion detected)──► MOTION_ACTIVE  (unlock)
//  Any ──(stop button)──► IDLE
//
private enum class CaptureState {
    IDLE,               // Auto-capture kapalı
    WAITING_FOR_MOTION, // Auto-capture açık, ilk hareketi bekliyor
    MOTION_ACTIVE,      // Hareket algılandı
    STILLNESS_TIMER,    // Hareket durdu, zamanlayıcı çalışıyor
    LOCKED              // Fotoğraf çekildi, yeni hareket bekleniyor
}

// ─────────────────────────────────────────────
//  Activity
// ─────────────────────────────────────────────
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            OsCaptureTheme {
                MainContent()
            }
        }
    }
}

// ─────────────────────────────────────────────
//  Permission Screen
// ─────────────────────────────────────────────
@Composable
fun MainContent() {
    val context = LocalContext.current
    var hasPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context, Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED
        )
    }
    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = { granted -> hasPermission = granted }
    )

    LaunchedEffect(Unit) {
        if (!hasPermission) launcher.launch(Manifest.permission.CAMERA)
    }

    Box(modifier = Modifier.fillMaxSize()) {
        if (hasPermission) {
            CameraScreen()
        } else {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Kamera izni gerekli.", color = Color.White, fontSize = 18.sp)
            }
        }
    }
}

// ─────────────────────────────────────────────
//  Camera Screen — Core Logic
// ─────────────────────────────────────────────
@Composable
fun CameraScreen() {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    // ── Camera references ──
    var imageCapture: ImageCapture? by remember { mutableStateOf(null) }
    val cameraExecutor = remember { Executors.newSingleThreadExecutor() }
    val handler = remember { Handler(Looper.getMainLooper()) }

    // ── State machine ──
    var captureState by remember { mutableStateOf(CaptureState.IDLE) }

    // ── UI state ──
    val showCaptureEffect = remember { mutableStateOf(false) }
    var photoCount by remember { mutableStateOf(0) }

    // ── Slider: 0.5, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10 ──
    //    Slider position 0 → 0.5s, position 1 → 1s, ... position 10 → 10s
    var sliderPosition by remember { mutableStateOf(0f) } // default 0.5s
    val delayMillis: Long = when (sliderPosition.toInt()) {
        0 -> 500L
        else -> sliderPosition.toLong() * 1000L
    }

    // ── Updated state refs for callbacks ──
    val stateRef = rememberUpdatedState(captureState)
    val delayRef = rememberUpdatedState(delayMillis)
    val imageCaptureRef = rememberUpdatedState(imageCapture)

    // ── Stillness timer runnable ──
    val stillnessRunnable = remember {
        Runnable {
            // Only fire if we are still in STILLNESS_TIMER
            if (stateRef.value == CaptureState.STILLNESS_TIMER) {
                takePhoto(context, imageCaptureRef.value, showCaptureEffect) { success ->
                    if (success) {
                        photoCount++
                        captureState = CaptureState.LOCKED
                        Log.d(TAG, "State -> LOCKED (photo taken)")
                    } else {
                        // On failure, go back to waiting
                        captureState = CaptureState.WAITING_FOR_MOTION
                    }
                }
            }
        }
    }

    // ── Motion callback (called from analyzer thread, posts to main) ──
    val onMotionCallback: () -> Unit = {
        when (stateRef.value) {
            CaptureState.WAITING_FOR_MOTION -> {
                captureState = CaptureState.MOTION_ACTIVE
                Log.d(TAG, "State -> MOTION_ACTIVE (first motion)")
            }
            CaptureState.MOTION_ACTIVE -> {
                // Already in motion, nothing to do — stays active
            }
            CaptureState.STILLNESS_TIMER -> {
                // New motion while counting down — reset timer
                handler.removeCallbacks(stillnessRunnable)
                captureState = CaptureState.MOTION_ACTIVE
                Log.d(TAG, "State -> MOTION_ACTIVE (timer reset)")
            }
            CaptureState.LOCKED -> {
                // New motion after capture — unlock!
                captureState = CaptureState.MOTION_ACTIVE
                Log.d(TAG, "State -> MOTION_ACTIVE (unlocked)")
            }
            CaptureState.IDLE -> { /* ignore */ }
        }
    }
    val currentOnMotion by rememberUpdatedState(onMotionCallback)

    // ── Stillness callback (called when analyzer sees no motion) ──
    val onStillnessCallback: () -> Unit = {
        if (stateRef.value == CaptureState.MOTION_ACTIVE) {
            captureState = CaptureState.STILLNESS_TIMER
            handler.removeCallbacks(stillnessRunnable)
            handler.postDelayed(stillnessRunnable, delayRef.value)
            Log.d(TAG, "State -> STILLNESS_TIMER (delay=${delayRef.value}ms)")
        }
    }
    val currentOnStillness by rememberUpdatedState(onStillnessCallback)

    // ── Cleanup ──
    DisposableEffect(Unit) {
        onDispose {
            cameraExecutor.shutdown()
            handler.removeCallbacksAndMessages(null)
        }
    }

    // ── CameraX setup ──
    val previewView = remember { PreviewView(context) }

    LaunchedEffect(lifecycleOwner) {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()

            val preview = Preview.Builder().build().also {
                it.setSurfaceProvider(previewView.surfaceProvider)
            }

            val capture = ImageCapture.Builder().build()
            imageCapture = capture

            val analyzer = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()
                .also {
                    it.setAnalyzer(cameraExecutor, MotionAnalyzer(
                        onMotion = { currentOnMotion() },
                        onStillness = { currentOnStillness() }
                    ))
                }

            try {
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(
                    lifecycleOwner,
                    CameraSelector.DEFAULT_BACK_CAMERA,
                    preview, capture, analyzer
                )
                Log.d(TAG, "Camera bound to lifecycle")
            } catch (e: Exception) {
                Log.e(TAG, "Camera bind failed", e)
            }
        }, ContextCompat.getMainExecutor(context))
    }

    // ─────────────────────────────────────────
    //  UI
    // ─────────────────────────────────────────
    Box(modifier = Modifier.fillMaxSize()) {
        // Camera preview
        AndroidView({ previewView }, modifier = Modifier.fillMaxSize())

        // State indicator — top center
        val stateLabel = when (captureState) {
            CaptureState.IDLE -> "⏸ Devre Dışı"
            CaptureState.WAITING_FOR_MOTION -> "👁 Hareket Bekleniyor"
            CaptureState.MOTION_ACTIVE -> "🔴 Hareket Algılandı"
            CaptureState.STILLNESS_TIMER -> "⏱ Zamanlayıcı Çalışıyor"
            CaptureState.LOCKED -> "🔒 Kilitli — Yeni Hareket Bekleniyor"
        }
        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 48.dp)
                .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(12.dp))
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            Text(stateLabel, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Medium)
        }

        // Photo counter — top right
        if (photoCount > 0) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 48.dp, end = 16.dp)
                    .background(Color(0xFF4CAF50).copy(alpha = 0.8f), RoundedCornerShape(12.dp))
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            ) {
                Text("📸 $photoCount", color = Color.White, fontSize = 14.sp)
            }
        }

        // Bottom controls
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .background(Color.Black.copy(alpha = 0.6f))
                .padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 36.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Delay label
            val delayText = if (sliderPosition.toInt() == 0) "0.5s" else "${sliderPosition.toInt()}s"
            Text("Bekleme Süresi: $delayText", color = Color.White, fontSize = 16.sp)

            // Slider
            Slider(
                value = sliderPosition,
                onValueChange = { sliderPosition = it },
                valueRange = 0f..10f,
                steps = 9,   // 11 values: 0,1,2,3,4,5,6,7,8,9,10
                enabled = captureState == CaptureState.IDLE,
                colors = SliderDefaults.colors(
                    thumbColor = Color.White,
                    activeTrackColor = Color(0xFF4CAF50)
                )
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Start / Stop button
            val isActive = captureState != CaptureState.IDLE
            Button(
                onClick = {
                    if (isActive) {
                        // ── STOP ──
                        handler.removeCallbacksAndMessages(null)
                        captureState = CaptureState.IDLE
                        Log.d(TAG, "State -> IDLE (stopped by user)")
                    } else {
                        // ── START ──
                        captureState = CaptureState.WAITING_FOR_MOTION
                        Log.d(TAG, "State -> WAITING_FOR_MOTION (started)")
                    }
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isActive) Color(0xFFF44336) else Color(0xFF4CAF50)
                ),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
            ) {
                Text(
                    if (isActive) "⏹ Durdur" else "▶ Başlat",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        // Flash effect on capture
        if (showCaptureEffect.value) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.White.copy(alpha = 0.5f))
            )
            LaunchedEffect(showCaptureEffect.value) {
                if (showCaptureEffect.value) {
                    delay(100)
                    showCaptureEffect.value = false
                }
            }
        }
    }
}

// ─────────────────────────────────────────────
//  Motion Analyzer — Pixel-Diff Based
// ─────────────────────────────────────────────
//
//  Compares consecutive Y-plane frames pixel by pixel (sampled).
//  Calls onMotion() when significant change detected,
//  calls onStillness() when the scene is stable.
//
private class MotionAnalyzer(
    private val onMotion: () -> Unit,
    private val onStillness: () -> Unit
) : ImageAnalysis.Analyzer {

    private var lastFrameData: ByteArray? = null
    private val pixelThreshold = 30      // min intensity diff to count as "changed"
    private val motionRatioThreshold = 0.03  // 3% of sampled pixels must change
    private var consecutiveStillFrames = 0
    private val stillFramesRequired = 3  // need 3 still frames in a row to trigger

    private fun ByteBuffer.toByteArray(): ByteArray {
        rewind()
        val data = ByteArray(remaining())
        get(data)
        return data
    }

    @ExperimentalGetImage
    override fun analyze(image: ImageProxy) {
        val buffer = image.planes[0].buffer
        val currentData = buffer.toByteArray()

        val previous = lastFrameData
        if (previous != null && previous.size == currentData.size) {
            val totalSampled = currentData.size / 4
            var changedPixels = 0

            for (i in currentData.indices step 4) {
                val diff = abs(
                    (currentData[i].toInt() and 0xFF) - (previous[i].toInt() and 0xFF)
                )
                if (diff > pixelThreshold) changedPixels++
            }

            val ratio = changedPixels.toDouble() / totalSampled

            if (ratio > motionRatioThreshold) {
                consecutiveStillFrames = 0
                onMotion()
            } else {
                consecutiveStillFrames++
                if (consecutiveStillFrames >= stillFramesRequired) {
                    onStillness()
                }
            }
        }

        lastFrameData = currentData
        image.close()
    }
}

// ─────────────────────────────────────────────
//  Photo Capture Helper
// ─────────────────────────────────────────────
private fun takePhoto(
    context: Context,
    imageCapture: ImageCapture?,
    showCaptureEffect: MutableState<Boolean>,
    onResult: (Boolean) -> Unit
) {
    val capture = imageCapture ?: run {
        onResult(false)
        return
    }

    val name = SimpleDateFormat(FILENAME_FORMAT, Locale.US).format(System.currentTimeMillis())
    val contentValues = ContentValues().apply {
        put(MediaStore.MediaColumns.DISPLAY_NAME, name)
        put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
    }

    val outputOptions = ImageCapture.OutputFileOptions.Builder(
        context.contentResolver,
        MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
        contentValues
    ).build()

    capture.takePicture(
        outputOptions,
        ContextCompat.getMainExecutor(context),
        object : ImageCapture.OnImageSavedCallback {
            override fun onError(exc: ImageCaptureException) {
                Log.e(TAG, "Photo capture failed: ${exc.message}", exc)
                onResult(false)
            }

            override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                showCaptureEffect.value = true
                Toast.makeText(context, "📸 Fotoğraf çekildi!", Toast.LENGTH_SHORT).show()
                Log.d(TAG, "Photo saved: ${output.savedUri}")
                onResult(true)
            }
        }
    )
}
