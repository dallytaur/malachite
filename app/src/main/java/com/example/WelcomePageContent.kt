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

@Composable
fun WelcomePageContent(onNavigate: (String) -> Unit) {
    val scrollState = rememberScrollState()
    
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
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "Manifesto",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "\"I'm a UX nerd, not a 'good' dev. I built this to fix my ADHD. Talk is cheap. Send patches. — Linus Torvalds\"",
                    style = MaterialTheme.typography.bodyLarge,
                    fontStyle = androidx.compose.ui.text.font.FontStyle.Italic
                )
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
                shape = RoundedCornerShape(12.dp)
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
            modifier = Modifier.fillMaxWidth().height(56.dp),
            shape = RoundedCornerShape(12.dp)
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
