package com.algerianwood.app.ui.home

import android.graphics.BitmapFactory
import android.graphics.Matrix
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.HelpOutline
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Photo
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.exifinterface.media.ExifInterface
import com.algerianwood.app.data.SessionData
import com.algerianwood.app.ui.theme.AppThemeMode
import com.algerianwood.app.ui.theme.LocalAppPalette
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    themeMode: AppThemeMode,
    onToggleTheme: () -> Unit,
    onImageReady: () -> Unit,
    onTakePicture: () -> Unit,
    onOpenSavedAnalyses: () -> Unit
) {
    val context = LocalContext.current
    val palette = LocalAppPalette.current
    val colors = MaterialTheme.colorScheme
    val scope = rememberCoroutineScope()
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var showHowItWorks by remember { mutableStateOf(false) }
    val cameraFlashAlpha = remember { Animatable(0f) }

    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let {
            isLoading = true
            try {
                val boundsOptions = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                context.contentResolver.openInputStream(it)?.use { stream ->
                    BitmapFactory.decodeStream(stream, null, boundsOptions)
                }

                val maxDim = 2000
                var sampleSize = 1
                val w = boundsOptions.outWidth
                val h = boundsOptions.outHeight
                if (w > maxDim || h > maxDim) {
                    val halfW = w / 2
                    val halfH = h / 2
                    while ((halfW / sampleSize) >= maxDim || (halfH / sampleSize) >= maxDim) {
                        sampleSize *= 2
                    }
                }

                val decodeOptions = BitmapFactory.Options().apply { inSampleSize = sampleSize }
                context.contentResolver.openInputStream(it)?.use { stream ->
                    val decoded = BitmapFactory.decodeStream(stream, null, decodeOptions)
                    val orientation = try {
                        context.contentResolver.openInputStream(it)?.use { exifStream ->
                            ExifInterface(exifStream).getAttributeInt(
                                ExifInterface.TAG_ORIENTATION,
                                ExifInterface.ORIENTATION_NORMAL
                            )
                        } ?: ExifInterface.ORIENTATION_NORMAL
                    } catch (_: Throwable) {
                        ExifInterface.ORIENTATION_NORMAL
                    }
                    val bitmap = decoded?.let { src -> rotateBitmapByExif(src, orientation) }
                    if (bitmap != null) {
                        SessionData.imageBitmap = bitmap
                        onImageReady()
                    }
                }
            } catch (_: Exception) {
                errorMessage = "Failed to load image"
            } finally {
                isLoading = false
            }
        }
    }

    fun triggerCameraFlashThenNavigate() {
        scope.launch {
            cameraFlashAlpha.snapTo(0f)
            cameraFlashAlpha.animateTo(0.35f, tween(90))
            cameraFlashAlpha.animateTo(0f, tween(90))
            onTakePicture()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(colors.background, colors.surface)
                )
            )
            .systemBarsPadding()
    ) {
        IconButton(
            onClick = onToggleTheme,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(8.dp)
                .size(44.dp)
                .background(colors.surfaceVariant.copy(alpha = 0.92f), CircleShape)
        ) {
            Icon(
                imageVector = if (themeMode == AppThemeMode.NIGHT) {
                    Icons.Filled.LightMode
                } else {
                    Icons.Filled.DarkMode
                },
                contentDescription = if (themeMode == AppThemeMode.NIGHT) {
                    "Switch to day mode"
                } else {
                    "Switch to night mode"
                },
                tint = palette.goldenAmber
            )
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Spacer(modifier = Modifier.height(48.dp))
            Spacer(modifier = Modifier.height(16.dp))

            errorMessage?.let { msg ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = palette.errorRed.copy(alpha = 0.15f)
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = msg,
                            style = MaterialTheme.typography.bodyMedium,
                            color = palette.errorRed,
                            modifier = Modifier.weight(1f)
                        )
                        TextButton(onClick = { errorMessage = null }) {
                            Text("Dismiss", color = palette.errorRed)
                        }
                    }
                }
            }

            Card(
                onClick = { imagePickerLauncher.launch("image/*") },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(140.dp),
                colors = CardDefaults.cardColors(containerColor = colors.surfaceVariant),
                shape = RoundedCornerShape(20.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(56.dp)
                            .background(
                                color = palette.forestGreen.copy(alpha = 0.2f),
                                shape = RoundedCornerShape(16.dp)
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Photo,
                            contentDescription = "Import",
                            tint = palette.forestGreenLight,
                            modifier = Modifier.size(32.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(20.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Import Picture",
                            style = MaterialTheme.typography.titleLarge,
                            color = colors.onBackground,
                            fontWeight = FontWeight.SemiBold
                        )
                    }

                    Icon(
                        imageVector = Icons.Filled.ChevronRight,
                        contentDescription = null,
                        tint = colors.onSurfaceVariant,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(140.dp)
            ) {
                Card(
                    onClick = { triggerCameraFlashThenNavigate() },
                    modifier = Modifier.fillMaxSize(),
                    colors = CardDefaults.cardColors(containerColor = colors.surfaceVariant),
                    shape = RoundedCornerShape(20.dp),
                    elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(24.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(56.dp)
                                .background(
                                    color = palette.woodBrown.copy(alpha = 0.2f),
                                    shape = RoundedCornerShape(16.dp)
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Filled.CameraAlt,
                                contentDescription = "Camera",
                                tint = palette.woodBrownLight,
                                modifier = Modifier.size(32.dp)
                            )
                        }

                        Spacer(modifier = Modifier.width(20.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Take a Picture",
                                style = MaterialTheme.typography.titleLarge,
                                color = colors.onBackground,
                                fontWeight = FontWeight.SemiBold
                            )
                        }

                        Icon(
                            imageVector = Icons.Filled.ChevronRight,
                            contentDescription = null,
                            tint = colors.onSurfaceVariant,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }

                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .background(
                            Color.White.copy(alpha = cameraFlashAlpha.value),
                            RoundedCornerShape(20.dp)
                        )
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            Card(
                onClick = onOpenSavedAnalyses,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(86.dp),
                colors = CardDefaults.cardColors(containerColor = colors.surfaceVariant),
                shape = RoundedCornerShape(20.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(20.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Filled.Save,
                        contentDescription = "Saved Analyses",
                        tint = palette.forestGreenLight
                    )
                    Spacer(modifier = Modifier.width(14.dp))
                    Text(
                        text = "Saved Analyses",
                        style = MaterialTheme.typography.titleMedium,
                        color = colors.onBackground,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            if (showHowItWorks) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = colors.surfaceVariant.copy(alpha = 0.5f)
                    ),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(modifier = Modifier.padding(20.dp)) {
                        Text(
                            text = "How it works",
                            style = MaterialTheme.typography.titleMedium,
                            color = palette.goldenAmber,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        val steps = listOf(
                            "1. Import or take a photo of stacked logs (front view)",
                            "2. Set the scale by picking two reference points",
                            "3. Enter the real distance and log length",
                            "4. AI detects and calculates the total volume"
                        )
                        steps.forEach { step ->
                            Text(
                                text = step,
                                style = MaterialTheme.typography.bodySmall,
                                color = colors.onSurfaceVariant,
                                modifier = Modifier.padding(vertical = 2.dp)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                IconButton(onClick = { showHowItWorks = !showHowItWorks }) {
                    Icon(
                        imageVector = Icons.Filled.HelpOutline,
                        contentDescription = "How it works",
                        tint = palette.forestGreenLight
                    )
                }
            }
        }

        if (isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(colors.background.copy(alpha = 0.7f)),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = palette.forestGreenLight)
            }
        }
    }
}

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
