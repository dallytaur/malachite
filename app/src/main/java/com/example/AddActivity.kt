package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.clickable
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.MyApplicationTheme

class AddActivity : ComponentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    val prefillUrl = intent.getStringExtra("PREFILL_URL") ?: ""
    enableEdgeToEdge()
    setContent {
      MyApplicationTheme {
        AddPanelScreen(prefillUrl = prefillUrl, onBack = { finish() })
      }
    }
  }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddPanelScreen(prefillUrl: String, onBack: () -> Unit) {
  var url by remember { mutableStateOf(prefillUrl) }
  var name by remember { mutableStateOf("") }
  var modifierValue by remember { mutableStateOf(0.0f) }
  var snoozeMinutes by remember { mutableIntStateOf(5) }
  var colorGroup by remember { mutableStateOf(AppColorGroup.BLUE) }

  Scaffold(
    topBar = {
      TopAppBar(
        title = { Text("Add to Feed", fontWeight = FontWeight.Bold) },
        navigationIcon = {
          IconButton(onClick = onBack) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
          }
        }
      )
    }
  ) { innerPadding ->
    Column(
      modifier = Modifier
        .fillMaxSize()
        .padding(innerPadding)
        .verticalScroll(rememberScrollState())
        .padding(16.dp),
      verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        // URL Input
        OutlinedTextField(
            value = url,
            onValueChange = { url = it },
            label = { Text("URL") },
            placeholder = { Text("https://example.com/page") },
            modifier = Modifier.fillMaxWidth()
        )

        OutlinedTextField(
            value = name,
            onValueChange = { name = it },
            label = { Text("Display Name") },
            placeholder = { Text("My Awesome Site") },
            modifier = Modifier.fillMaxWidth()
        )

        // Tiered Settings Reuse
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f))
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text("TIERED CONFIGURATION", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                
                // Dot Box (Standard Format)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Box(modifier = Modifier.border(1.dp, Color.Gray.copy(alpha = 0.2f), RoundedCornerShape(4.dp)).padding(4.dp)) {
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                DotSmall(AppColorGroup.RED, colorGroup == AppColorGroup.RED) { colorGroup = it }
                                DotSmall(AppColorGroup.YELLOW, colorGroup == AppColorGroup.YELLOW) { colorGroup = it }
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                DotSmall(AppColorGroup.GREEN, colorGroup == AppColorGroup.GREEN) { colorGroup = it }
                                DotSmall(AppColorGroup.BLUE, colorGroup == AppColorGroup.BLUE) { colorGroup = it }
                            }
                        }
                    }
                    Column {
                        Text("Active Channel: ${colorGroup.name}", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        val multiplier = BrowserState.groupSettings[colorGroup]?.first ?: 1.0f
                        Text("Allowance: ${multiplier}x", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                    }
                }

                // Affinity Slider/Control
                Column {
                    Text("Hard Affinity Offset: %+.1f".format(modifierValue), style = MaterialTheme.typography.labelSmall)
                    Slider(value = modifierValue, onValueChange = { modifierValue = it }, valueRange = -5f..5f)
                }

                // Snooze Control
                Column {
                    Text("Repetition Guard: ${snoozeMinutes}m", style = MaterialTheme.typography.labelSmall)
                    Slider(value = snoozeMinutes.toFloat(), onValueChange = { snoozeMinutes = it.toInt() }, valueRange = 1f..120f)
                }
            }
        }

        Button(
            onClick = {
                if (url.isNotBlank()) {
                    val finalName = name.ifBlank { url }
                    BrowserState.addPageToFeed(
                        url = url, 
                        title = finalName, 
                        group = colorGroup,
                        modifier = modifierValue,
                        snooze = snoozeMinutes
                    )
                    onBack()
                }
            },
            modifier = Modifier.fillMaxWidth().height(56.dp)
        ) {
            Text("Add URL to List", fontWeight = FontWeight.Bold)
        }
    }
  }
}

@Composable
fun DotSmall(group: AppColorGroup, isSelected: Boolean, onClick: (AppColorGroup) -> Unit) {
    androidx.compose.foundation.Canvas(
        modifier = Modifier
            .size(24.dp)
            .clickable { onClick(group) }
            .padding(4.dp)
    ) {
        drawCircle(
            color = when (group) {
                AppColorGroup.RED -> Color(0xFFF44336)
                AppColorGroup.YELLOW -> Color(0xFFFFEB3B)
                AppColorGroup.GREEN -> Color(0xFF4CAF50)
                AppColorGroup.BLUE -> Color(0xFF2196F3)
            },
            radius = size.minDimension / 2
        )
        if (isSelected) {
            drawCircle(
                color = Color.White,
                radius = size.minDimension / 4
            )
        }
    }
}
