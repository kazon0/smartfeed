package com.example.smartfeedandroid.ui.profile

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.smartfeedandroid.R
import com.example.smartfeedandroid.ui.common.JournalBlue
import com.example.smartfeedandroid.ui.common.JournalGreen
import com.example.smartfeedandroid.ui.common.JournalInk
import com.example.smartfeedandroid.ui.common.JournalInkLight
import com.example.smartfeedandroid.ui.common.JournalLine
import com.example.smartfeedandroid.ui.common.JournalPaper
import com.example.smartfeedandroid.ui.common.JournalPink
import com.example.smartfeedandroid.ui.common.JournalTerra
import com.example.smartfeedandroid.ui.common.JournalYellow
import com.example.smartfeedandroid.ui.theme.KalamFontFamily

private enum class AuthMode { Login, Register }

@Composable
fun AuthScreen(
    uiState: AuthUiState,
    onLogin: (String, String) -> Unit,
    onRegister: (String, String, String) -> Unit,
    onClearError: () -> Unit,
    modifier: Modifier = Modifier
) {
    var mode by rememberSaveable { mutableStateOf(AuthMode.Login) }
    var email by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    var displayName by rememberSaveable { mutableStateOf("") }
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val interactionSource = remember { MutableInteractionSource() }
    val submit = {
        focusManager.clearFocus()
        keyboardController?.hide()
        if (mode == AuthMode.Login) {
            onLogin(email, password)
        } else {
            onRegister(email, password, displayName)
        }
    }

    Scaffold(modifier = modifier.fillMaxSize(), containerColor = JournalPaper) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(JournalPaper)
                .imePadding()
                .clickable(
                    interactionSource = interactionSource,
                    indication = null
                ) { focusManager.clearFocus() },
            contentAlignment = Alignment.TopCenter
        ) {
            AuthBackground()
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 440.dp)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 24.dp)
                    .padding(top = 72.dp, bottom = 28.dp),
                verticalArrangement = Arrangement.spacedBy(22.dp)
            ) {
                AuthHeader()
                AuthModeSwitch(
                    mode = mode,
                    onModeChange = {
                        mode = it
                        onClearError()
                    }
                )
                AuthFormCard(
                    mode = mode,
                    email = email,
                    password = password,
                    displayName = displayName,
                    isSubmitting = uiState.isSubmitting,
                    errorMessage = uiState.errorMessage,
                    onEmailChange = { email = it },
                    onPasswordChange = { password = it },
                    onDisplayNameChange = { displayName = it },
                    onSubmit = submit
                )
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = "登录即代表同意用户协议和隐私政策",
                    modifier = Modifier.align(Alignment.CenterHorizontally),
                    color = JournalInkLight.copy(alpha = 0.72f),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
private fun AuthBackground() {
    Canvas(modifier = Modifier.fillMaxSize()) {
        drawCircle(
            color = JournalYellow.copy(alpha = 0.45f),
            radius = 145.dp.toPx(),
            center = Offset(size.width + 28.dp.toPx(), 70.dp.toPx())
        )
        drawCircle(
            color = JournalBlue.copy(alpha = 0.34f),
            radius = 170.dp.toPx(),
            center = Offset(-34.dp.toPx(), size.height - 70.dp.toPx())
        )
        drawCircle(
            color = JournalGreen.copy(alpha = 0.24f),
            radius = 84.dp.toPx(),
            center = Offset(size.width * 0.75f, size.height * 0.78f)
        )
    }
    Text(
        text = "✦",
        modifier = Modifier
            .offset(x = 322.dp, y = 106.dp)
            .rotate(14f),
        color = JournalTerra.copy(alpha = 0.48f),
        fontFamily = KalamFontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 17.sp
    )
    Text(
        text = "✦",
        modifier = Modifier
            .offset(x = 46.dp, y = 670.dp)
            .rotate(-9f),
        color = JournalYellow.copy(alpha = 0.78f),
        fontFamily = KalamFontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 20.sp
    )
}

@Composable
private fun AuthHeader() {
    Column(modifier = Modifier.padding(start = 2.dp)) {
        Text(
            text = "Welcome back,",
            modifier = Modifier.rotate(-2f),
            color = JournalTerra,
            fontFamily = KalamFontFamily,
            fontWeight = FontWeight.Bold,
            style = MaterialTheme.typography.titleMedium
        )
        Box(modifier = Modifier.padding(top = 2.dp)) {
            Text(
                text = stringResource(R.string.app_name),
                color = JournalInk,
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.ExtraBold
            )
            Canvas(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 1.dp)
                    .size(width = 78.dp, height = 9.dp)
            ) {
                val path = Path()
                val centerY = size.height * 0.58f
                val step = size.width / 4f
                path.moveTo(0f, centerY)
                repeat(4) { index ->
                    val startX = step * index
                    path.cubicTo(
                        startX + step * 0.25f,
                        centerY - 5.dp.toPx(),
                        startX + step * 0.75f,
                        centerY + 5.dp.toPx(),
                        startX + step,
                        centerY
                    )
                }
                drawPath(path, color = JournalTerra, style = Stroke(width = 3.dp.toPx()))
            }
        }
        Text(
            text = stringResource(R.string.auth_subtitle),
            modifier = Modifier
                .padding(top = 10.dp)
                .width(250.dp),
            color = JournalInkLight,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            lineHeight = 20.sp
        )
    }
}

@Composable
private fun AuthModeSwitch(mode: AuthMode, onModeChange: (AuthMode) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(JournalInk.copy(alpha = 0.06f), RoundedCornerShape(28.dp))
            .border(1.dp, JournalInk.copy(alpha = 0.05f), RoundedCornerShape(28.dp))
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        AuthMode.entries.forEach { item ->
            val selected = mode == item
            Row(
                modifier = Modifier
                    .weight(1f)
                    .height(42.dp)
                    .clip(RoundedCornerShape(23.dp))
                    .background(if (selected) Color.White else Color.Transparent)
                    .shadow(if (selected) 1.dp else 0.dp, RoundedCornerShape(23.dp), clip = false)
                    .clickable { onModeChange(item) },
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (selected) {
                    Text(
                        text = "✓",
                        color = JournalTerra,
                        fontWeight = FontWeight.ExtraBold,
                        modifier = Modifier.padding(end = 7.dp)
                    )
                }
                Text(
                    text = stringResource(if (item == AuthMode.Login) R.string.login else R.string.register),
                    color = if (selected) JournalInk else JournalInkLight,
                    fontWeight = FontWeight.ExtraBold,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
    }
}

@Composable
private fun AuthFormCard(
    mode: AuthMode,
    email: String,
    password: String,
    displayName: String,
    isSubmitting: Boolean,
    errorMessage: String?,
    onEmailChange: (String) -> Unit,
    onPasswordChange: (String) -> Unit,
    onDisplayNameChange: (String) -> Unit,
    onSubmit: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .rotate(0.4f)
    ) {
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .offset(x = (-34).dp, y = (-8).dp)
                .rotate(3f)
                .size(width = 58.dp, height = 17.dp)
                .background(JournalYellow.copy(alpha = 0.68f), RoundedCornerShape(3.dp))
                .drawBehind {
                    drawRoundRect(
                        color = Color.White.copy(alpha = 0.28f),
                        style = Stroke(
                            width = 1.dp.toPx(),
                            pathEffect = PathEffect.dashPathEffect(floatArrayOf(7f, 5f))
                        )
                    )
                }
        )
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .shadow(4.dp, RoundedCornerShape(24.dp), clip = false)
                .background(Color.White.copy(alpha = 0.76f), RoundedCornerShape(24.dp))
                .border(1.dp, Color.White.copy(alpha = 0.88f), RoundedCornerShape(24.dp))
                .padding(22.dp),
            verticalArrangement = Arrangement.spacedBy(15.dp)
        ) {
            if (mode == AuthMode.Register) {
                JournalTextField(
                    value = displayName,
                    onValueChange = onDisplayNameChange,
                    placeholder = stringResource(R.string.display_name),
                    glyph = "✎",
                    imeAction = ImeAction.Next
                )
            }
            JournalTextField(
                value = email,
                onValueChange = onEmailChange,
                placeholder = stringResource(R.string.email),
                glyph = "✉",
                keyboardType = KeyboardType.Email,
                imeAction = ImeAction.Next
            )
            JournalTextField(
                value = password,
                onValueChange = onPasswordChange,
                placeholder = stringResource(R.string.password),
                glyph = "●",
                keyboardType = KeyboardType.Password,
                imeAction = ImeAction.Done,
                isPassword = true,
                supportingText = if (mode == AuthMode.Register) {
                    stringResource(R.string.password_requirement)
                } else {
                    null
                },
                onDone = onSubmit
            )
            if (mode == AuthMode.Login) {
                Text(
                    text = "Forgot password?",
                    modifier = Modifier
                        .align(Alignment.End)
                        .rotate(1f),
                    color = JournalTerra,
                    fontFamily = KalamFontFamily,
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.labelLarge
                )
            }
            errorMessage?.let {
                Text(
                    text = it,
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(JournalPink.copy(alpha = 0.35f), RoundedCornerShape(12.dp))
                        .padding(horizontal = 12.dp, vertical = 9.dp),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Bold
                )
            }
            AuthSubmitButton(
                mode = mode,
                isSubmitting = isSubmitting,
                enabled = email.isNotBlank() && password.length >= 8 && !isSubmitting,
                onClick = onSubmit
            )
        }
    }
}

@Composable
private fun JournalTextField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    glyph: String,
    keyboardType: KeyboardType = KeyboardType.Text,
    imeAction: ImeAction,
    isPassword: Boolean = false,
    supportingText: String? = null,
    onDone: (() -> Unit)? = null
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        placeholder = { Text(placeholder, color = JournalInkLight.copy(alpha = 0.7f)) },
        leadingIcon = {
            Text(
                text = glyph,
                color = JournalInkLight.copy(alpha = 0.66f),
                fontFamily = KalamFontFamily,
                fontWeight = FontWeight.Bold
            )
        },
        supportingText = supportingText?.let { text -> { Text(text) } },
        singleLine = true,
        visualTransformation = if (isPassword) PasswordVisualTransformation() else androidx.compose.ui.text.input.VisualTransformation.None,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType, imeAction = imeAction),
        keyboardActions = KeyboardActions(onDone = { onDone?.invoke() }),
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedContainerColor = Color.White,
            unfocusedContainerColor = Color.White.copy(alpha = 0.82f),
            focusedBorderColor = JournalBlue.copy(alpha = 0.75f),
            unfocusedBorderColor = JournalInk.copy(alpha = 0.06f),
            cursorColor = JournalTerra,
            focusedTextColor = JournalInk,
            unfocusedTextColor = JournalInk
        )
    )
}

@Composable
private fun AuthSubmitButton(
    mode: AuthMode,
    isSubmitting: Boolean,
    enabled: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 3.dp)
            .rotate(-0.5f)
            .clip(RoundedCornerShape(18.dp))
            .background(if (enabled) JournalInk else JournalInk.copy(alpha = 0.42f))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(vertical = 15.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (isSubmitting) {
            CircularProgressIndicator(
                modifier = Modifier.size(20.dp),
                strokeWidth = 2.dp,
                color = JournalPaper
            )
        } else {
            Text(
                text = "↪",
                color = JournalPaper,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(end = 8.dp)
            )
            Text(
                text = stringResource(
                    if (mode == AuthMode.Login) R.string.auth_submit_login else R.string.create_account
                ),
                color = JournalPaper,
                fontWeight = FontWeight.ExtraBold,
                style = MaterialTheme.typography.bodyLarge
            )
        }
    }
}

@Composable
fun SessionLoadingScreen(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(JournalPaper),
        contentAlignment = Alignment.Center
    ) {
        CircularProgressIndicator(color = JournalTerra)
    }
}

@Preview(showBackground = true)
@Composable
private fun AuthScreenPreview() {
    AuthScreen(
        uiState = AuthUiState(),
        onLogin = { _, _ -> },
        onRegister = { _, _, _ -> },
        onClearError = {}
    )
}
