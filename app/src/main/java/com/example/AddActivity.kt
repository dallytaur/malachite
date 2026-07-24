package com.example

import android.os.Bundle
import android.net.Uri
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.clickable
import androidx.compose.foundation.border
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.clip
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.MyApplicationTheme

class AddActivity : ComponentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    val prefillUrl = intent.getStringExtra("PREFILL_URL") ?: ""
    val prefillTitle = intent.getStringExtra("PREFILL_TITLE") ?: ""
    enableEdgeToEdge()
    setContent {
      MyApplicationTheme {
        AddPanelScreen(
            prefillUrl = prefillUrl, 
            prefillTitle = prefillTitle,
            onBack = { finish() }
        )
      }
    }
  }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddPanelScreen(prefillUrl: String, prefillTitle: String, onBack: () -> Unit) {
  var url by remember { mutableStateOf(prefillUrl) }
  var name by remember { mutableStateOf(prefillTitle) }
  var modifierValue by remember { mutableStateOf(0.0f) }
  var snoozeMinutes by remember { mutableIntStateOf(5) }
  
  // Intelligent Domain Color Matching
  var colorGroup by remember { 
      mutableStateOf(deriveInitialGroup(prefillUrl)) 
  }

  Scaffold(
    topBar = {
      TopAppBar(
        title = { Text("Add to Feed", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold) },
        navigationIcon = {
          IconButton(onClick = onBack) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
          }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.background,
            titleContentColor = MaterialTheme.colorScheme.primary
        )
      )
    }
  ) { innerPadding ->
    Column(
      modifier = Modifier
        .fillMaxSize()
        .padding(innerPadding)
        .verticalScroll(rememberScrollState())
        .padding(8.dp),
      verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // High Density Input Row
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.extraSmall,
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.1f)),
            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f))
        ) {
            Column(modifier = Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = url,
                    onValueChange = { url = it },
                    label = { Text("URL", fontSize = 10.sp) },
                    textStyle = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    shape = MaterialTheme.shapes.extraSmall
                )

                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Name", fontSize = 10.sp) },
                    textStyle = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    shape = MaterialTheme.shapes.extraSmall
                )
            }
        }

        // Tiered Settings (Zero Dead Space Format)
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.extraSmall,
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.15f)),
            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f))
        ) {
            Column(modifier = Modifier.padding(6.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("CONFIGURATION", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                
                // One-Row Channel Selector
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Box(modifier = Modifier.border(1.dp, Color.Gray.copy(alpha = 0.1f), MaterialTheme.shapes.extraSmall).padding(2.dp)) {
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                                DotSmall(AppColorGroup.RED, colorGroup == AppColorGroup.RED) { colorGroup = it }
                                DotSmall(AppColorGroup.YELLOW, colorGroup == AppColorGroup.YELLOW) { colorGroup = it }
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                                DotSmall(AppColorGroup.GREEN, colorGroup == AppColorGroup.GREEN) { colorGroup = it }
                                DotSmall(AppColorGroup.BLUE, colorGroup == AppColorGroup.BLUE) { colorGroup = it }
                            }
                        }
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(colorGroup.name, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        val multiplier = BrowserState.groupSettings[colorGroup]?.multiplier ?: 1.0f
                        Text("${multiplier}x Allowance", style = MaterialTheme.typography.labelSmall, fontSize = 9.sp, color = Color.Gray)
                    }
                }

                // Consolidated Controls
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("Hard Offset", style = MaterialTheme.typography.labelSmall, fontSize = 9.sp)
                    PrecisionControl(
                        value = modifierValue, 
                        onValueChange = { modifierValue = it }, 
                        range = -5f..5f
                    )
                }

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("Guard (min)", style = MaterialTheme.typography.labelSmall, fontSize = 9.sp)
                    PrecisionControlInt(
                        value = snoozeMinutes, 
                        onValueChange = { snoozeMinutes = it }, 
                        range = 1..120
                    )
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
            modifier = Modifier.fillMaxWidth().height(48.dp),
            shape = MaterialTheme.shapes.extraSmall
        ) {
            Text("ADD TO FEED", fontWeight = FontWeight.Bold)
        }
    }
  }
}

private fun deriveInitialGroup(url: String): AppColorGroup {
    if (url.isBlank()) return AppColorGroup.BLUE
    val uri = Uri.parse(url)
    val domainUrl = "${uri.scheme}://${uri.host}"
    return BrowserState.domainsList.find { it.url.equals(domainUrl, ignoreCase = true) }?.defaultColorGroup ?: AppColorGroup.BLUE
}

@Composable
fun DotSmall(group: AppColorGroup, isSelected: Boolean, onClick: (AppColorGroup) -> Unit) {
    Box(
        modifier = Modifier
            .size(16.dp)
            .clip(CircleShape)
            .background(getGroupColor(group))
            .clickable { onClick(group) }
            .border(1.5.dp, if (isSelected) Color.White else Color.Transparent, CircleShape)
    )
}

// Re-using Precision Controls from Settings (normally would be in a shared UI file)
@Composable
fun PrecisionControl(
    value: Float,
    onValueChange: (Float) -> Unit,
    range: ClosedFloatingPointRange<Float>,
    step: Float = 0.1f
) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        IconButton(onClick = { onValueChange((value - step).coerceIn(range)) }, modifier = Modifier.size(24.dp)) {
            Icon(Icons.Default.Remove, null, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.primary)
        }
        Text("%.1f".format(value), fontWeight = FontWeight.Bold, fontSize = 11.sp, modifier = Modifier.widthIn(min = 28.dp), textAlign = TextAlign.Center)
        IconButton(onClick = { onValueChange((value + step).coerceIn(range)) }, modifier = Modifier.size(24.dp)) {
            Icon(Icons.Default.Add, null, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.primary)
        }
    }
}

@Composable
fun PrecisionControlInt(
    value: Int,
    onValueChange: (Int) -> Unit,
    range: IntRange
) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        IconButton(onClick = { onValueChange((value - 1).coerceIn(range)) }, modifier = Modifier.size(24.dp)) {
            Icon(Icons.Default.Remove, null, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.primary)
        }
        Text(value.toString(), fontWeight = FontWeight.Bold, fontSize = 11.sp, modifier = Modifier.widthIn(min = 28.dp), textAlign = TextAlign.Center)
        IconButton(onClick = { onValueChange((value + 1).coerceIn(range)) }, modifier = Modifier.size(24.dp)) {
            Icon(Icons.Default.Add, null, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.primary)
        }
    }
}
