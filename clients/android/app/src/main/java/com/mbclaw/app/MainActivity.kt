package com.mbclaw.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.*

// ── API Interface ──

interface MbclawApi {
    @GET("api/health")
    suspend fun health(): Map<String, Any>

    @GET("api/projects")
    suspend fun getProjects(): List<Map<String, Any>>

    @POST("api/projects/{id}/sessions")
    suspend fun createSession(@Path("id") projectId: Int, @Body body: Map<String, Any>): Map<String, Any>

    @POST("api/sessions/{id}/messages")
    suspend fun sendMessage(@Path("id") sessionId: Int, @Body body: Map<String, String>): Map<String, Any>
}

// ── Main Activity ──

class MainActivity : ComponentActivity() {
    private val api = Retrofit.Builder()
        .baseUrl("http://10.0.2.2:8000/")  // Android emulator → host
        .addConverterFactory(GsonConverterFactory.create())
        .build()
        .create(MbclawApi::class.java)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MbclawTheme {
                MbclawApp(api)
            }
        }
    }
}

@Composable
fun MbclawApp(api: MbclawApi) {
    var messages by remember { mutableStateOf(listOf<Pair<String, String>>()) }
    var inputText by remember { mutableStateOf("") }
    var status by remember { mutableStateOf("连接中...") }
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        try {
            val health = api.health()
            status = "✅ ${health["status"]}"
        } catch (e: Exception) {
            status = "❌ 无法连接服务器"
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("MBclaw") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            )
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            // Status
            Text(status, modifier = Modifier.padding(8.dp), style = MaterialTheme.typography.bodySmall)

            // Messages
            LazyColumn(
                modifier = Modifier.weight(1f).fillMaxWidth().padding(8.dp)
            ) {
                items(messages.size) { i ->
                    val (role, content) = messages[i]
                    Card(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = if (role == "user")
                                MaterialTheme.colorScheme.secondaryContainer
                            else MaterialTheme.colorScheme.surfaceVariant
                        )
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(if (role == "user") "🧑 你" else "🤖 MBclaw", style = MaterialTheme.typography.labelSmall)
                            Text(content, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            }

            // Input
            Row(modifier = Modifier.fillMaxWidth().padding(8.dp)) {
                OutlinedTextField(
                    value = inputText,
                    onValueChange = { inputText = it },
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("输入消息...") },
                    singleLine = true
                )
                Spacer(modifier = Modifier.width(8.dp))
                Button(onClick = {
                    if (inputText.isNotBlank()) {
                        val msg = inputText
                        inputText = ""
                        messages = messages + ("user" to msg)
                        scope.launch {
                            try {
                                val resp = api.sendMessage(1, mapOf("role" to "user", "content" to msg))
                                val reply = resp["reply"]?.toString() ?: "(无回复)"
                                messages = messages + ("assistant" to reply)
                            } catch (e: Exception) {
                                messages = messages + ("system" to "发送失败: ${e.message}")
                            }
                        }
                    }
                }) {
                    Text("发送")
                }
            }
        }
    }
}

@Composable
fun MbclawTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = androidx.compose.ui.graphics.Color(0xFF58A6FF),
            secondary = androidx.compose.ui.graphics.Color(0xFF3FB950),
        ),
        content = content,
    )
}
