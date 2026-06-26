package com.algerianwood.app.ui.login

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.algerianwood.app.data.LocalUser
import com.algerianwood.app.data.LocalUserRepository
import com.algerianwood.app.ui.theme.LocalAppPalette

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RegisterScreen(
    onBack: () -> Unit,
    onCreated: () -> Unit
) {
    val palette = LocalAppPalette.current
    val colors = MaterialTheme.colorScheme
    val context = LocalContext.current
    val userRepository = remember(context) { LocalUserRepository(context) }

    var firstName by remember { mutableStateOf("") }
    var familyName by remember { mutableStateOf("") }
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    fun createAccount() {
        val f = firstName.trim()
        val l = familyName.trim()
        val u = username.trim()
        when {
            f.isBlank() || l.isBlank() || u.isBlank() || password.isBlank() || confirmPassword.isBlank() ->
                errorMessage = "Please fill all fields"
            password != confirmPassword ->
                errorMessage = "Passwords do not match"
            else -> {
                val result = userRepository.createUser(
                    LocalUser(
                        firstName = f,
                        familyName = l,
                        username = u,
                        password = password
                    )
                )
                if (result.isSuccess) {
                    onCreated()
                } else {
                    errorMessage = result.exceptionOrNull()?.message ?: "Failed to create account"
                }
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Create Account", fontWeight = FontWeight.SemiBold) },
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
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .systemBarsPadding()
                .imePadding()
                .padding(20.dp)
                .verticalScroll(rememberScrollState())
                .background(colors.background),
            verticalArrangement = Arrangement.Top
        ) {
            OutlinedTextField(
                value = firstName,
                onValueChange = { firstName = it; errorMessage = null },
                label = { Text("Name") },
                modifier = Modifier.fillMaxWidth(),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = palette.forestGreenLight,
                    focusedLabelColor = palette.forestGreenLight,
                    cursorColor = palette.forestGreenLight
                )
            )
            Spacer(modifier = Modifier.height(10.dp))
            OutlinedTextField(
                value = familyName,
                onValueChange = { familyName = it; errorMessage = null },
                label = { Text("Family Name") },
                modifier = Modifier.fillMaxWidth(),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = palette.forestGreenLight,
                    focusedLabelColor = palette.forestGreenLight,
                    cursorColor = palette.forestGreenLight
                )
            )
            Spacer(modifier = Modifier.height(10.dp))
            OutlinedTextField(
                value = username,
                onValueChange = { username = it; errorMessage = null },
                label = { Text("Username (unique)") },
                modifier = Modifier.fillMaxWidth(),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = palette.forestGreenLight,
                    focusedLabelColor = palette.forestGreenLight,
                    cursorColor = palette.forestGreenLight
                )
            )
            Spacer(modifier = Modifier.height(10.dp))
            OutlinedTextField(
                value = password,
                onValueChange = { password = it; errorMessage = null },
                label = { Text("Password") },
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth(),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = palette.forestGreenLight,
                    focusedLabelColor = palette.forestGreenLight,
                    cursorColor = palette.forestGreenLight
                )
            )
            Spacer(modifier = Modifier.height(10.dp))
            OutlinedTextField(
                value = confirmPassword,
                onValueChange = { confirmPassword = it; errorMessage = null },
                label = { Text("Confirm Password") },
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth(),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = palette.forestGreenLight,
                    focusedLabelColor = palette.forestGreenLight,
                    cursorColor = palette.forestGreenLight
                )
            )

            errorMessage?.let {
                Spacer(modifier = Modifier.height(10.dp))
                Text(it, color = palette.errorRed, style = MaterialTheme.typography.bodyMedium)
            }

            Spacer(modifier = Modifier.height(16.dp))
            Button(
                onClick = { createAccount() },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = palette.forestGreen),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("Create Account", fontWeight = FontWeight.Bold, color = colors.onBackground)
            }

            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Create account locally on this phone",
                style = MaterialTheme.typography.bodySmall,
                color = colors.onSurfaceVariant
            )
        }
    }
}
