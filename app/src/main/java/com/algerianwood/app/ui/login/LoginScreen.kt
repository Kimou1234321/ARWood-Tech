package com.algerianwood.app.ui.login

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.Image
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.algerianwood.app.R
import com.algerianwood.app.data.LocalUserRepository
import com.algerianwood.app.data.SessionData
import com.algerianwood.app.ui.theme.LocalAppPalette

@Composable
fun LoginScreen(
    onLoginSuccess: () -> Unit,
    onCreateAccount: () -> Unit
) {
    val palette = LocalAppPalette.current
    val colors = MaterialTheme.colorScheme
    val context = LocalContext.current
    val userRepository = remember(context) { LocalUserRepository(context) }
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var showPassword by remember { mutableStateOf(false) }
    var showError by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf("") }

    val focusManager = LocalFocusManager.current

    fun attemptLogin() {
        focusManager.clearFocus()
        if (userRepository.validateCredentials(username.trim(), password)) {
            SessionData.currentUsername = username.trim()
            onLoginSuccess()
        } else {
            showError = true
            errorMessage = if (username.isBlank() || password.isBlank()) {
                "Please enter both username and password"
            } else {
                "Invalid username or password"
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                brush = if (palette.isLight) {
                    Brush.verticalGradient(
                        colors = listOf(
                            palette.background,
                            palette.surface,
                            palette.sandBrown.copy(alpha = 0.35f)
                        )
                    )
                } else {
                    Brush.verticalGradient(
                        colors = listOf(
                            colors.background,
                            colors.surface,
                            palette.woodBrownDark.copy(alpha = 0.3f)
                        )
                    )
                }
            )
            .systemBarsPadding()
            .imePadding()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // ── Logo ──
            Image(
                painter = painterResource(id = R.drawable.app_logo),
                contentDescription = "Logo",
                modifier = Modifier.size(120.dp),
                contentScale = ContentScale.Fit
            )

            Spacer(modifier = Modifier.height(16.dp))

            // ── App Title ──
            Text(
                text = "ARWood Tech",
                style = MaterialTheme.typography.displayLarge,
                color = colors.onBackground,
                fontWeight = FontWeight.Bold
            )

            Text(
                text = "Wood Volume Measurement",
                style = MaterialTheme.typography.bodyLarge,
                color = palette.textSecondary,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(48.dp))

            // ── Login Card ──
            Card(
                modifier = Modifier
                    .fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = colors.surface
                ),
                shape = RoundedCornerShape(20.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "Sign In",
                        style = MaterialTheme.typography.headlineMedium,
                        color = colors.onBackground,
                        modifier = Modifier.padding(bottom = 24.dp)
                    )

                    // Username
                    OutlinedTextField(
                        value = username,
                        onValueChange = { username = it; showError = false },
                        label = { Text("Username") },
                        leadingIcon = { Icon(Icons.Filled.Person, "Username") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = palette.forestGreen,
                            unfocusedBorderColor = palette.textSecondary.copy(alpha = 0.45f),
                            focusedLabelColor = palette.forestGreenDark,
                            unfocusedLabelColor = palette.textSecondary,
                            cursorColor = palette.forestGreen
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    // Password
                    OutlinedTextField(
                        value = password,
                        onValueChange = { password = it; showError = false },
                        label = { Text("Password") },
                        leadingIcon = { Icon(Icons.Filled.Lock, "Password") },
                        trailingIcon = {
                            IconButton(onClick = { showPassword = !showPassword }) {
                                Icon(
                                    if (showPassword) Icons.Filled.VisibilityOff
                                    else Icons.Filled.Visibility,
                                    "Toggle password visibility"
                                )
                            }
                        },
                        singleLine = true,
                        visualTransformation = if (showPassword) VisualTransformation.None
                        else PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Password,
                            imeAction = ImeAction.Done
                        ),
                        keyboardActions = KeyboardActions(onDone = { attemptLogin() }),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = palette.forestGreen,
                            unfocusedBorderColor = palette.textSecondary.copy(alpha = 0.45f),
                            focusedLabelColor = palette.forestGreenDark,
                            unfocusedLabelColor = palette.textSecondary,
                            cursorColor = palette.forestGreen
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )

                    // Error message
                    if (showError) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = errorMessage,
                            color = palette.errorRed,
                            style = MaterialTheme.typography.bodySmall,
                            textAlign = TextAlign.Center
                        )
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    // Login button
                    Button(
                        onClick = { attemptLogin() },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = palette.forestGreen),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(
                            "Login",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = colors.onPrimary
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                    TextButton(onClick = onCreateAccount) {
                        Text("Create account", color = palette.forestGreenDark)
                    }
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            Text(
                text = "v1.0.0 — Prototype",
                style = MaterialTheme.typography.labelSmall,
                color = palette.textSecondary.copy(alpha = 0.75f)
            )
        }
    }
}
