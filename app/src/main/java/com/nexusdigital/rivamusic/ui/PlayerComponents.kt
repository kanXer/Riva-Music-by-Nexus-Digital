package com.nexusdigital.rivamusic.ui

import android.content.Intent
import android.graphics.PointF
import androidx.compose.ui.platform.LocalContext
import androidx.compose.material.icons.rounded.Share
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.FullscreenExit
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Radio
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.nexusdigital.rivamusic.LyricsUi
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.graphics.graphicsLayer
import coil.compose.AsyncImage
import com.nexusdigital.rivamusic.data.Track
import com.nexusdigital.rivamusic.data.upgradeThumbnailQuality
import com.nexusdigital.rivamusic.theme.*
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2

/**
 * Animated Vinyl Record disk that wraps the track artwork.
 * - Spins continuously during playback.
 * - Supports scratch / spin gestures around the disk center to fast-forward or rewind!
 * - Enforces Google-auth rule: Guests are alerted to sign in with Google to seek.
 */
@Composable
fun VinylDiskPlayer(
    thumbnailUrl: String?,
    isPlaying: Boolean,
    canSeek: Boolean,
    positionMs: Long,
    durationMs: Long,
    onSeek: (Long) -> Unit,
    onRestrictedAction: (String) -> Unit,
    modifier: Modifier = Modifier,
    onThumbnailClick: (() -> Unit)? = null,
    contentOverlay: (@Composable () -> Unit)? = null,
) {
    val transition = rememberInfiniteTransition(label = "VinylSpin")
    val spinAngle by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 5000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "SpinAngle",
    )

    var manualRotation by remember { mutableFloatStateOf(0f) }
    var scrubNotice by remember { mutableStateOf<String?>(null) }
    var scrubAccumulator by remember { mutableFloatStateOf(0f) }

    LaunchedEffect(scrubNotice) {
        if (scrubNotice != null) {
            kotlinx.coroutines.delay(1200)
            scrubNotice = null
        }
    }

    val displayRotation = if (isPlaying) spinAngle + manualRotation else manualRotation

    val currentCanSeek by rememberUpdatedState(canSeek)
    val currentPositionMs by rememberUpdatedState(positionMs)
    val currentDurationMs by rememberUpdatedState(durationMs)
    val currentOnSeek by rememberUpdatedState(onSeek)
    val currentOnRestrictedAction by rememberUpdatedState(onRestrictedAction)
    val currentOnThumbnailClick by rememberUpdatedState(onThumbnailClick)

    var isDragging by remember { mutableStateOf(false) }
    var dragSeekPositionMs by remember { mutableLongStateOf(0L) }
    var dragStartPositionMs by remember { mutableLongStateOf(0L) }

    val displayPos = if (isDragging) dragSeekPositionMs else positionMs
    val progressFraction = (displayPos.toFloat() / durationMs.coerceAtLeast(1L).toFloat()).coerceIn(0f, 1f)

    Box(
        modifier = modifier
            .aspectRatio(1f)
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val center = Offset(size.width / 2f, size.height / 2f)
                    val rad = atan2(down.position.y - center.y, down.position.x - center.x)
                    var lastAngle = (rad * 180f / PI).toFloat()

                    var isDrag = false
                    val touchSlop = viewConfiguration.touchSlop

                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        if (!change.pressed) {
                            if (!isDrag) {
                                currentOnThumbnailClick?.invoke()
                            }
                            break
                        }

                        val dist = (change.position - down.position).getDistance()
                        if (!isDrag && dist > touchSlop) {
                            if (!currentCanSeek) {
                                currentOnRestrictedAction("Sign in with Google to rotate vinyl disk! 🔒")
                                while (true) {
                                    val ev = awaitPointerEvent()
                                    ev.changes.forEach { it.consume() }
                                    if (!ev.changes.any { it.pressed }) break
                                }
                                break
                            }
                            isDrag = true
                            isDragging = true
                            dragStartPositionMs = currentPositionMs
                            dragSeekPositionMs = currentPositionMs
                            scrubAccumulator = 0f
                            change.consume()
                        } else if (isDrag) {
                            change.consume()
                            val curRad = atan2(change.position.y - center.y, change.position.x - center.x)
                            val curAngle = (curRad * 180f / PI).toFloat()

                            var delta = curAngle - lastAngle
                            if (delta > 180f) delta -= 360f
                            if (delta < -180f) delta += 360f

                            manualRotation = (manualRotation + delta) % 360f
                            scrubAccumulator += delta

                            // 1.5x fast scrub: every 4 degrees, adjust 2.5 seconds
                            if (abs(scrubAccumulator) >= 4f) {
                                val steps = (scrubAccumulator / 4f).toInt()
                                val secondsChange = (steps * 2.5f).toLong()
                                val maxDur = currentDurationMs.coerceAtLeast(1L)
                                dragSeekPositionMs = (dragSeekPositionMs + secondsChange * 1000L).coerceIn(0L, maxDur)
                                val offsetSec = (dragSeekPositionMs - dragStartPositionMs) / 1000
                                val sign = if (offsetSec >= 0) "+" else ""
                                scrubNotice = "${sign}${offsetSec}s • ${formatDuration(dragSeekPositionMs)}"
                                scrubAccumulator -= steps * 4f
                            }
                            lastAngle = curAngle
                        }
                    }

                    if (isDragging) {
                        isDragging = false
                        currentOnSeek(dragSeekPositionMs)
                    }
                    scrubAccumulator = 0f
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        // === STATIONARY MUSIC PROGRESS BAR AROUND DISK PERIMETER ===
        Canvas(modifier = Modifier.fillMaxSize()) {
            val center = Offset(size.width / 2f, size.height / 2f)
            val ringRadius = (size.minDimension / 2f) - 4.dp.toPx()

            // Outer subtle track groove
            drawCircle(
                color = Color.White.copy(alpha = 0.08f),
                radius = ringRadius,
                center = center,
                style = Stroke(width = 3.5.dp.toPx(), cap = StrokeCap.Round),
            )

            // Dynamic music progress arc (sweeps clockwise from 12 o'clock)
            if (progressFraction > 0f) {
                val sweepAngle = progressFraction * 360f
                val progressBrush = Brush.sweepGradient(
                    0.0f to Color(0xFFD4FF00), // Lime
                    0.4f to Color(0xFF10B981), // Emerald
                    0.8f to Color(0xFF38BDF8), // Cyan/Sky
                    1.0f to Color(0xFFD4FF00), // Lime loop
                    center = center,
                )
                drawArc(
                    brush = progressBrush,
                    startAngle = -90f,
                    sweepAngle = sweepAngle,
                    useCenter = false,
                    topLeft = Offset(center.x - ringRadius, center.y - ringRadius),
                    size = androidx.compose.ui.geometry.Size(ringRadius * 2f, ringRadius * 2f),
                    style = Stroke(width = 4.dp.toPx(), cap = StrokeCap.Round),
                )

                // Glowing indicator head at current progress position
                val headAngleRad = Math.toRadians((sweepAngle - 90f).toDouble()).toFloat()
                val headX = center.x + ringRadius * kotlin.math.cos(headAngleRad)
                val headY = center.y + ringRadius * kotlin.math.sin(headAngleRad)

                // Neon glow aura
                drawCircle(
                    color = Color(0xFFD4FF00).copy(alpha = 0.45f),
                    radius = 7.dp.toPx(),
                    center = Offset(headX, headY),
                )
                // White core bead
                drawCircle(
                    color = Color.White,
                    radius = 3.5.dp.toPx(),
                    center = Offset(headX, headY),
                )
            }
        }

        // === ROTATING PREMIUM VINYL RECORD DISC ===
        Canvas(
            modifier = Modifier
                .fillMaxSize(0.92f)
                .rotate(displayRotation),
        ) {
            val radius = size.minDimension / 2f
            val center = Offset(size.width / 2f, size.height / 2f)

            // Deep obsidian vinyl base
            drawCircle(
                color = Color(0xFF0D0D10),
                radius = radius,
                center = center,
            )

            // Metallic outer rim border
            drawCircle(
                brush = Brush.sweepGradient(
                    listOf(
                        Color(0xFF3E3E46),
                        Color(0xFF1E1E22),
                        Color(0xFF52525C),
                        Color(0xFF1E1E22),
                        Color(0xFF3E3E46),
                    ),
                    center = center,
                ),
                radius = radius - 1.dp.toPx(),
                center = center,
                style = Stroke(width = 2.5.dp.toPx()),
            )

            // Realistic high-density sound microgrooves (12 concentric rings)
            val grooveCount = 12
            val startR = radius * 0.64f
            val step = (radius * 0.95f - startR) / grooveCount
            for (i in 0 until grooveCount) {
                drawCircle(
                    color = Color.White.copy(alpha = if (i % 3 == 0) 0.055f else if (i % 2 == 0) 0.035f else 0.02f),
                    radius = startR + (i * step),
                    center = center,
                    style = Stroke(width = if (i % 4 == 0) 1.2.dp.toPx() else 0.8.dp.toPx()),
                )
            }

            // Anisotropic light sheen reflection (4 specular reflection quadrants)
            val sheenBrush = Brush.sweepGradient(
                listOf(
                    Color.Transparent,
                    Color.White.copy(alpha = 0.09f),
                    Color.Transparent,
                    Color.White.copy(alpha = 0.09f),
                    Color.Transparent,
                    Color.White.copy(alpha = 0.09f),
                    Color.Transparent,
                    Color.White.copy(alpha = 0.09f),
                    Color.Transparent,
                ),
                center = center,
            )
            drawCircle(
                brush = sheenBrush,
                radius = radius - 2.dp.toPx(),
                center = center,
            )

            // Center Label gold/lime accent ring
            drawCircle(
                color = Color(0xFFD4FF00).copy(alpha = 0.35f),
                radius = radius * 0.65f,
                center = center,
                style = Stroke(width = 1.5.dp.toPx()),
            )
        }

        // Center Album Artwork (Circular Label)
        Box(
            modifier = Modifier
                .fillMaxSize(0.60f)
                .clip(CircleShape)
                .border(2.5.dp, Color.White.copy(alpha = 0.28f), CircleShape)
                .background(InkSoft),
            contentAlignment = Alignment.Center,
        ) {
            if (contentOverlay != null) {
                contentOverlay()
            } else {
                AsyncImage(
                    model = upgradeThumbnailQuality(thumbnailUrl),
                    contentDescription = "Track Artwork",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            }

            // Center machined spindle hole
            Box(
                modifier = Modifier
                    .size(26.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF141416))
                    .border(2.dp, Color(0xFFE2E8F0).copy(alpha = 0.6f), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(Ink),
                )
            }
        }

        // On-screen scrub notification pill
        if (scrubNotice != null) {
            Box(
                modifier = Modifier
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.85f))
                    .border(1.5.dp, Lime, CircleShape)
                    .padding(horizontal = 18.dp, vertical = 9.dp),
            ) {
                Text(
                    text = scrubNotice.orEmpty(),
                    color = Lime,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 16.sp,
                )
            }
        }
    }
}

/**
 * Clean & elegant fallback displayed when lyrics are unavailable for a song.
 */
@Composable
fun NoLyricsAvailableView(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            modifier = Modifier
                .size(72.dp)
                .clip(CircleShape)
                .background(if (RivaIsDark) Color.White.copy(alpha = 0.08f) else PanelElevated)
                .border(1.dp, if (RivaIsDark) Color.White.copy(alpha = 0.14f) else PanelBorder, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Rounded.GraphicEq,
                contentDescription = null,
                tint = Lime,
                modifier = Modifier.size(36.dp),
            )
        }

        Spacer(Modifier.height(18.dp))
        Text(
            text = "No Lyrics Available",
            style = MaterialTheme.typography.titleMedium,
            color = TextPrimary,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = "Enjoy the music, feel the rhythm & vibe",
            style = MaterialTheme.typography.bodyMedium,
            color = TextSecondary,
            textAlign = TextAlign.Center,
        )
    }
}

/**
 * Bottom Sheet displaying 50 songs of the same vibe / genre / artist
 * powered by YouTube Music's Automix Radio API.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SimilarTracksSheet(
    parentTrack: Track,
    tracks: List<Track>,
    isLoading: Boolean,
    currentTrackId: String?,
    onDismiss: () -> Unit,
    onPlayTrack: (Track, List<Track>) -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = Panel,
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(top = 10.dp)
                    .width(36.dp)
                    .height(4.dp)
                    .clip(CircleShape)
                    .background(if (RivaIsDark) Color.White.copy(alpha = 0.3f) else PanelBorder),
            )
        },
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.75f)
                .padding(horizontal = 20.dp, vertical = 6.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Rounded.Radio,
                            contentDescription = null,
                            tint = Lime,
                            modifier = Modifier.size(20.dp),
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = "50 SIMILAR TRACKS",
                            style = MaterialTheme.typography.labelSmall,
                            color = Lime,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.2.sp,
                        )
                    }
                    Text(
                        text = "Vibe mix for ${parentTrack.title}",
                        style = MaterialTheme.typography.titleMedium,
                        color = TextPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        fontWeight = FontWeight.Bold,
                    )
                }

                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(if (RivaIsDark) Color.White.copy(alpha = 0.1f) else PanelElevated),
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Close,
                        contentDescription = "Close",
                        tint = TextPrimary,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }

            Spacer(Modifier.height(14.dp))

            if (isLoading && tracks.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(color = Lime, strokeWidth = 3.dp)
                        Spacer(Modifier.height(12.dp))
                        Text(
                            text = "Curating 50 matching songs...",
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextSecondary,
                        )
                    }
                }
            } else if (tracks.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "No similar tracks found for this song",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary,
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    itemsIndexed(tracks, key = { idx, item -> "similar-${item.id}-$idx" }) { index, item ->
                        val isCurrent = item.id == currentTrackId
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(16.dp))
                                .background(
                                    if (isCurrent) (if (RivaIsDark) Lime.copy(alpha = 0.18f) else Lime.copy(alpha = 0.22f))
                                    else (if (RivaIsDark) Color.White.copy(alpha = 0.05f) else PanelElevated),
                                )
                                .border(
                                    1.dp,
                                    if (isCurrent) Lime.copy(alpha = 0.6f)
                                    else (if (RivaIsDark) Color.White.copy(alpha = 0.08f) else PanelBorder),
                                    RoundedCornerShape(16.dp),
                                )
                                .clickable {
                                    onPlayTrack(item, tracks)
                                }
                                .padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = "#${index + 1}",
                                style = MaterialTheme.typography.labelSmall,
                                color = if (isCurrent) Lime else TextFaint,
                                modifier = Modifier.width(32.dp),
                                fontWeight = FontWeight.Bold,
                            )
                            AsyncImage(
                                model = item.thumbnailUrl,
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .size(46.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(InkSoft),
                            )
                            Spacer(Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = item.title,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = if (isCurrent) Lime else TextPrimary,
                                    fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Medium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Text(
                                    text = item.artist,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = TextSecondary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                            if (isCurrent) {
                                Icon(
                                    imageVector = Icons.Rounded.GraphicEq,
                                    contentDescription = "Playing",
                                    tint = Lime,
                                    modifier = Modifier.size(20.dp),
                                )
                            } else {
                                Icon(
                                    imageVector = Icons.Rounded.PlayArrow,
                                    contentDescription = "Play",
                                    tint = TextSecondary,
                                    modifier = Modifier.size(20.dp),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Apple Music iOS style glassy rounded button with subtle border and translucent frosted backdrop.
 */
@Composable
fun AppleGlassButton(
    icon: ImageVector,
    contentDescription: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tint: Color = TextPrimary,
    badgeText: String? = null,
    size: androidx.compose.ui.unit.Dp = 42.dp,
    iconSize: androidx.compose.ui.unit.Dp = 20.dp,
    isActive: Boolean = false,
) {
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(
                if (isActive) Lime.copy(alpha = 0.22f)
                else if (RivaIsDark) Color.White.copy(alpha = 0.08f) else PanelElevated
            )
            .border(
                1.dp,
                if (isActive) Lime.copy(alpha = 0.6f)
                else if (RivaIsDark) Color.White.copy(alpha = 0.16f) else PanelBorder,
                CircleShape
            )
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = if (isActive) Lime else tint,
            modifier = Modifier.size(iconSize),
        )
        if (badgeText != null) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .clip(CircleShape)
                    .background(Lime)
                    .padding(horizontal = 4.dp, vertical = 1.dp)
            ) {
                Text(
                    text = badgeText,
                    color = Ink,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}

/**
 * Shared Realtime Synced & Plain Lyrics View.
 * Displays synced lyrics (LRCLIB / BetterLyrics) with auto-scroll and highlight,
 * plain lyrics fallback, or loading / meme states.
 */
@Composable
fun SyncedLyricsView(
    lyrics: LyricsUi?,
    positionMs: Long,
    canSeek: Boolean,
    onSeek: (Long) -> Unit,
    onRestrictedAction: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (lyrics == null || lyrics.loading) {
        Column(
            modifier = modifier,
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            CircularProgressIndicator(color = Lime, strokeWidth = 3.dp)
            Spacer(Modifier.height(14.dp))
            Text(
                text = "Searching realtime lyrics from BetterLyrics...",
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondary,
            )
        }
    } else if (lyrics.failed || (!lyrics.synced && lyrics.lines.isEmpty())) {
        NoLyricsAvailableView(modifier = modifier)
    } else if (lyrics.synced && lyrics.syncedLines.isNotEmpty()) {
        val listState = rememberLazyListState()
        var manualOffsetMs by remember(lyrics) { mutableLongStateOf(0L) }
        val effectiveMs = (positionMs + manualOffsetMs).coerceAtLeast(0L)
        val activeIndex = lyrics.syncedLines.indexOfLast { it.startMs <= effectiveMs }

        LaunchedEffect(activeIndex) {
            if (activeIndex >= 0) {
                listState.animateScrollToItem(activeIndex, scrollOffset = -120)
            }
        }

        Column(modifier = modifier) {
            // Fine-tune timing nudge controls (allows syncing by ±0.5s if a specific upload has an intro delay)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp, vertical = 2.dp),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(if (RivaIsDark) Color.White.copy(alpha = 0.06f) else PanelBorder.copy(alpha = 0.5f))
                        .padding(horizontal = 6.dp, vertical = 2.dp),
                ) {
                    Text(
                        text = "-0.5s",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (manualOffsetMs < 0) Lime else TextSecondary,
                        modifier = Modifier
                            .clickable { manualOffsetMs -= 500L }
                            .padding(horizontal = 6.dp, vertical = 2.dp),
                    )
                    Text(
                        text = if (manualOffsetMs == 0L) "Sync" else "${if (manualOffsetMs > 0) "+" else ""}${manualOffsetMs / 1000f}s",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (manualOffsetMs != 0L) Lime else TextFaint,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier
                            .clickable { manualOffsetMs = 0L }
                            .padding(horizontal = 4.dp, vertical = 2.dp),
                    )
                    Text(
                        text = "+0.5s",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (manualOffsetMs > 0) Lime else TextSecondary,
                        modifier = Modifier
                            .clickable { manualOffsetMs += 500L }
                            .padding(horizontal = 6.dp, vertical = 2.dp),
                    )
                }
            }

            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentPadding = PaddingValues(vertical = 40.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                itemsIndexed(lyrics.syncedLines) { index, line ->
                    val isActive = index == activeIndex
                    val isPast = index < activeIndex
                    val distance = kotlin.math.abs(index - activeIndex)

                    Text(
                        text = line.text,
                        style = if (isActive) MaterialTheme.typography.headlineSmall else MaterialTheme.typography.titleMedium,
                        color = when {
                            isActive -> Lime
                            distance == 1 -> TextPrimary.copy(alpha = 0.70f)
                            isPast -> TextFaint.copy(alpha = 0.35f)
                            else -> TextSecondary.copy(alpha = 0.35f)
                        },
                        fontWeight = if (isActive) FontWeight.ExtraBold else FontWeight.Medium,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = if (isActive) 12.dp else 8.dp)
                            .graphicsLayer {
                                scaleX = if (isActive) 1.08f else 0.96f
                                scaleY = if (isActive) 1.08f else 0.96f
                            }
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                            ) {
                                if (canSeek) {
                                    onSeek(line.startMs)
                                } else {
                                    onRestrictedAction("Sign in with Google to jump to lyric lines! 🔒")
                                }
                            },
                    )
                }
            }
        }
    } else {
        Column(
            modifier = modifier.verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            lyrics.lines.forEachIndexed { index, line ->
                Text(
                    text = line.ifBlank { " " },
                    style = MaterialTheme.typography.titleMedium,
                    color = if (index % 2 == 0) TextPrimary else TextSecondary,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(10.dp))
            }
        }
    }
}

/**
 * Fullscreen Player Mode:
 * Shows a minimalist ambient player with large Vinyl disk & artwork.
 * Tapping anywhere toggles to full-screen Realtime Synced Lyrics (BetterLyrics / LRCLIB).
 * Tapping again switches back to the artwork.
 */
@Composable
fun FullScreenPlayerOverlay(
    track: Track,
    isPlaying: Boolean,
    positionMs: Long,
    durationMs: Long,
    lyrics: LyricsUi?,
    canSeek: Boolean,
    canRotateDisk: Boolean = canSeek,
    onSeek: (Long) -> Unit,
    onRestrictedAction: (String) -> Unit,
    onTogglePlayPause: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onDismiss: () -> Unit,
) {
    var showLyrics by remember { mutableStateOf(false) }
    val context = LocalContext.current

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false,
        ),
    ) {
        val bgColors = if (RivaIsDark) {
            listOf(
                Color(0xFF261917),
                Color(0xFF130A09),
                Color(0xFF090404),
            )
        } else {
            listOf(PlayerTop, PlayerMid, Ink)
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.radialGradient(
                        colors = bgColors,
                        radius = 1200f,
                    )
                )
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                ) {
                    showLyrics = !showLyrics
                },
        ) {
            // Ambient subtle glow behind center
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(340.dp)
                    .clip(CircleShape)
                    .background(
                        Brush.radialGradient(
                            listOf(
                                Lime.copy(alpha = if (RivaIsDark) 0.12f else 0.08f),
                                Color.Transparent,
                            )
                        )
                    )
            )

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .navigationBarsPadding()
                    .padding(horizontal = 20.dp, vertical = 6.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                // Top glassy bar with Dismiss/Exit Fullscreen button
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(if (RivaIsDark) Color.White.copy(alpha = 0.08f) else PanelElevated)
                            .border(1.dp, if (RivaIsDark) Color.White.copy(alpha = 0.14f) else PanelBorder, CircleShape)
                            .padding(horizontal = 14.dp, vertical = 6.dp),
                    ) {
                        Box(
                            modifier = Modifier
                                .size(7.dp)
                                .clip(CircleShape)
                                .background(if (isPlaying) Lime else Coral),
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = if (showLyrics) "REALTIME LYRICS" else "NOW PLAYING",
                            style = MaterialTheme.typography.labelSmall,
                            color = TextPrimary,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.2.sp,
                        )
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        AppleGlassButton(
                            icon = Icons.Rounded.Share,
                            contentDescription = "Share",
                            onClick = {
                                val sendIntent = Intent(Intent.ACTION_SEND).apply {
                                    type = "text/plain"
                                    putExtra(Intent.EXTRA_SUBJECT, "${track.title} - ${track.artist}")
                                    putExtra(
                                        Intent.EXTRA_TEXT,
                                        "Listen to \"${track.title}\" by ${track.artist} on Riva Music / YouTube Music:\nhttps://music.youtube.com/watch?v=${track.id}"
                                    )
                                }
                                context.startActivity(Intent.createChooser(sendIntent, "Share \"${track.title}\" via"))
                            },
                            size = 40.dp,
                            iconSize = 20.dp,
                        )

                        AppleGlassButton(
                            icon = Icons.Rounded.FullscreenExit,
                            contentDescription = "Exit Fullscreen",
                            onClick = onDismiss,
                            size = 40.dp,
                            iconSize = 22.dp,
                        )
                    }
                }

                // Center Content: Vinyl Disk & Title OR Realtime Synced Lyrics
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(vertical = 6.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    if (!showLyrics) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            VinylDiskPlayer(
                                thumbnailUrl = track.thumbnailUrl,
                                isPlaying = isPlaying,
                                canSeek = canRotateDisk,
                                positionMs = positionMs,
                                durationMs = durationMs,
                                onSeek = onSeek,
                                onRestrictedAction = onRestrictedAction,
                                modifier = Modifier
                                    .fillMaxWidth(0.80f)
                                    .aspectRatio(1f),
                            )

                            Spacer(Modifier.height(24.dp))

                            Text(
                                text = track.title,
                                style = MaterialTheme.typography.headlineMedium,
                                color = TextPrimary,
                                fontWeight = FontWeight.Bold,
                                textAlign = TextAlign.Center,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )

                            Spacer(Modifier.height(6.dp))

                            Text(
                                text = track.artist,
                                style = MaterialTheme.typography.titleMedium,
                                color = TextSecondary,
                                textAlign = TextAlign.Center,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )

                            Spacer(Modifier.height(14.dp))

                            Text(
                                text = "💡 Tap screen to view Realtime Lyrics",
                                style = MaterialTheme.typography.labelMedium,
                                color = TextFaint,
                            )
                        }
                    } else {
                        // Realtime Synced Lyrics in Full Screen
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .clip(RoundedCornerShape(24.dp))
                                .background(if (RivaIsDark) Color.White.copy(alpha = 0.04f) else PanelElevated)
                                .border(1.dp, if (RivaIsDark) Color.White.copy(alpha = 0.08f) else PanelBorder, RoundedCornerShape(24.dp))
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(bottom = 8.dp),
                            ) {
                                Text(
                                    text = "${track.title} • ${track.artist}",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = Lime,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }

                            SyncedLyricsView(
                                lyrics = lyrics,
                                positionMs = positionMs,
                                canSeek = canSeek,
                                onSeek = onSeek,
                                onRestrictedAction = onRestrictedAction,
                                modifier = Modifier.fillMaxSize(),
                            )
                        }
                    }
                }

                // Bottom minimal glassy transport controls
                Row(
                    modifier = Modifier
                        .padding(top = 4.dp, bottom = 10.dp)
                        .clip(CircleShape)
                        .background(if (RivaIsDark) Color.Black.copy(alpha = 0.55f) else PanelElevated.copy(alpha = 0.95f))
                        .border(1.dp, if (RivaIsDark) Color.White.copy(alpha = 0.16f) else PanelBorder, CircleShape)
                        .padding(horizontal = 24.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(28.dp),
                ) {
                    AppleGlassButton(
                        icon = Icons.Rounded.SkipPrevious,
                        contentDescription = "Previous",
                        onClick = onPrevious,
                        size = 40.dp,
                        iconSize = 22.dp,
                    )

                    Box(
                        modifier = Modifier
                            .size(54.dp)
                            .clip(CircleShape)
                            .background(Lime)
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                onClick = onTogglePlayPause,
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                            contentDescription = if (isPlaying) "Pause" else "Play",
                            tint = Ink,
                            modifier = Modifier.size(30.dp),
                        )
                    }

                    AppleGlassButton(
                        icon = Icons.Rounded.SkipNext,
                        contentDescription = "Next",
                        onClick = onNext,
                        size = 40.dp,
                        iconSize = 22.dp,
                    )
                }
            }
        }
    }
}
