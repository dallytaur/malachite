package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Snooze
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.ui.theme.MyApplicationTheme

class AddActivity : ComponentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge()
    setContent {
      MyApplicationTheme {
        AddPanelScreen(onBack = { finish() })
      }
    }
  }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddPanelScreen(onBack: () -> Unit) {
  Scaffold(
    topBar = {
      TopAppBar(
        title = { Text("Add Activity Panel") },
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
        .padding(16.dp)
        .verticalScroll(rememberScrollState()),
      verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // 1. Favorites (heart) action stub
          Card(
            onClick = { /* Implementation would go here */ },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(
              containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
            )
          ) {
            Row(
              modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
              verticalAlignment = Alignment.CenterVertically
            ) {
              Icon(
                imageVector = Icons.Default.Favorite,
                contentDescription = "Favorite",
                tint = Color(0xFFE91E63),
                modifier = Modifier.size(32.dp)
              )
              Spacer(modifier = Modifier.width(16.dp))
              Column {
                Text(
                  text = "Favorite Page",
                  style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                )
                Text(
                  text = "Bookmark the current URL as a favorite",
                  style = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                )
              }
            }
          }

          // 2. Add (+) action stub
          Card(
            onClick = { /* Implementation would go here */ },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(
              containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
            )
          ) {
            Row(
              modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
              verticalAlignment = Alignment.CenterVertically
            ) {
              Icon(
                imageVector = Icons.Default.Add,
                contentDescription = "Add Dial",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(32.dp)
              )
              Spacer(modifier = Modifier.width(16.dp))
              Column {
                Text(
                  text = "Add Custom Dial",
                  style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                )
                Text(
                  text = "Insert a new quick access bookmark link",
                  style = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                )
              }
            }
          }

          // 3. Snooze (zzz) action stub
          val activePage = BrowserState.activePageObject
          val snoozeCount = activePage?.snoozeCount ?: 0
          
          Card(
            onClick = { /* Implementation would go here */ },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(
              containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
            )
          ) {
            Row(
              modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
              verticalAlignment = Alignment.CenterVertically
            ) {
              BadgedBox(
                badge = {
                  if (snoozeCount > 0) {
                    Badge { Text(snoozeCount.toString()) }
                  }
                }
              ) {
                Icon(
                    imageVector = Icons.Default.Snooze,
                    contentDescription = "Snooze Alerts",
                    tint = Color(0xFFFF9800),
                    modifier = Modifier.size(32.dp)
                )
              }
              Spacer(modifier = Modifier.width(16.dp))
              Column {
                Text(
                  text = "Snooze Domain Alerts",
                  style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                )
                Text(
                  text = "Mute notifications and tracking for 1 hour",
                  style = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                )
              }
            }
          }

          // 4. Settings (cog) action stub
          Card(
            onClick = { /* Implementation would go here */ },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(
              containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
            )
          ) {
            Row(
              modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
              verticalAlignment = Alignment.CenterVertically
            ) {
              Icon(
                imageVector = Icons.Default.Settings,
                contentDescription = "Settings",
                tint = MaterialTheme.colorScheme.secondary,
                modifier = Modifier.size(32.dp)
              )
              Spacer(modifier = Modifier.width(16.dp))
              Column {
                Text(
                  text = "Advanced Configurations",
                  style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                )
                Text(
                  text = "Open engine parameter files and customize flags",
                  style = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                )
              }
            }
          }
    }
  }
}
