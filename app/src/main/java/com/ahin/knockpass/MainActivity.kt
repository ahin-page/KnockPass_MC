package com.ahin.knockpass

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.ahin.knockpass.ui.theme.KnockpassTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            KnockpassTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    MainScreen()
                }
            }
        }
    }
}

@Composable
fun MainScreen() {
    val context = LocalContext.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center
    ) {
        Text("Welcome to KnockPass!", style = MaterialTheme.typography.headlineMedium)
        Spacer(modifier = Modifier.height(24.dp))

        // SensorActivity 실행 버튼
        Button(onClick = {
            val intent = Intent(context, SensorActivity::class.java)
            context.startActivity(intent)
        }) {
            Text("Start Sensor Recording")
        }

        Spacer(modifier = Modifier.height(16.dp))

        // ExportActivity 실행 버튼
        Button(onClick = {
            val intent = Intent(context, ExportActivity::class.java)
            context.startActivity(intent)
        }) {
            Text("Export CSV via Email")
        }
    }
}

@Preview(showBackground = true)
@Composable
fun MainScreenPreview() {
    KnockpassTheme {
        MainScreen()
    }
}