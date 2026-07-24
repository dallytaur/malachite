package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.MyApplicationTheme

class SettingsActivity : ComponentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge()
    setContent {
      MyApplicationTheme {
        SettingsScreen(onBack = { finish() })
      }
    }
  }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(onBack: () -> Unit) {
  Scaffold(
    topBar = {
      TopAppBar(
        title = { Text("Control Center", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold) },
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
        .padding(12.dp),
      verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
      // 1. Global Defaults
      LabelText("GLOBAL DEFAULTS")
      AffinityControlBlock(
          title = "Browser Baseline",
          settings = BrowserState.globalSettings,
          onModifierChange = { BrowserState.globalSettings = BrowserState.globalSettings.copy(modifier = it) },
          onSnoozeChange = { BrowserState.globalSettings = BrowserState.globalSettings.copy(snoozeMinutes = it) }
      )

      // 2. Group / Channel Multipliers
      LabelText("GROUP CHANNELS")
      Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
          AppColorGroup.entries.forEach { group ->
              val current = BrowserState.groupSettings[group] ?: Pair(1.0f, 5)
              GroupControlBlock(
                  group = group,
                  multiplier = current.first,
                  snooze = current.second,
                  onMultiplierChange = { newM -> 
                      val newMap = BrowserState.groupSettings.toMutableMap()
                      newMap[group] = Pair(newM, current.second)
                      BrowserState.groupSettings = newMap
                  },
                  onSnoozeChange = { newS ->
                      val newMap = BrowserState.groupSettings.toMutableMap()
                      newMap[group] = Pair(current.first, newS)
                      BrowserState.groupSettings = newMap
                  }
              )
          }
      }

      // 3. Domains & URLs Hierarchy
      LabelText("RECORDED DOMAINS")
      val totalDomainWeight = BrowserState.domainsList.sumOf { d ->
          maxOf(0.01, (d.affinityScore + d.settings.modifier + BrowserState.globalSettings.modifier).toDouble())
      }
      Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
          BrowserState.domainsList.forEach { domain ->
              DomainHierarchyCard(domain, totalDomainWeight)
          }
      }

      // 4. UI Tweaks
      LabelText("NAVIGATION TWEAKS")
      Card(
          modifier = Modifier.fillMaxWidth(),
          shape = RoundedCornerShape(8.dp),
          colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.1f)),
          border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f))
      ) {
          Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)) {
              ToggleRow("Swipe Right Next", BrowserState.swipeRightNext) { BrowserState.swipeRightNext = it }
              ToggleRow("Swipe Left Next", BrowserState.swipeLeftNext) { BrowserState.swipeLeftNext = it }
              ToggleRow("Double Tap Next", BrowserState.doubleTapNext) { BrowserState.doubleTapNext = it }
          }
      }
      
      Spacer(modifier = Modifier.height(32.dp))
    }
  }
}

@Composable
fun LabelText(text: String) {
    Text(text, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f), modifier = Modifier.padding(start = 4.dp))
}

@Composable
fun AffinityControlBlock(
    title: String,
    settings: AffinitySettings,
    onModifierChange: (Float) -> Unit,
    onSnoozeChange: (Int) -> Unit,
    colorGroup: AppColorGroup? = null,
    onGroupChange: ((AppColorGroup) -> Unit)? = null,
    stats: String? = null
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.15f)),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f))
    ) {
        Column(modifier = Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(title, fontWeight = FontWeight.Bold, fontSize = 13.sp, maxLines = 1, modifier = Modifier.weight(1f))
                if (stats != null) {
                    Text(stats, style = MaterialTheme.typography.labelSmall, color = Color.Gray, fontSize = 10.sp)
                }
            }

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                // RYGB Dot Box (Compact)
                Box(modifier = Modifier.border(1.dp, Color.Gray.copy(alpha = 0.1f), RoundedCornerShape(4.dp)).padding(2.dp)) {
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                            Dot(AppColorGroup.RED, colorGroup == AppColorGroup.RED) { onGroupChange?.invoke(it) }
                            Dot(AppColorGroup.YELLOW, colorGroup == AppColorGroup.YELLOW) { onGroupChange?.invoke(it) }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                            Dot(AppColorGroup.GREEN, colorGroup == AppColorGroup.GREEN) { onGroupChange?.invoke(it) }
                            Dot(AppColorGroup.BLUE, colorGroup == AppColorGroup.BLUE) { onGroupChange?.invoke(it) }
                        }
                    }
                }

                // Affinity Modifier (Compact)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    IconButton(onClick = { onModifierChange(settings.modifier - 0.1f) }, modifier = Modifier.size(24.dp)) { Icon(Icons.Default.Remove, null, modifier = Modifier.size(16.dp)) }
                    Text("%+.1f".format(settings.modifier), fontWeight = FontWeight.ExtraBold, fontSize = 14.sp, modifier = Modifier.widthIn(min = 40.dp))
                    IconButton(onClick = { onModifierChange(settings.modifier + 0.1f) }, modifier = Modifier.size(24.dp)) { Icon(Icons.Default.Add, null, modifier = Modifier.size(16.dp)) }
                }

                // Upswipe Snooze (Compact Row)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Icon(Icons.Default.Timer, null, modifier = Modifier.size(12.dp), tint = Color.Gray)
                    Text("${settings.snoozeMinutes}m", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = MaterialTheme.colorScheme.primary)
                    Slider(
                        value = settings.snoozeMinutes.toFloat(),
                        onValueChange = { onSnoozeChange(it.toInt()) },
                        valueRange = 1f..60f,
                        modifier = Modifier.width(60.dp).height(20.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun GroupControlBlock(
    group: AppColorGroup,
    multiplier: Float,
    snooze: Int,
    onMultiplierChange: (Float) -> Unit,
    onSnoozeChange: (Int) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = getGroupColor(group).copy(alpha = 0.05f)),
        border = androidx.compose.foundation.BorderStroke(1.dp, getGroupColor(group).copy(alpha = 0.2f))
    ) {
        Row(modifier = Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(modifier = Modifier.size(10.dp).clip(CircleShape).background(getGroupColor(group)))
            Text(group.name.take(1), fontWeight = FontWeight.Bold, fontSize = 12.sp)
            
            Row(modifier = Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Prob:", style = MaterialTheme.typography.labelSmall, fontSize = 9.sp, color = Color.Gray)
                Slider(value = multiplier, onValueChange = onMultiplierChange, valueRange = 0.1f..3.0f, modifier = Modifier.weight(1f).height(20.dp))
                Text("%.1fx".format(multiplier), fontWeight = FontWeight.Bold, fontSize = 11.sp, modifier = Modifier.width(30.dp))
            }

            Row(modifier = Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Guard:", style = MaterialTheme.typography.labelSmall, fontSize = 9.sp, color = Color.Gray)
                Slider(value = snooze.toFloat(), onValueChange = { onSnoozeChange(it.toInt()) }, valueRange = 1f..120f, modifier = Modifier.weight(1f).height(20.dp))
                Text("${snooze}m", fontWeight = FontWeight.Bold, fontSize = 11.sp, modifier = Modifier.width(30.dp))
            }
        }
    }
}

@Composable
fun DomainHierarchyCard(domain: DomainObject, totalGlobalWeight: Double) {
    var expanded by remember { mutableStateOf(false) }
    val effectiveWeight = maxOf(0.01, (domain.affinityScore + domain.settings.modifier + BrowserState.globalSettings.modifier).toDouble())
    val globalPercent = (effectiveWeight / totalGlobalWeight * 100).toInt()

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        AffinityControlBlock(
            title = domain.url.replace("https://", "").replace("www.", ""),
            settings = domain.settings,
            onModifierChange = { newVal ->
                BrowserState.domainsList = BrowserState.domainsList.map { if (it.url == domain.url) it.copy(settings = it.settings.copy(modifier = newVal)) else it }
            },
            onSnoozeChange = { newVal ->
                BrowserState.domainsList = BrowserState.domainsList.map { if (it.url == domain.url) it.copy(settings = it.settings.copy(snoozeMinutes = newVal)) else it }
            },
            colorGroup = domain.defaultColorGroup,
            onGroupChange = { newGroup ->
                BrowserState.domainsList = BrowserState.domainsList.map { if (it.url == domain.url) it.copy(defaultColorGroup = newGroup) else it }
            },
            stats = "Global: $globalPercent%"
        )
        
        if (domain.pages.isNotEmpty()) {
            Row(modifier = Modifier.padding(start = 12.dp).clickable { expanded = !expanded }, verticalAlignment = Alignment.CenterVertically) {
                Icon(if (expanded) Icons.Default.ArrowDropUp else Icons.Default.ArrowDropDown, null, modifier = Modifier.size(16.dp))
                Text("${domain.pages.size} pages", fontSize = 10.sp, color = MaterialTheme.colorScheme.primary)
            }

            AnimatedVisibility(visible = expanded) {
                val totalPageWeight = domain.pages.sumOf { p ->
                    val m = BrowserState.groupSettings[p.assignedColorGroup]?.first ?: 1.0f
                    maxOf(0.01, (p.affinityScore + p.settings.modifier + BrowserState.globalSettings.modifier).toDouble() * m)
                }

                Column(modifier = Modifier.padding(start = 16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    domain.pages.forEach { page ->
                        val pageWeight = (page.affinityScore + page.settings.modifier + BrowserState.globalSettings.modifier).toDouble() * (BrowserState.groupSettings[page.assignedColorGroup]?.first ?: 1.0f)
                        val inDomainPercent = (pageWeight / totalPageWeight * 100).toInt()
                        val pageGlobalPercent = (inDomainPercent * globalPercent) / 100

                        AffinityControlBlock(
                            title = page.path,
                            settings = page.settings,
                            onModifierChange = { newVal ->
                                BrowserState.domainsList = BrowserState.domainsList.map { d ->
                                    if (d.url == domain.url) d.copy(pages = d.pages.map { if (it.path == page.path) it.copy(settings = it.settings.copy(modifier = newVal)) else it }) else d
                                }
                            },
                            onSnoozeChange = { newVal ->
                                BrowserState.domainsList = BrowserState.domainsList.map { d ->
                                    if (d.url == domain.url) d.copy(pages = d.pages.map { if (it.path == page.path) it.copy(settings = it.settings.copy(snoozeMinutes = newVal)) else it }) else d
                                }
                            },
                            colorGroup = page.assignedColorGroup,
                            onGroupChange = { newGroup ->
                                BrowserState.domainsList = BrowserState.domainsList.map { d ->
                                    if (d.url == domain.url) d.copy(pages = d.pages.map { if (it.path == page.path) it.copy(assignedColorGroup = newGroup) else it }) else d
                                }
                            },
                            stats = "$inDomainPercent% / $pageGlobalPercent%"
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun Dot(group: AppColorGroup, isSelected: Boolean, onClick: (AppColorGroup) -> Unit) {
    Box(
        modifier = Modifier
            .size(14.dp)
            .clip(CircleShape)
            .background(getGroupColor(group))
            .clickable { onClick(group) }
            .border(1.5.dp, if (isSelected) Color.White else Color.Transparent, CircleShape)
    )
}

@Composable
fun ToggleRow(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(modifier = Modifier.fillMaxWidth().height(32.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.bodySmall, fontSize = 12.sp)
        Switch(checked = checked, onCheckedChange = onCheckedChange, modifier = Modifier.scale(0.7f))
    }
}

// Extension to scale components like Switch
@Composable
fun Modifier.scale(scale: Float): Modifier = this.then(
    Modifier.graphicsLayer(scaleX = scale, scaleY = scale)
)

fun getGroupColor(group: AppColorGroup): Color = when (group) {
    AppColorGroup.RED -> Color(0xFFF44336)
    AppColorGroup.YELLOW -> Color(0xFFFFEB3B)
    AppColorGroup.GREEN -> Color(0xFF4CAF50)
    AppColorGroup.BLUE -> Color(0xFF2196F3)
}
