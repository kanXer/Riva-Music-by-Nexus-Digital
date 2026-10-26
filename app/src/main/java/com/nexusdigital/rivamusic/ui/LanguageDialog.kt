package com.nexusdigital.rivamusic.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Translate
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.nexusdigital.rivamusic.theme.*

data class MusicRegion(
    val code: String,
    val name: String,
    val flag: String,
)

val AVAILABLE_REGIONS = listOf(
    MusicRegion("IN", "India", "🇮🇳"),
    MusicRegion("US", "United States", "🇺🇸"),
    MusicRegion("GB", "United Kingdom", "🇬🇧"),
    MusicRegion("CA", "Canada", "🇨🇦"),
    MusicRegion("AE", "UAE", "🇦🇪"),
    MusicRegion("AU", "Australia", "🇦🇺"),
    MusicRegion("PK", "Pakistan", "🇵🇰"),
    MusicRegion("NP", "Nepal", "🇳🇵"),
    MusicRegion("WW", "Worldwide", "🌍"),
)

data class MusicLanguage(
    val name: String,
    val nativeName: String,
)

val MUSIC_LANGUAGES = listOf(
    MusicLanguage("Hindi", "हिंदी"),
    MusicLanguage("English", "English"),
    MusicLanguage("Punjabi", "ਪੰਜਾਬੀ"),
    MusicLanguage("Bhojpuri", "भोजपुरी"),
    MusicLanguage("Haryanvi", "हरियाणवी"),
    MusicLanguage("Tamil", "தமிழ்"),
    MusicLanguage("Telugu", "తెలుగు"),
    MusicLanguage("Malayalam", "മലയാളം"),
    MusicLanguage("Kannada", "ಕನ್ನಡ"),
    MusicLanguage("Bengali", "বাংলা"),
    MusicLanguage("Marathi", "मराठी"),
    MusicLanguage("Gujarati", "ગુજરાતી"),
    MusicLanguage("Rajasthani", "राजस्थानी"),
    MusicLanguage("Urdu", "اردو"),
)

@Composable
fun LanguageSelectionDialog(
    initialRegion: MusicRegion = AVAILABLE_REGIONS.first(),
    initialSelected: Set<String>,
    onDismiss: () -> Unit,
    onSave: (MusicRegion, Set<String>) -> Unit,
) {
    var selectedRegion by remember(initialRegion) { mutableStateOf(initialRegion) }
    var selectedLanguages by remember(initialSelected) {
        mutableStateOf(
            if (initialSelected.isNotEmpty()) initialSelected else setOf("Hindi", "English")
        )
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(0.94f)
                .clip(RoundedCornerShape(28.dp))
                .background(PanelElevated)
                .border(1.dp, PanelBorder, RoundedCornerShape(28.dp))
                .padding(20.dp),
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                // Header icon
                Box(
                    modifier = Modifier
                        .size(46.dp)
                        .background(Lime.copy(alpha = 0.16f), CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Rounded.Translate,
                        contentDescription = null,
                        tint = Lime,
                        modifier = Modifier.size(24.dp),
                    )
                }

                Spacer(Modifier.height(12.dp))

                Text(
                    text = "Region & Music Languages",
                    style = MaterialTheme.typography.titleLarge,
                    color = TextPrimary,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                )

                Spacer(Modifier.height(4.dp))

                Text(
                    text = "Apna country aur pasandida bhashayein chunein for trending & personalized songs:",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary,
                    textAlign = TextAlign.Center,
                )

                Spacer(Modifier.height(16.dp))

                // Section 1: Region Selection
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "COUNTRY / REGION",
                        style = MaterialTheme.typography.labelSmall,
                        color = Lime,
                        fontWeight = FontWeight.Bold,
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = "(${selectedRegion.flag} ${selectedRegion.name})",
                        style = MaterialTheme.typography.labelSmall,
                        color = TextFaint,
                    )
                }

                Spacer(Modifier.height(8.dp))

                LazyRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(AVAILABLE_REGIONS, key = { it.code }) { region ->
                        val isSelected = selectedRegion.code == region.code
                        Row(
                            modifier = Modifier
                                .clip(RoundedCornerShape(13.dp))
                                .background(if (isSelected) Lime.copy(alpha = 0.22f) else Panel)
                                .border(
                                    width = if (isSelected) 1.5.dp else 1.dp,
                                    color = if (isSelected) Lime else PanelBorder,
                                    shape = RoundedCornerShape(13.dp),
                                )
                                .clickable { selectedRegion = region }
                                .padding(horizontal = 11.dp, vertical = 7.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(text = region.flag, style = MaterialTheme.typography.bodyMedium)
                            Spacer(Modifier.width(6.dp))
                            Text(
                                text = region.name,
                                style = MaterialTheme.typography.labelMedium,
                                color = if (isSelected) TextPrimary else TextSecondary,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                            )
                            if (isSelected) {
                                Spacer(Modifier.width(5.dp))
                                Icon(
                                    Icons.Rounded.Check,
                                    contentDescription = null,
                                    tint = Lime,
                                    modifier = Modifier.size(13.dp),
                                )
                            }
                        }
                    }
                }

                Spacer(Modifier.height(16.dp))

                // Section 2: Language Selection
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "PREFERRED LANGUAGES",
                        style = MaterialTheme.typography.labelSmall,
                        color = Lime,
                        fontWeight = FontWeight.Bold,
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = "(${selectedLanguages.size} selected)",
                        style = MaterialTheme.typography.labelSmall,
                        color = TextFaint,
                    )
                }

                Spacer(Modifier.height(8.dp))

                // Grid of language cards
                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 240.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(MUSIC_LANGUAGES, key = { it.name }) { lang ->
                        val isSelected = selectedLanguages.contains(lang.name)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(14.dp))
                                .background(
                                    if (isSelected) Lime.copy(alpha = 0.18f) else Panel
                                )
                                .border(
                                    width = if (isSelected) 1.5.dp else 1.dp,
                                    color = if (isSelected) Lime else PanelBorder,
                                    shape = RoundedCornerShape(14.dp),
                                )
                                .clickable {
                                    selectedLanguages = if (isSelected) {
                                        if (selectedLanguages.size > 1) selectedLanguages - lang.name else selectedLanguages
                                    } else {
                                        selectedLanguages + lang.name
                                    }
                                }
                                .padding(horizontal = 10.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = lang.name,
                                    style = MaterialTheme.typography.titleSmall,
                                    color = if (isSelected) TextPrimary else TextPrimary.copy(alpha = 0.9f),
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Text(
                                    text = lang.nativeName,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (isSelected) Lime else TextFaint,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                            Box(
                                modifier = Modifier
                                    .size(20.dp)
                                    .clip(CircleShape)
                                    .background(if (isSelected) Lime else Color.Transparent)
                                    .border(
                                        width = if (isSelected) 0.dp else 1.5.dp,
                                        color = if (isSelected) Color.Transparent else TextFaint.copy(alpha = 0.5f),
                                        shape = CircleShape,
                                    ),
                                contentAlignment = Alignment.Center,
                            ) {
                                if (isSelected) {
                                    Icon(
                                        Icons.Rounded.Check,
                                        contentDescription = null,
                                        tint = Ink,
                                        modifier = Modifier.size(13.dp),
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(Modifier.height(16.dp))

                // Actions
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TextButton(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(14.dp),
                    ) {
                        Text(
                            text = "Later",
                            color = TextSecondary,
                            style = MaterialTheme.typography.labelLarge,
                        )
                    }

                    Button(
                        onClick = {
                            val ordered = if (selectedLanguages.any { it.equals("Hindi", ignoreCase = true) }) {
                                val hindi = selectedLanguages.first { it.equals("Hindi", ignoreCase = true) }
                                val rest = selectedLanguages.filterNot { it.equals("Hindi", ignoreCase = true) }
                                linkedSetOf(hindi).apply { addAll(rest) }
                            } else {
                                selectedLanguages
                            }
                            onSave(selectedRegion, ordered)
                        },
                        modifier = Modifier.weight(1.5f),
                        enabled = selectedLanguages.isNotEmpty(),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Lime,
                            contentColor = Ink,
                            disabledContainerColor = PanelBorder,
                            disabledContentColor = TextFaint,
                        ),
                        shape = RoundedCornerShape(14.dp),
                    ) {
                        Text(
                            text = "Apply & Explore",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
            }
        }
    }
}
