package com.example.aiassistant.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(vm: ChatViewModel = viewModel()) {
    val context = LocalContext.current
    val listState = rememberLazyListState()
    var input by remember { mutableStateOf("") }
    var showSettings by remember { mutableStateOf(false) }

    val micPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> if (granted) vm.startVoice() }

    LaunchedEffect(vm.messages.size) {
        if (vm.messages.isNotEmpty()) listState.animateScrollToItem(vm.messages.lastIndex)
    }

    fun submit() {
        vm.send(input)
        input = ""
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text("Mera Assistant") },
                actions = {
                    IconButton(onClick = { vm.clearChat() }) {
                        Icon(Icons.Filled.DeleteSweep, contentDescription = "Chat saaf karo")
                    }
                    IconButton(onClick = { showSettings = true }) {
                        Icon(Icons.Filled.Settings, contentDescription = "Settings")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .fillMaxSize()
                .imePadding()
        ) {
            Box(Modifier.weight(1f).fillMaxWidth()) {
                if (vm.messages.isEmpty()) {
                    Text(
                        "Namaste! Kuch bhi poochiye ya boliye:\n“7 baje ka alarm lagao”\n“YouTube kholo”",
                        modifier = Modifier.align(Alignment.Center).padding(32.dp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        items(vm.messages) { msg -> Bubble(msg) }
                    }
                }
            }

            if (vm.isLoading) LinearProgressIndicator(Modifier.fillMaxWidth())

            Row(
                Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedTextField(
                    value = if (vm.isListening) vm.partialText else input,
                    onValueChange = { if (!vm.isListening) input = it },
                    modifier = Modifier.weight(1f),
                    placeholder = { Text(if (vm.isListening) "Sun raha hoon…" else "Message likhiye") },
                    maxLines = 4,
                    shape = RoundedCornerShape(24.dp),
                )
                IconButton(onClick = {
                    when {
                        vm.isListening -> vm.stopVoice()
                        ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO)
                                == PackageManager.PERMISSION_GRANTED -> vm.startVoice()
                        else -> micPermission.launch(Manifest.permission.RECORD_AUDIO)
                    }
                }) {
                    Icon(
                        if (vm.isListening) Icons.Filled.Stop else Icons.Filled.Mic,
                        contentDescription = "Mic",
                        tint = if (vm.isListening) MaterialTheme.colorScheme.error
                        else MaterialTheme.colorScheme.primary,
                    )
                }
                IconButton(
                    onClick = ::submit,
                    enabled = input.isNotBlank() && !vm.isLoading && !vm.isListening,
                ) {
                    Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Bhejo")
                }
            }
        }
    }

    if (showSettings) {
        var provider by remember { mutableStateOf(vm.provider) }
        var claudeKey by remember { mutableStateOf(vm.claudeKey) }
        var geminiKey by remember { mutableStateOf(vm.geminiKey) }
        var claudeModel by remember { mutableStateOf(vm.claudeModel) }
        var geminiModel by remember { mutableStateOf(vm.geminiModel) }
        var speak by remember { mutableStateOf(vm.speakReplies) }
        var lang by remember { mutableStateOf(vm.language) }
        val isGemini = provider == "gemini"
        val closeSettings = {
            vm.clearTestResult()
            showSettings = false
        }

        AlertDialog(
            onDismissRequest = closeSettings,
            title = { Text("Settings") },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    Text("AI Provider")
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(selected = !isGemini, onClick = { provider = "claude"; vm.clearTestResult() }, label = { Text("Claude") })
                        FilterChip(selected = isGemini, onClick = { provider = "gemini"; vm.clearTestResult() }, label = { Text("Google Gemini") })
                    }
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = if (isGemini) geminiKey else claudeKey,
                        onValueChange = { if (isGemini) geminiKey = it else claudeKey = it },
                        label = { Text(if (isGemini) "Gemini API key" else "Anthropic API key") },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                    )
                    Text(
                        if (isGemini) "Key: aistudio.google.com/apikey. Har provider ki key alag save hoti hai (Keystore me encrypt)."
                        else "Key: console.anthropic.com. Har provider ki key alag save hoti hai (Keystore me encrypt).",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = if (isGemini) geminiModel else claudeModel,
                        onValueChange = { if (isGemini) geminiModel = it else claudeModel = it },
                        label = { Text("Model ID") },
                        singleLine = true,
                    )
                    Text(
                        "Model ID khali ya galat ho to 404 error aayega; Connection test se turant pata chal jaata hai.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(
                        onClick = {
                            vm.testConnection(
                                provider,
                                if (isGemini) geminiKey else claudeKey,
                                if (isGemini) geminiModel else claudeModel,
                            )
                        },
                        enabled = !vm.isTesting,
                    ) { Text(if (vm.isTesting) "Test ho raha hai…" else "Connection test") }
                    vm.testResult?.let {
                        Text(it, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 4.dp))
                    }
                    Spacer(Modifier.height(12.dp))
                    Text("Bolne ki bhasha")
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(selected = lang == "hi-IN", onClick = { lang = "hi-IN" }, label = { Text("हिन्दी") })
                        FilterChip(selected = lang == "en-IN", onClick = { lang = "en-IN" }, label = { Text("English") })
                    }
                    Spacer(Modifier.height(12.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Jawab bol kar sunao", Modifier.weight(1f))
                        Switch(checked = speak, onCheckedChange = { speak = it })
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    vm.saveSettings(provider, claudeKey, geminiKey, claudeModel, geminiModel, speak, lang)
                    closeSettings()
                }) { Text("Save") }
            },
            dismissButton = { TextButton(onClick = closeSettings) { Text("Cancel") } },
        )
    }
}

@Composable
private fun Bubble(msg: ChatMessage) {
    val isUser = msg.role == Role.USER
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start,
    ) {
        Surface(
            shape = RoundedCornerShape(18.dp),
            color = if (isUser) MaterialTheme.colorScheme.primaryContainer
            else MaterialTheme.colorScheme.surfaceVariant,
            modifier = Modifier.widthIn(max = 300.dp),
        ) {
            Text(msg.text, Modifier.padding(horizontal = 14.dp, vertical = 10.dp))
        }
    }
}
