package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.ui.theme.MyApplicationTheme

class HistoryActivity : ComponentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge()
    setContent {
      MyApplicationTheme {
        HistoryScreen(onBack = { finish() })
      }
    }
  }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(onBack: () -> Unit) {
  val history = BrowserState.history

  Scaffold(
    topBar = {
      TopAppBar(
        title = { Text("Browser History") },
        navigationIcon = {
          IconButton(onClick = onBack) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
          }
        }
      )
    }
  ) { innerPadding ->
    if (history.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize().padding(innerPadding), contentAlignment = Alignment.Center) {
            Text("No history yet.")
        }
    } else {
        LazyColumn(
          modifier = Modifier
            .fillMaxSize()
            .padding(innerPadding)
        ) {
          items(history) { entry ->
            HistoryItem(
                entry = entry,
                onNavigate = {
                    BrowserState.currentUrlToLoad = entry.url
                    onBack()
                },
                onAddToFeed = {
                    BrowserState.addPageToFeed(entry.url, entry.title)
                }
            )
            HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
          }
        }
    }
  }
}

@Composable
fun HistoryItem(
    entry: HistoryEntry,
    onNavigate: () -> Unit,
    onAddToFeed: () -> Unit
) {
    val durationSeconds = entry.duration / 1000
    val durationFormatted = if (durationSeconds < 60) "${durationSeconds}s" else "${durationSeconds / 60}m ${durationSeconds % 60}s"

    Row(
      modifier = Modifier
        .fillMaxWidth()
        .clickable(onClick = onNavigate)
        .padding(16.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.SpaceBetween
    ) {
      Column(modifier = Modifier.weight(1f)) {
        Text(text = entry.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Text(text = entry.url, style = MaterialTheme.typography.bodySmall, maxLines = 1)
        Spacer(modifier = Modifier.height(4.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(text = "Spent: $durationFormatted", style = MaterialTheme.typography.labelSmall)
            Text(text = "via ${entry.parentContext}", style = MaterialTheme.typography.labelSmall)
        }
      }
      
      IconButton(onClick = onAddToFeed) {
        Icon(imageVector = Icons.Default.Add, contentDescription = "Add to Feed")
      }
    }
}
