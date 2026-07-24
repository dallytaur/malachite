package com.example

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

import android.app.Activity
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Extension
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.launch
import androidx.compose.runtime.rememberCoroutineScope

import android.content.Context
import android.content.ContextWrapper

fun findActivity(context: Context): Activity? {
    var currentContext = context
    while (currentContext is ContextWrapper) {
        if (currentContext is Activity) return currentContext
        currentContext = currentContext.baseContext
    }
    return null
}

@Composable
fun WelcomePageContent(onNavigate: (String) -> Unit) {
    val scrollState = rememberScrollState()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val identityManager = remember { IdentityManager(context) }
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        Text(
            text = "MALACHITE",
            style = MaterialTheme.typography.displayMedium,
            fontWeight = FontWeight.ExtraBold,
            color = MaterialTheme.colorScheme.primary
        )

        // Manifest
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
            shape = MaterialTheme.shapes.extraSmall
        ) {
            Column(modifier = Modifier.padding(8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (BrowserState.userProfile != null) "Welcome, ${BrowserState.userProfile?.displayName}" else "Manifesto",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f)
                    )
                    
                    if (BrowserState.userProfile == null) {
                        Button(
                            onClick = {
                                scope.launch {
                                    val activity = findActivity(context)
                                    if (activity != null) {
                                        val success = identityManager.requestVerifiedEmail(activity)
                                        if (success) {
                                            android.widget.Toast.makeText(context, "Account Connected!", android.widget.Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                }
                            },
                            modifier = Modifier.height(28.dp),
                            contentPadding = PaddingValues(horizontal = 8.dp),
                            shape = MaterialTheme.shapes.extraSmall
                        ) {
                            Text("Connect", fontSize = 9.sp)
                        }
                    }
                }
                
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "\"I'm a UX nerd, not a 'good' dev. I built this to fix my ADHD. Talk is cheap. Send patches. — Linus Torvalds\"",
                    style = MaterialTheme.typography.bodySmall,
                    fontStyle = androidx.compose.ui.text.font.FontStyle.Italic
                )
                
                BrowserState.userProfile?.let {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Connected: ${it.email}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f),
                        fontSize = 8.sp
                    )
                }
            }
        }

        // Tutorial
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                text = "The RYGB Flow: Feed -> Limit -> Decide",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
            
            TutorialItem(
                color = Color.Blue,
                title = "BLUE: Best",
                description = "AI, Dev Tools, Learning. 2.0x probability."
            )
            TutorialItem(
                color = Color.Green,
                title = "GREEN: Good",
                description = "Productivity & Search. 1.5x probability."
            )
            TutorialItem(
                color = Color.Yellow,
                title = "YELLOW: Careful",
                description = "News & Shopping. 0.7x probability."
            )
            TutorialItem(
                color = Color.Red,
                title = "RED: Addictive",
                description = "Social Media & Gaming. 0.3x probability & strict limits."
            )
        }

        // Add-ons Section
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.3f))
        ) {
            Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Icon(Icons.Default.Extension, null, tint = MaterialTheme.colorScheme.tertiary)
                Column {
                    Text("Powered by GeckoView", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall)
                    Text("uBlock Origin is auto-installed to keep your feed clean and distraction-free.", style = MaterialTheme.typography.bodySmall)
                }
            }
        }

        // App Library
        Text(
            text = "Suggested Library",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold
        )

        BrowserState.SuggestedLibrary.forEach { domain ->
            val isAdded = BrowserState.domainsList.any { it.url == domain.url }
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.extraSmall
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = domain.pages.firstOrNull()?.name ?: domain.url,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = domain.url,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Button(
                        onClick = {
                            if (!isAdded) {
                                BrowserState.domainsList = BrowserState.domainsList + domain
                            }
                        },
                        enabled = !isAdded,
                        colors = if (isAdded) ButtonDefaults.buttonColors(containerColor = Color.Gray) else ButtonDefaults.buttonColors()
                    ) {
                        Text(if (isAdded) "Added" else "Add to Feed")
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        Button(
            onClick = { onNavigate("https://www.google.com") },
            modifier = Modifier.fillMaxWidth().height(48.dp),
            shape = MaterialTheme.shapes.extraSmall
        ) {
            Text("ENTER THE FEED", fontWeight = FontWeight.Bold)
        }
        
        Spacer(modifier = Modifier.height(48.dp))
    }
}

@Composable
fun TutorialItem(color: Color, title: String, description: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Box(modifier = Modifier.size(16.dp).background(color, RoundedCornerShape(4.dp)))
        Column {
            Text(text = title, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyLarge)
            Text(text = description, style = MaterialTheme.typography.bodyMedium)
        }
    }
}
