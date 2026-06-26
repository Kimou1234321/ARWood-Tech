package com.algerianwood.app.ui.results

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.algerianwood.app.data.LocalAnalysisRepository
import com.algerianwood.app.data.SessionData
import com.algerianwood.app.ui.common.ZoomableImageCanvas
import com.algerianwood.app.ui.theme.LocalAppPalette

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SavedAnalysisDetailScreen(onBack: () -> Unit) {
    val palette = LocalAppPalette.current
    val colors = MaterialTheme.colorScheme
    val context = androidx.compose.ui.platform.LocalContext.current
    val repo = remember(context) { LocalAnalysisRepository(context) }
    val username = SessionData.currentUsername
    val id = SessionData.selectedSavedAnalysisId
    val snapshot = remember(username, id) { id?.let { repo.load(username, it) } }
    var currentMode by remember { mutableStateOf(ResultImageMode.ORIGINAL) }
    var resultZoom by remember { mutableFloatStateOf(1f) }
    var resultPanX by remember { mutableFloatStateOf(0f) }
    var resultPanY by remember { mutableFloatStateOf(0f) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Saved Analysis") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = colors.background,
                    titleContentColor = colors.onBackground,
                    navigationIconContentColor = colors.onBackground
                )
            )
        },
        containerColor = colors.background
    ) { padding ->
        if (snapshot == null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Text("Saved analysis not found", color = colors.onSurfaceVariant)
            }
            return@Scaffold
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            val currentBmp = when (currentMode) {
                ResultImageMode.ORIGINAL -> snapshot.originalBitmap
                ResultImageMode.MASK -> snapshot.maskBitmap
                ResultImageMode.YOLO -> snapshot.yoloBitmap
            }
            ZoomableImageCanvas(
                bitmap = currentBmp,
                zoom = resultZoom,
                onZoomChange = { resultZoom = it },
                panX = resultPanX,
                onPanXChange = { resultPanX = it },
                panY = resultPanY,
                onPanYChange = { resultPanY = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp)
            )

            DirectModeChooser(currentMode = currentMode, onModeSelected = { currentMode = it })

            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
            ) {
                Spacer(modifier = Modifier.height(12.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    SavedStatCard("Logs Detected", snapshot.totalLogs.toString(), Modifier.weight(1f))
                    SavedStatCard("Avg Diameter", String.format("%.1f cm", snapshot.averageDiameterCm), Modifier.weight(1f))
                }
                Spacer(modifier = Modifier.height(12.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    SavedStatCard("Total Area", String.format("%.2f m²", snapshot.totalAreaCm2 / 10000f), Modifier.weight(1f))
                    SavedStatCard("Total Volume", String.format("%.3f m³", snapshot.totalVolumeM3), Modifier.weight(1f))
                }

                Spacer(modifier = Modifier.height(20.dp))
                Button(
                    onClick = onBack,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = palette.forestGreen)
                ) { Text("Back") }
                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }
}

@Composable
private fun SavedStatCard(
    title: String,
    value: String,
    modifier: Modifier = Modifier
) {
    val palette = LocalAppPalette.current
    val colors = MaterialTheme.colorScheme
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = colors.surfaceVariant)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(title, color = colors.onSurfaceVariant)
            Spacer(modifier = Modifier.height(8.dp))
            Text(value, color = palette.forestGreenLight, fontWeight = FontWeight.Bold)
        }
    }
}
