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

import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.platform.LocalContext
import org.mozilla.geckoview.WebExtension

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts

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

@Composable
fun PrecisionControl(
    value: Float,
    onValueChange: (Float) -> Unit,
    range: ClosedFloatingPointRange<Float> = 0f..5f,
    step: Float = 0.1f,
    format: String = "%.1f",
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.height(20.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        IconButton(
            onClick = { onValueChange((value - step).coerceIn(range)) },
            modifier = Modifier.size(18.dp)
        ) {
            Icon(Icons.Default.Remove, null, modifier = Modifier.size(12.dp), tint = MaterialTheme.colorScheme.primary)
        }
        
        Text(
            text = format.format(value),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.widthIn(min = 24.dp),
            textAlign = TextAlign.Center,
            fontSize = 9.sp
        )

        IconButton(
            onClick = { onValueChange((value + step).coerceIn(range)) },
            modifier = Modifier.size(18.dp)
        ) {
            Icon(Icons.Default.Add, null, modifier = Modifier.size(12.dp), tint = MaterialTheme.colorScheme.primary)
        }
    }
}

@Composable
fun PrecisionControlInt(
    value: Int,
    onValueChange: (Int) -> Unit,
    range: IntRange = 0..120,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.height(20.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        IconButton(
            onClick = { onValueChange((value - 1).coerceIn(range)) },
            modifier = Modifier.size(18.dp)
        ) {
            Icon(Icons.Default.Remove, null, modifier = Modifier.size(12.dp), tint = MaterialTheme.colorScheme.primary)
        }
        
        Text(
            text = value.toString(),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.widthIn(min = 20.dp),
            textAlign = TextAlign.Center,
            fontSize = 9.sp
        )

        IconButton(
            onClick = { onValueChange((value + 1).coerceIn(range)) },
            modifier = Modifier.size(18.dp)
        ) {
            Icon(Icons.Default.Add, null, modifier = Modifier.size(12.dp), tint = MaterialTheme.colorScheme.primary)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(onBack: () -> Unit) {
  val context = LocalContext.current
  val config = androidx.compose.ui.platform.LocalConfiguration.current
  val isLandscape = config.screenWidthDp > config.screenHeightDp
  
  val prefs = remember { context.getSharedPreferences("minima_browser_prefs", android.content.Context.MODE_PRIVATE) }
  var showAddonsDialog by remember { mutableStateOf(false) }
  var installedExtensions by remember { mutableStateOf<List<WebExtension>>(emptyList()) }
  var remoteDebuggingEnabled by remember { mutableStateOf(false) }
  
  if (showAddonsDialog) {
      GeckoEngineManager.getInstalledExtensions { installedExtensions = it }
      AlertDialog(
          onDismissRequest = { showAddonsDialog = false },
          title = { Text("Installed Add-ons") },
          text = {
              Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                  if (installedExtensions.isEmpty()) {
                      Text("No add-ons found.", style = MaterialTheme.typography.bodySmall)
                  }
                  installedExtensions.forEach { ext ->
                      Card(modifier = Modifier.fillMaxWidth()) {
                          Column(modifier = Modifier.padding(8.dp)) {
                              Text(ext.id, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                              Text("Source: Bundled Asset", fontSize = 10.sp, color = Color.Gray)
                          }
                      }
                  }

                  Button(
                      onClick = { 
                          android.widget.Toast.makeText(context, "Downloading uBlock...", android.widget.Toast.LENGTH_SHORT).show()
                          GeckoEngineManager.installExtensionFromUrl(context, "https://addons.mozilla.org/firefox/downloads/latest/ublock-origin/latest.xpi") { success ->
                              if (success) {
                                  android.widget.Toast.makeText(context, "uBlock Installed!", android.widget.Toast.LENGTH_SHORT).show()
                                  showAddonsDialog = false
                              } else {
                                  android.widget.Toast.makeText(context, "Install failed. Check connection.", android.widget.Toast.LENGTH_SHORT).show()
                              }
                          }
                      },
                      modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                      colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                      shape = MaterialTheme.shapes.extraSmall
                  ) {
                      Icon(Icons.Default.Shield, null, modifier = Modifier.size(18.dp))
                      Spacer(modifier = Modifier.width(8.dp))
                      Text("Install uBlock (Online)")
                  }
              }
          },
          confirmButton = { TextButton(onClick = { showAddonsDialog = false }) { Text("Close") } }
      )
  }

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
        .padding(4.dp),
      verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
      if (isLandscape) {
          Row(modifier = Modifier.fillMaxWidth()) {
              Column(modifier = Modifier.weight(1f).padding(end = 2.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                  LabelText("GLOBAL")
                  AffinityControlBlock(
                      title = "Baseline",
                      settings = BrowserState.globalSettings,
                      onModifierChange = { BrowserState.globalSettings = BrowserState.globalSettings.copy(modifier = it) },
                      onSnoozeChange = { BrowserState.globalSettings = BrowserState.globalSettings.copy(snoozeMinutes = it) }
                  )
                  
                  LabelText("NAV")
                  NavigationTweaksCard(prefs, onBack)
              }
              Column(modifier = Modifier.weight(1f).padding(start = 2.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                  LabelText("GROUPS")
                  GroupChannelsList()
                  
                  LabelText("ENGINE")
                  AdvancedEngineSettingsCard(remoteDebuggingEnabled, { remoteDebuggingEnabled = it }, { showAddonsDialog = true }, onBack)

                  LabelText("DATA & PORTABILITY")
                  DataPortabilityCard()
              }
          }
      } else {
          LabelText("GLOBAL DEFAULTS")
          AffinityControlBlock(
              title = "Browser Baseline",
              settings = BrowserState.globalSettings,
              onModifierChange = { BrowserState.globalSettings = BrowserState.globalSettings.copy(modifier = it) },
              onSnoozeChange = { BrowserState.globalSettings = BrowserState.globalSettings.copy(snoozeMinutes = it) }
          )

          LabelText("GROUP CHANNELS")
          GroupChannelsList()

          LabelText("NAVIGATION TWEAKS")
          NavigationTweaksCard(prefs, onBack)
      }

      LabelText("RECORDED DOMAINS")
      val totalDomainWeight = BrowserState.domainsList.sumOf { d ->
          maxOf(0.01, (d.affinityScore + d.settings.modifier + BrowserState.globalSettings.modifier).toDouble())
      }
      Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
          BrowserState.domainsList.forEach { domain ->
              DomainHierarchyCard(domain, totalDomainWeight)
          }
      }

      if (!isLandscape) {
          LabelText("ADVANCED ENGINE SETTINGS")
          AdvancedEngineSettingsCard(remoteDebuggingEnabled, { remoteDebuggingEnabled = it }, { showAddonsDialog = true }, onBack)

          LabelText("DATA & PORTABILITY")
          DataPortabilityCard()
      }
      
      Spacer(modifier = Modifier.height(32.dp))
    }
  }
}

@Composable
fun LabelText(text: String) {
    Text(text, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f), fontSize = 9.sp)
}

@Composable
fun AffinityControlBlock(
    title: String,
    settings: AffinitySettings,
    onModifierChange: (Float) -> Unit,
    onSnoozeChange: (Int) -> Unit,
    colorGroup: AppColorGroup? = null,
    onGroupChange: ((AppColorGroup) -> Unit)? = null,
    onDelete: (() -> Unit)? = null,
    stats: String? = null
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.15f)),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 2.dp, vertical = 2.dp).fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            // 1. RYGB Dots (If present)
            if (colorGroup != null && onGroupChange != null) {
                Box(modifier = Modifier.border(1.dp, Color.Gray.copy(alpha = 0.1f), MaterialTheme.shapes.extraSmall).padding(1.dp)) {
                    Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(1.dp)) {
                            Dot(AppColorGroup.RED, colorGroup == AppColorGroup.RED) { onGroupChange(it) }
                            Dot(AppColorGroup.YELLOW, colorGroup == AppColorGroup.YELLOW) { onGroupChange(it) }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(1.dp)) {
                            Dot(AppColorGroup.GREEN, colorGroup == AppColorGroup.GREEN) { onGroupChange(it) }
                            Dot(AppColorGroup.BLUE, colorGroup == AppColorGroup.BLUE) { onGroupChange(it) }
                        }
                    }
                }
            }

            // 2. Title & Stats
            Column(modifier = Modifier.padding(horizontal = 4.dp)) {
                Text(title, fontWeight = FontWeight.Bold, fontSize = 12.sp, maxLines = 1)
                if (stats != null) {
                    Text(stats, style = MaterialTheme.typography.labelSmall, color = Color.Gray, fontSize = 8.sp)
                }
            }

            // 3. Affinity Modifier
            PrecisionControl(value = settings.modifier, onValueChange = onModifierChange, range = -5f..5f)

            // 4. Timer
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(1.dp)) {
                Icon(Icons.Default.Timer, null, modifier = Modifier.size(8.dp), tint = Color.Gray)
                PrecisionControlInt(value = settings.snoozeMinutes, onValueChange = onSnoozeChange, range = 1..120)
            }

            // 5. Delete
            if (onDelete != null) {
                IconButton(onClick = onDelete, modifier = Modifier.size(16.dp)) {
                    Icon(Icons.Default.Close, null, tint = Color.Red.copy(alpha = 0.7f), modifier = Modifier.size(12.dp))
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
        colors = CardDefaults.cardColors(containerColor = getGroupColor(group).copy(alpha = 0.05f)),
        border = androidx.compose.foundation.BorderStroke(1.dp, getGroupColor(group).copy(alpha = 0.2f))
    ) {
        Row(modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Box(modifier = Modifier.size(6.dp).clip(CircleShape).background(getGroupColor(group)))
            Text(group.name.take(1), fontWeight = FontWeight.Bold, fontSize = 10.sp)
            
            Row(modifier = Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                Text("P:", style = MaterialTheme.typography.labelSmall, fontSize = 8.sp, color = Color.Gray)
                PrecisionControl(value = multiplier, onValueChange = onMultiplierChange, range = 0.1f..3.0f, step = 0.1f)
            }

            Row(modifier = Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                Text("G:", style = MaterialTheme.typography.labelSmall, fontSize = 8.sp, color = Color.Gray)
                PrecisionControlInt(value = snooze, onValueChange = onSnoozeChange, range = 1..120)
            }
        }
    }
}

@Composable
fun DomainHierarchyCard(domain: DomainObject, totalGlobalWeight: Double) {
    var expanded by remember { mutableStateOf(false) }
    val effectiveWeight = maxOf(0.01, (domain.affinityScore + domain.settings.modifier + BrowserState.globalSettings.modifier).toDouble())
    val globalPercent = (effectiveWeight / totalGlobalWeight * 100).toInt()

    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
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
            onDelete = {
                BrowserState.domainsList = BrowserState.domainsList.filter { it.url != domain.url }
            },
            stats = "G: $globalPercent%"
        )
        
        if (domain.pages.isNotEmpty()) {
            Row(modifier = Modifier.padding(start = 8.dp).clickable { expanded = !expanded }, verticalAlignment = Alignment.CenterVertically) {
                Icon(if (expanded) Icons.Default.ArrowDropUp else Icons.Default.ArrowDropDown, null, modifier = Modifier.size(12.dp), tint = MaterialTheme.colorScheme.primary)
                Text("${domain.pages.size} p", fontSize = 8.sp, color = MaterialTheme.colorScheme.primary)
            }

            AnimatedVisibility(visible = expanded) {
                val totalPageWeight = domain.pages.sumOf { p ->
                    val m = BrowserState.groupSettings[p.assignedColorGroup]?.multiplier ?: 1.0f
                    maxOf(0.01, (p.affinityScore + p.settings.modifier + BrowserState.globalSettings.modifier).toDouble() * m)
                }

                Column(modifier = Modifier.padding(start = 12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    domain.pages.forEach { page ->
                        val pageWeight = (page.affinityScore + page.settings.modifier + BrowserState.globalSettings.modifier).toDouble() * (BrowserState.groupSettings[page.assignedColorGroup]?.multiplier ?: 1.0f)
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
                            onDelete = {
                                BrowserState.domainsList = BrowserState.domainsList.map { d ->
                                    if (d.url == domain.url) d.copy(pages = d.pages.filter { it.path != page.path }) else d
                                }
                            },
                            stats = "$inDomainPercent%/$pageGlobalPercent%"
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun NavigationTweaksCard(prefs: android.content.SharedPreferences, onBack: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.1f)),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f))
    ) {
        Column(modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Box(modifier = Modifier.weight(1f)) { ToggleRow("R", BrowserState.swipeRightNext) { BrowserState.swipeRightNext = it } }
                Box(modifier = Modifier.weight(1f)) { ToggleRow("L", BrowserState.swipeLeftNext) { BrowserState.swipeLeftNext = it } }
                Box(modifier = Modifier.weight(1f)) { ToggleRow("T", BrowserState.doubleTapNext) { BrowserState.doubleTapNext = it } }
            }

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("Forward", style = MaterialTheme.typography.labelSmall, fontSize = 9.sp)
                PrecisionControlInt(
                    value = BrowserState.forwardBufferCount,
                    onValueChange = { 
                        BrowserState.forwardBufferCount = it
                        prefs.edit().putInt("forward_buffer_count", it).apply()
                    },
                    range = 1..10
                )
            }

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("History", style = MaterialTheme.typography.labelSmall, fontSize = 9.sp)
                PrecisionControlInt(
                    value = BrowserState.historyBufferCount,
                    onValueChange = { 
                        BrowserState.historyBufferCount = it
                        prefs.edit().putInt("history_buffer_count", it).apply()
                    },
                    range = 1..20
                )
            }
            
            Button(
                onClick = { 
                    BrowserState.currentUrlToLoad = "malachite://welcome"
                    onBack()
                },
                modifier = Modifier.fillMaxWidth().height(24.dp),
                contentPadding = PaddingValues(0.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary),
                shape = MaterialTheme.shapes.extraSmall
            ) {
                Text("Welcome/Setup", fontSize = 9.sp)
            }
        }
    }
}

@Composable
fun GroupChannelsList() {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        AppColorGroup.entries.forEach { group ->
            val current = BrowserState.groupSettings[group] ?: GroupSettings(1.0f, 5)
            GroupControlBlock(
                group = group,
                multiplier = current.multiplier,
                snooze = current.snoozeMinutes,
                onMultiplierChange = { newM -> 
                    val newMap = BrowserState.groupSettings.toMutableMap()
                    newMap[group] = current.copy(multiplier = newM)
                    BrowserState.groupSettings = newMap
                },
                onSnoozeChange = { newS ->
                    val newMap = BrowserState.groupSettings.toMutableMap()
                    newMap[group] = current.copy(snoozeMinutes = newS)
                    BrowserState.groupSettings = newMap
                }
            )
        }
    }
}

@Composable
fun AdvancedEngineSettingsCard(
    remoteDebuggingEnabled: Boolean,
    onRemoteDebuggingChange: (Boolean) -> Unit,
    onShowAddons: () -> Unit,
    onBack: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.1f)),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f))
    ) {
        Column(modifier = Modifier.padding(2.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            ToggleRow("Debug", remoteDebuggingEnabled) { 
                onRemoteDebuggingChange(it)
                GeckoEngineManager.setRemoteDebuggingEnabled(it)
            }
            
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Button(
                    onClick = onShowAddons,
                    modifier = Modifier.weight(1f).height(24.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.tertiary),
                    shape = MaterialTheme.shapes.extraSmall,
                    contentPadding = PaddingValues(0.dp)
                ) {
                    Icon(Icons.Default.Extension, null, modifier = Modifier.size(12.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Add-ons", fontSize = 9.sp)
                }
                
                OutlinedButton(
                    onClick = { 
                        BrowserState.currentUrlToLoad = "about:config"
                        onBack()
                    },
                    modifier = Modifier.weight(1f).height(24.dp),
                    shape = MaterialTheme.shapes.extraSmall,
                    contentPadding = PaddingValues(0.dp)
                ) {
                    Icon(Icons.Default.SettingsSuggest, null, modifier = Modifier.size(12.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Config", fontSize = 9.sp)
                }
            }
        }
    }
}

@Composable
fun Dot(group: AppColorGroup, isSelected: Boolean, onClick: (AppColorGroup) -> Unit) {
    Box(
        modifier = Modifier
            .size(12.dp)
            .clip(CircleShape)
            .background(getGroupColor(group))
            .clickable { onClick(group) }
            .border(1.dp, if (isSelected) Color.White else Color.Transparent, CircleShape)
    )
}

@Composable
fun ToggleRow(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(modifier = Modifier.height(20.dp), horizontalArrangement = Arrangement.spacedBy(2.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.labelSmall, fontSize = 8.sp, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onCheckedChange, modifier = Modifier.scale(0.45f))
    }
}

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

@Composable
fun DataPortabilityCard() {
    val context = LocalContext.current
    
    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        uri?.let { PersistenceManager.exportToJson(context, it) }
    }

    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let { PersistenceManager.importFromJson(context, it) }
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.1f)),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f))
    ) {
        Column(modifier = Modifier.padding(4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Button(
                    onClick = { exportLauncher.launch("malachite_backup.json") },
                    modifier = Modifier.weight(1f).height(28.dp),
                    shape = MaterialTheme.shapes.extraSmall,
                    contentPadding = PaddingValues(0.dp)
                ) {
                    Icon(Icons.Default.Upload, null, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Export", fontSize = 10.sp)
                }

                Button(
                    onClick = { importLauncher.launch("application/json") },
                    modifier = Modifier.weight(1f).height(28.dp),
                    shape = MaterialTheme.shapes.extraSmall,
                    contentPadding = PaddingValues(0.dp)
                ) {
                    Icon(Icons.Default.Download, null, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Import", fontSize = 10.sp)
                }
            }
            
            OutlinedButton(
                onClick = { 
                    BrowserState.history.clear()
                    GeckoEngineManager.clearAllData()
                    android.widget.Toast.makeText(context, "History & Cookies Cleared", android.widget.Toast.LENGTH_SHORT).show()
                },
                modifier = Modifier.fillMaxWidth().height(24.dp),
                shape = MaterialTheme.shapes.extraSmall,
                contentPadding = PaddingValues(0.dp)
            ) {
                Icon(Icons.Default.DeleteForever, null, modifier = Modifier.size(12.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Clear History & Data", fontSize = 9.sp)
            }
        }
    }
}
