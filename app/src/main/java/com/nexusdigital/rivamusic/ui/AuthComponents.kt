package com.nexusdigital.rivamusic.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.CloudDone
import androidx.compose.material.icons.rounded.ExitToApp
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.PersonOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.nexusdigital.rivamusic.R
import com.nexusdigital.rivamusic.data.UserProfile
import com.nexusdigital.rivamusic.theme.BgDeep
import com.nexusdigital.rivamusic.theme.Coral
import com.nexusdigital.rivamusic.theme.Ink
import com.nexusdigital.rivamusic.theme.InkSoft
import com.nexusdigital.rivamusic.theme.Lime
import com.nexusdigital.rivamusic.theme.Panel
import com.nexusdigital.rivamusic.theme.PanelBorder
import com.nexusdigital.rivamusic.theme.PanelElevated
import com.nexusdigital.rivamusic.theme.TextFaint
import com.nexusdigital.rivamusic.theme.TextPrimary
import com.nexusdigital.rivamusic.theme.TextSecondary

@Composable
fun WelcomeAuthScreen(
    onGoogleSignInClick: () -> Unit,
    onGuestSignInSubmit: (String) -> Unit,
) {
    var showGuestNameDialog by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(BgDeep, Ink, InkSoft)
                )
            ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .padding(horizontal = 28.dp, vertical = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Spacer(Modifier.height(20.dp))

            // Branding Section
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                BrandMark(size = 80.dp)
                Spacer(Modifier.height(20.dp))
                Text(
                    text = "Riva Music",
                    style = MaterialTheme.typography.headlineLarge,
                    color = TextPrimary,
                    fontWeight = FontWeight.Black,
                    letterSpacing = (-0.5).sp,
                )
                Text(
                    text = "Infinite music • Stream & sync offline",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary,
                    modifier = Modifier.padding(top = 6.dp),
                )
            }

            // Auth Actions Card
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(28.dp))
                    .background(PanelElevated.copy(alpha = 0.85f))
                    .border(1.dp, PanelBorder, RoundedCornerShape(28.dp))
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = "Get Started",
                    style = MaterialTheme.typography.titleMedium,
                    color = TextPrimary,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = "Sign in to backup your offline music to the cloud",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextFaint,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 4.dp, bottom = 20.dp),
                )

                // Google Sign In Button
                Button(
                    onClick = onGoogleSignInClick,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(54.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color.White,
                        contentColor = Color.Black,
                    ),
                    elevation = ButtonDefaults.buttonElevation(defaultElevation = 2.dp),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center,
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_google_logo),
                            contentDescription = "Google",
                            tint = Color.Unspecified,
                            modifier = Modifier.size(22.dp),
                        )
                        Spacer(Modifier.width(12.dp))
                        Text(
                            text = "Continue with Google",
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF1F1F1F),
                        )
                    }
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    HorizontalDivider(modifier = Modifier.weight(1f), color = PanelBorder)
                    Text(
                        text = "OR",
                        style = MaterialTheme.typography.labelSmall,
                        color = TextFaint,
                        modifier = Modifier.padding(horizontal = 12.dp),
                        fontWeight = FontWeight.Bold,
                    )
                    HorizontalDivider(modifier = Modifier.weight(1f), color = PanelBorder)
                }

                // Guest Sign In Button
                OutlinedButton(
                    onClick = { showGuestNameDialog = true },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = TextPrimary,
                    ),
                    border = ButtonDefaults.outlinedButtonBorder.copy(
                        brush = Brush.horizontalGradient(listOf(Lime, Lime.copy(alpha = 0.5f)))
                    ),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center,
                    ) {
                        Icon(
                            Icons.Rounded.PersonOutline,
                            contentDescription = null,
                            tint = Lime,
                            modifier = Modifier.size(20.dp),
                        )
                        Spacer(Modifier.width(10.dp))
                        Text(
                            text = "Continue as Guest",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = TextPrimary,
                        )
                    }
                }
            }

            Text(
                text = "By continuing, you agree to our Terms and Privacy Policy",
                style = MaterialTheme.typography.labelSmall,
                color = TextFaint,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(bottom = 8.dp),
            )
        }
    }

    if (showGuestNameDialog) {
        GuestNameInputDialog(
            onDismiss = { showGuestNameDialog = false },
            onSubmit = { name ->
                showGuestNameDialog = false
                onGuestSignInSubmit(name)
            },
        )
    }
}

@Composable
fun GuestNameInputDialog(
    onDismiss: () -> Unit,
    onSubmit: (String) -> Unit,
) {
    var name by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = PanelElevated,
        shape = RoundedCornerShape(24.dp),
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(Lime.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Rounded.Person, contentDescription = null, tint = Lime)
                }
                Spacer(Modifier.width(12.dp))
                Text(
                    text = "Welcome Guest",
                    style = MaterialTheme.typography.titleLarge,
                    color = TextPrimary,
                    fontWeight = FontWeight.Bold,
                )
            }
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "What should we call you in Riva Music?",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary,
                    modifier = Modifier.padding(bottom = 14.dp),
                )
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    placeholder = { Text("Enter your name", color = TextFaint) },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = Panel,
                        unfocusedContainerColor = Panel,
                        focusedBorderColor = Lime,
                        unfocusedBorderColor = PanelBorder,
                        cursorColor = Lime,
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary,
                    ),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val finalName = name.trim().ifBlank { "Guest" }
                    onSubmit(finalName)
                },
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Lime, contentColor = Ink),
            ) {
                Text("Continue", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = TextSecondary)
            }
        },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileBottomSheet(
    userProfile: UserProfile,
    onDismiss: () -> Unit,
    onGoogleSignIn: () -> Unit,
    onChangeLanguages: () -> Unit,
    onSignOut: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = InkSoft,
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(vertical = 12.dp)
                    .size(width = 36.dp, height = 4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(TextFaint.copy(alpha = 0.4f)),
            )
        },
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 36.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // Top Close
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "My Profile",
                    style = MaterialTheme.typography.titleLarge,
                    color = TextPrimary,
                    fontWeight = FontWeight.Bold,
                )
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Rounded.Close, contentDescription = "Close", tint = TextSecondary)
                }
            }

            Spacer(Modifier.height(16.dp))

            // Avatar & Name Card
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
                    .background(PanelElevated)
                    .border(1.dp, PanelBorder, RoundedCornerShape(20.dp))
                    .padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                // Profile Avatar / DP
                Box(
                    modifier = Modifier
                        .size(80.dp)
                        .clip(CircleShape)
                        .border(2.dp, if (!userProfile.isGuest) Lime else PanelBorder, CircleShape)
                        .background(Panel),
                    contentAlignment = Alignment.Center,
                ) {
                    if (!userProfile.photoUrl.isNullOrBlank()) {
                        AsyncImage(
                            model = userProfile.photoUrl,
                            contentDescription = userProfile.displayName,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop,
                        )
                    } else {
                        Text(
                            text = userProfile.displayName.take(1).uppercase(),
                            style = MaterialTheme.typography.headlineMedium,
                            color = Lime,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }

                Spacer(Modifier.height(12.dp))

                Text(
                    text = userProfile.displayName,
                    style = MaterialTheme.typography.titleLarge,
                    color = TextPrimary,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )

                if (!userProfile.email.isNullOrBlank()) {
                    Text(
                        text = userProfile.email,
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }

                Spacer(Modifier.height(10.dp))

                // Account Type Badge
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (!userProfile.isGuest) Lime.copy(alpha = 0.18f) else Panel)
                        .padding(horizontal = 10.dp, vertical = 4.dp),
                ) {
                    if (!userProfile.isGuest) {
                        Icon(
                            Icons.Rounded.CheckCircle,
                            contentDescription = null,
                            tint = Lime,
                            modifier = Modifier.size(14.dp),
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = "Google Account • Cloud Synced",
                            style = MaterialTheme.typography.labelSmall,
                            color = Lime,
                            fontWeight = FontWeight.Bold,
                        )
                    } else {
                        Text(
                            text = "Guest Mode",
                            style = MaterialTheme.typography.labelSmall,
                            color = TextSecondary,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            // Cloud Sync Status / Upgrade Prompt
            if (userProfile.isGuest) {
                // Prompt to sign in with Google
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(Lime.copy(alpha = 0.1f))
                        .border(1.dp, Lime.copy(alpha = 0.3f), RoundedCornerShape(16.dp))
                        .clickable(onClick = onGoogleSignIn)
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_google_logo),
                        contentDescription = "Google",
                        tint = Color.Unspecified,
                        modifier = Modifier.size(24.dp),
                    )
                    Spacer(Modifier.width(14.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Sign in with Google",
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextPrimary,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            text = "Save offline songs & sync to database across devices",
                            style = MaterialTheme.typography.labelSmall,
                            color = TextSecondary,
                        )
                    }
                }
                Spacer(Modifier.height(12.dp))
            } else {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(PanelElevated)
                        .border(1.dp, PanelBorder, RoundedCornerShape(16.dp))
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        Icons.Rounded.CloudDone,
                        contentDescription = null,
                        tint = Lime,
                        modifier = Modifier.size(24.dp),
                    )
                    Spacer(Modifier.width(14.dp))
                    Column {
                        Text(
                            text = "Offline Music Synced",
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextPrimary,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            text = "Saved songs are stored in Firebase and restored automatically anywhere you log in.",
                            style = MaterialTheme.typography.labelSmall,
                            color = TextSecondary,
                        )
                    }
                }
                Spacer(Modifier.height(12.dp))
            }

            // Quick Actions: Change Languages
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(PanelElevated)
                    .border(1.dp, PanelBorder, RoundedCornerShape(16.dp))
                    .clickable {
                        onDismiss()
                        onChangeLanguages()
                    }
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    Icons.Rounded.Language,
                    contentDescription = null,
                    tint = Lime,
                    modifier = Modifier.size(22.dp),
                )
                Spacer(Modifier.width(14.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Music Languages & Region",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextPrimary,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = "Customize trending songs & home recommendations",
                        style = MaterialTheme.typography.labelSmall,
                        color = TextSecondary,
                    )
                }
            }

            Spacer(Modifier.height(16.dp))

            // Sign Out / Switch Account
            Button(
                onClick = onSignOut,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Coral.copy(alpha = 0.18f),
                    contentColor = Coral,
                ),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Rounded.ExitToApp,
                        contentDescription = null,
                        tint = Coral,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = if (userProfile.isGuest) "Switch Account / Sign In" else "Sign Out",
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        }
    }
}

@Composable
fun OfflineAuthPromptDialog(
    onDismiss: () -> Unit,
    onSignInGoogle: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = PanelElevated,
        shape = RoundedCornerShape(24.dp),
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_google_logo),
                    contentDescription = null,
                    tint = Color.Unspecified,
                    modifier = Modifier.size(24.dp),
                )
                Spacer(Modifier.width(12.dp))
                Text(
                    text = "Sign in to Save Offline",
                    style = MaterialTheme.typography.titleMedium,
                    color = TextPrimary,
                    fontWeight = FontWeight.Bold,
                )
            }
        },
        text = {
            Text(
                text = "Offline music saving is enabled for Google accounts. Sign in to download songs and sync them to your cloud database across devices.",
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondary,
            )
        },
        confirmButton = {
            Button(
                onClick = onSignInGoogle,
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Lime, contentColor = Ink),
            ) {
                Text("Sign in with Google", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Maybe Later", color = TextFaint)
            }
        },
    )
}
