package com.smokegrid.app

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

private val UndoBorder = Color(0xFF4A4038)

/** Sign in with your email to sync, or see sync status and sign out. */
@Composable
fun SyncScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val version by remember { Sync.changes(context) }.collectAsState(initial = 0)
    val signedIn = remember(version) { Sync.isSignedIn(context) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .verticalScroll(rememberScrollState())
            .padding(start = 24.dp, end = 24.dp, top = 16.dp, bottom = 40.dp),
    ) {
        ScreenHeader("Sync", onBack)
        Spacer(Modifier.height(20.dp))
        when {
            !Sync.isConfigured -> Text("Sync isn't set up in this build.", style = Caption)
            signedIn -> SignedIn(version)
            else -> SignInForm()
        }
    }
}

@Composable
private fun SignInForm() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var email by rememberSaveable { mutableStateOf(Sync.email(context).orEmpty()) }
    // Kept out of saved state on purpose.
    var password by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf(Sync.lastError(context)) }
    val canSubmit = !busy && email.isNotBlank() && password.isNotEmpty()

    fun submit() {
        if (!canSubmit) return
        busy = true
        scope.launch {
            error = Sync.signIn(context, email, password)
            busy = false
            if (error == null) password = ""
        }
    }

    Text("Sign in with your email to keep your log in sync across your phones.", style = Caption)
    Spacer(Modifier.height(20.dp))
    EmberField(
        value = email,
        onValueChange = { email = it },
        label = "Email",
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Next),
    )
    Spacer(Modifier.height(12.dp))
    EmberField(
        value = password,
        onValueChange = { password = it },
        label = "Password",
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { submit() }),
        visualTransformation = PasswordVisualTransformation(),
    )
    error?.let {
        Spacer(Modifier.height(12.dp))
        Text(it, style = Caption.copy(color = Accent))
    }
    Spacer(Modifier.height(24.dp))
    PillButton(if (busy) "Signing in…" else "Sign in", enabled = canSubmit, onClick = ::submit)
}

@Composable
private fun SignedIn(version: Int) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val lastSync = remember(version) { Sync.lastSyncAt(context) }
    val error = remember(version) { Sync.lastError(context) }

    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(CardFill)
            .padding(16.dp)
    ) {
        Text("Signed in as", style = Small.copy(fontSize = 12.sp, lineHeight = 16.sp))
        Spacer(Modifier.height(4.dp))
        Text(Sync.email(context).orEmpty(), style = Label.copy(color = TextPrimary))
        Spacer(Modifier.height(10.dp))
        Box(Modifier.fillMaxWidth().height(1.dp).background(ButtonBorder))
        Spacer(Modifier.height(10.dp))
        Text(if (lastSync == 0L) "Not synced yet" else "Last synced ${describe(lastSync)}", style = Caption)
        error?.let {
            Spacer(Modifier.height(4.dp))
            Text(it, style = Caption.copy(color = Accent))
        }
    }
    Spacer(Modifier.height(24.dp))
    PillButton("Sync now", onClick = { Sync.requestSync(context, delaySeconds = 0) })
    Spacer(Modifier.height(12.dp))
    OutlinedButton(
        onClick = { scope.launch { Sync.signOut(context) } },
        modifier = Modifier.fillMaxWidth().height(56.dp),
        border = BorderStroke(1.dp, UndoBorder),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = TextPrimary),
    ) {
        Text("Sign out", style = Label)
    }
    Spacer(Modifier.height(12.dp))
    Text("Signing out keeps your log on this phone.", style = Caption)
}

private fun describe(millis: Long): String {
    val time = Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault())
    val clock = time.format(DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT))
    return if (time.toLocalDate() == java.time.LocalDate.now()) "today at $clock"
    else time.format(DateTimeFormatter.ofPattern("d MMM", Locale.getDefault())) + " at $clock"
}

/** Same pill as "I smoked today". */
@Composable
private fun PillButton(text: String, enabled: Boolean = true, onClick: () -> Unit) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.fillMaxWidth().height(56.dp),
        border = BorderStroke(1.dp, ButtonBorder),
        colors = ButtonDefaults.outlinedButtonColors(
            containerColor = ButtonFill,
            contentColor = Accent,
            disabledContainerColor = ButtonFill,
            disabledContentColor = TextSecondary,
        ),
        contentPadding = PaddingValues(horizontal = 32.dp),
    ) {
        Text(text, style = Label)
    }
}

@Composable
private fun EmberField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    keyboardOptions: KeyboardOptions,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    visualTransformation: VisualTransformation = VisualTransformation.None,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = Modifier.fillMaxWidth(),
        label = { Text(label, fontFamily = Manrope) },
        singleLine = true,
        textStyle = Label.copy(fontWeight = null, color = TextPrimary),
        keyboardOptions = keyboardOptions,
        keyboardActions = keyboardActions,
        visualTransformation = visualTransformation,
        shape = RoundedCornerShape(16.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedTextColor = TextPrimary,
            unfocusedTextColor = TextPrimary,
            focusedContainerColor = CardFill,
            unfocusedContainerColor = CardFill,
            focusedBorderColor = Accent,
            unfocusedBorderColor = ButtonBorder,
            focusedLabelColor = Accent,
            unfocusedLabelColor = TextSecondary,
            cursorColor = Accent,
        ),
    )
}
