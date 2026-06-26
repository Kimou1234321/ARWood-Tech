package com.algerianwood.app.ui.camera

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FlashOff
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.exifinterface.media.ExifInterface
import com.algerianwood.app.data.SessionData
import com.algerianwood.app.ui.theme.LocalAppPalette
import java.io.File
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

@Composable
fun CameraScreen(
    onImageCaptured: () -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context, Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        hasCameraPermission = isGranted
    }

    LaunchedEffect(Unit) {
        if (!hasCameraPermission) {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    if (hasCameraPermission) {
        CameraContent(onImageCaptured = onImageCaptured, onBack = onBack)
    } else {
        val palette = LocalAppPalette.current
        val colors = MaterialTheme.colorScheme
        // ── Permission denied UI ──
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(colors.background)
                .systemBarsPadding(),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(32.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.CameraAlt,
                    contentDescription = null,
                    tint = colors.onSurfaceVariant,
                    modifier = Modifier.size(64.dp)
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "Camera Permission Required",
                    style = MaterialTheme.typography.titleLarge,
                    color = colors.onBackground,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Please grant camera permission to take photos of logs",
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(24.dp))
                Button(
                    onClick = { permissionLauncher.launch(Manifest.permission.CAMERA) },
                    colors = ButtonDefaults.buttonColors(containerColor = palette.forestGreen)
                ) {
                    Text("Grant Permission")
                }
                Spacer(modifier = Modifier.height(8.dp))
                TextButton(onClick = onBack) {
                    Text("Go Back", color = colors.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun CameraContent(
    onImageCaptured: () -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current

    val lifecycleOwner = LocalLifecycleOwner.current

    val palette = LocalAppPalette.current
    val colors = MaterialTheme.colorScheme

    val previewView = remember { PreviewView(context) }
    var isCapturing by remember { mutableStateOf(false) }
    var captureError by remember { mutableStateOf<String?>(null) }
    var boundCamera by remember { mutableStateOf<Camera?>(null) }
    var zoomRatio by remember { mutableFloatStateOf(1f) }
    var flashEnabled by remember { mutableStateOf(true) }
    var imageCaptureUseCase by remember { mutableStateOf<ImageCapture?>(null) }

    LaunchedEffect(flashEnabled) {
        try {
            val cameraProvider = context.getCameraProvider()
            val preview = Preview.Builder().build().also {
                it.setSurfaceProvider(previewView.surfaceProvider)
            }
            val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
            val capture = ImageCapture.Builder()
                .setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY)
                .setFlashMode(
                    if (flashEnabled) ImageCapture.FLASH_MODE_ON else ImageCapture.FLASH_MODE_OFF
                )
                .build()
            imageCaptureUseCase = capture

            cameraProvider.unbindAll()
            val camera = cameraProvider.bindToLifecycle(
                lifecycleOwner,
                cameraSelector,
                preview,
                capture
            )
            boundCamera = camera
            zoomRatio = camera.cameraInfo.zoomState.value?.zoomRatio ?: 1f
        } catch (e: Exception) {
            Log.e("CameraScreen", "Camera bind failed", e)
            captureError = "Could not start camera: ${e.message}"
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        // Camera preview with pinch zoom
        AndroidView(
            factory = { previewView },
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(boundCamera) {
                    val camera = boundCamera ?: return@pointerInput
                    awaitEachGesture {
                        do {
                            val event = awaitPointerEvent()
                            val pressed = event.changes.filter { it.pressed }
                            if (pressed.size >= 2) {
                                val zoomChange = event.calculateZoom()
                                val zoomState = camera.cameraInfo.zoomState.value
                                val minZoom = zoomState?.minZoomRatio ?: 1f
                                val maxZoom = zoomState?.maxZoomRatio ?: 1f
                                val current = zoomState?.zoomRatio ?: zoomRatio
                                val newRatio = (current * zoomChange).coerceIn(minZoom, maxZoom)
                                camera.cameraControl.setZoomRatio(newRatio)
                                zoomRatio = newRatio
                                pressed.forEach { it.consume() }
                            }
                        } while (event.changes.any { it.pressed })
                    }
                }
        )

        // ── Top bar ──
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = onBack,
                modifier = Modifier
                    .size(40.dp)
                    .background(
                        color = colors.surface.copy(alpha = 0.78f),
                        shape = CircleShape
                    )
            ) {
                Icon(
                    imageVector = Icons.Filled.Close,
                    contentDescription = "Close",
                    tint = colors.onBackground
                )
            }

            Spacer(modifier = Modifier.weight(1f))

            if (boundCamera?.cameraInfo?.hasFlashUnit() == true) {
                IconButton(
                    onClick = { flashEnabled = !flashEnabled },
                    modifier = Modifier
                        .size(40.dp)
                        .background(
                            color = colors.surface.copy(alpha = 0.78f),
                            shape = CircleShape
                        )
                ) {
                    Icon(
                        imageVector = if (flashEnabled) Icons.Filled.FlashOn else Icons.Filled.FlashOff,
                        contentDescription = if (flashEnabled) "Flash on" else "Flash off",
                        tint = if (flashEnabled) palette.goldenAmber else colors.onBackground
                    )
                }
            }
        }

        // ── Error message ──
        captureError?.let { error ->
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(32.dp)
                    .background(colors.background.copy(alpha = 0.82f), CircleShape)
                    .padding(16.dp)
            ) {
                Text(text = error, color = colors.onBackground, textAlign = TextAlign.Center)
            }
        }

        // ── Bottom controls ──
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .background(
                    brush = Brush.verticalGradient(
                        colors = listOf(Color.Transparent, colors.background.copy(alpha = 0.7f))
                    )
                )
                .navigationBarsPadding()
                .padding(bottom = 32.dp, top = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = String.format("%.1fx — Pinch to zoom", zoomRatio),
                style = MaterialTheme.typography.labelLarge,
                color = colors.onBackground,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.padding(bottom = 16.dp)
            )

            // Capture button
            IconButton(
                onClick = {
                    if (isCapturing) return@IconButton
                    val capture = imageCaptureUseCase ?: return@IconButton
                    isCapturing = true
                    captureError = null

                    val photoFile = File(
                        context.cacheDir,
                        "capture_${System.currentTimeMillis()}.jpg"
                    )
                    val outputOptions = ImageCapture.OutputFileOptions.Builder(photoFile).build()

                    capture.takePicture(
                        outputOptions,
                        ContextCompat.getMainExecutor(context),
                        object : ImageCapture.OnImageSavedCallback {
                            override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                                val rawBitmap = BitmapFactory.decodeFile(photoFile.absolutePath)
                                val bitmap = rawBitmap?.let {
                                    val orientation = try {
                                        ExifInterface(photoFile.absolutePath).getAttributeInt(
                                            ExifInterface.TAG_ORIENTATION,
                                            ExifInterface.ORIENTATION_NORMAL
                                        )
                                    } catch (_: Throwable) {
                                        ExifInterface.ORIENTATION_NORMAL
                                    }
                                    rotateBitmapByExif(it, orientation)
                                }
                                photoFile.delete()
                                if (bitmap != null) {
                                    SessionData.imageBitmap = bitmap
                                    onImageCaptured()
                                } else {
                                    captureError = "Failed to decode captured photo"
                                }
                                isCapturing = false
                            }

                            override fun onError(exception: ImageCaptureException) {
                                photoFile.delete()
                                Log.e("CameraScreen", "Capture failed", exception)
                                captureError = "Capture failed: ${exception.message}"
                                isCapturing = false
                            }
                        }
                    )
                },
                modifier = Modifier.size(76.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(76.dp)
                        .border(4.dp, colors.onBackground, CircleShape)
                        .padding(6.dp)
                        .background(
                            if (isCapturing) colors.surface else PaleCapture,
                            CircleShape
                        )
                )
            }
        }

        // ── Capture loading overlay ──
        if (isCapturing) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(colors.background.copy(alpha = 0.58f)),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = palette.forestGreenLight)
            }
        }
    }
}

private val PaleCapture = Color(0xFFF4E4CD)

private fun rotateBitmapByExif(bitmap: android.graphics.Bitmap, orientation: Int): android.graphics.Bitmap {
    val matrix = Matrix()
    when (orientation) {
        ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
        ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
        ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
        ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.preScale(-1f, 1f)
        ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.preScale(1f, -1f)
        else -> return bitmap
    }
    return try {
        android.graphics.Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    } catch (_: Throwable) {
        bitmap
    }
}

private suspend fun android.content.Context.getCameraProvider(): ProcessCameraProvider {
    return suspendCoroutine { continuation ->
        ProcessCameraProvider.getInstance(this).also { future ->
            future.addListener(
                { continuation.resume(future.get()) },
                ContextCompat.getMainExecutor(this)
            )
        }
    }
}
