package com.algerianwood.app.ui.results

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.algerianwood.app.data.LocalAnalysisRepository
import com.algerianwood.app.data.SessionData
import com.algerianwood.app.ui.theme.LocalAppPalette
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SavedAnalysesScreen(
    onBack: () -> Unit,
    onOpenSaved: (Long) -> Unit
) {
    val palette = LocalAppPalette.current
    val colors = MaterialTheme.colorScheme
    val context = LocalContext.current
    val repo = remember(context) { LocalAnalysisRepository(context) }
    val username = SessionData.currentUsername
    var savedItems by remember(username) { mutableStateOf(repo.listForUser(username)) }
    var pendingDeleteId by remember { mutableStateOf<Long?>(null) }
    val fmt = remember { SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Saved Analyses", fontWeight = FontWeight.SemiBold) },
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
        if (savedItems.isEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Spacer(modifier = Modifier.height(32.dp))
                Text("No saved analyses for this account", color = colors.onSurfaceVariant)
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(horizontal = 16.dp)
            ) {
                items(savedItems) { item ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 6.dp)
                            .clickable { onOpenSaved(item.id) },
                        colors = CardDefaults.cardColors(containerColor = colors.surfaceVariant)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp)
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(item.name, color = colors.onBackground, fontWeight = FontWeight.SemiBold)
                                Text(item.description, color = colors.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                            }
                            Text(
                                text = fmt.format(Date(item.savedAtMs)),
                                color = palette.forestGreenLight,
                                style = MaterialTheme.typography.labelSmall
                            )
                            IconButton(onClick = { pendingDeleteId = item.id }) {
                                Icon(
                                    imageVector = Icons.Filled.Delete,
                                    contentDescription = "Delete saved analysis",
                                    tint = colors.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }

        if (pendingDeleteId != null) {
            AlertDialog(
                onDismissRequest = { pendingDeleteId = null },
                title = { Text("Delete analysis?") },
                text = { Text("This action cannot be undone.") },
                confirmButton = {
                    Button(onClick = {
                        val id = pendingDeleteId ?: return@Button
                        repo.delete(username, id)
                        savedItems = repo.listForUser(username)
                        pendingDeleteId = null
                    }) {
                        Text("Delete")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { pendingDeleteId = null }) {
                        Text("Cancel")
                    }
                }
            )
        }
    }
}
