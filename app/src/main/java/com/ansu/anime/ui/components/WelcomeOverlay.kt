package com.ansu.anime.ui.components

import android.os.Build
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.ansu.anime.R
import com.ansu.anime.ui.theme.AnsuColors
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

private const val TELEGRAM_URL = "https://t.me/anisu_anime_app"

/**
 * The one-time welcome card: an [ErrorGlassCard]-style frosted card in the middle of the screen over a
 * dimmed scrim, with the app logo on a slowly rotating Material shape at the top centre, a short
 * message, and the "Anisu Team" sign-off at the end.
 *
 * [onShowingChange] tells the caller when it is up so it can blur the screen behind it (Android 12+;
 * older versions just get a darker scrim). The card is closed only by its button, so a stray tap
 * outside never throws the welcome away; [onDismiss] is where the caller records that it was seen.
 *
 * Give it the full screen as [modifier]; the card centres itself.
 */
@Composable
fun WelcomeOverlay(
    visible: Boolean,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier) {
        AnimatedVisibility(
            visible = visible,
            enter = fadeIn(tween(260)),
            exit = fadeOut(tween(220)),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) 0.40f else 0.80f))
                    // The scrim swallows taps so nothing underneath can be pressed while the card is up.
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {}
                    .statusBarsPadding()
                    .navigationBarsPadding(),
                contentAlignment = Alignment.Center,
            ) {
                WelcomeGlassCard(
                    onDismiss = onDismiss,
                    modifier = Modifier
                        .padding(horizontal = 28.dp)
                        .animateEnterExit(
                            enter = scaleIn(tween(300), initialScale = 0.92f) + fadeIn(tween(300)),
                            exit = scaleOut(tween(220), targetScale = 0.96f) + fadeOut(tween(220)),
                        ),
                )
            }
        }
    }
}

@Composable
private fun WelcomeGlassCard(
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    FrostedGlassCard(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(32.dp),
        tintAlpha = 0.88f,
        blurRadius = 12.dp,
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                // Same wash as the error card, in the accent colour instead of red.
                .background(
                    Brush.verticalGradient(
                        0f to AnsuColors.Accent.copy(alpha = 0.20f),
                        0.65f to AnsuColors.Accent.copy(alpha = 0f),
                    ),
                ),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 24.dp, vertical = 28.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                RotatingLogoBadge()
                Text(
                    text = "Welcome to Anisu \uD83D\uDC4B",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = AnsuColors.TextPrimary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 22.dp),
                )
                Text(
                    text = "Hey, and thank you for being here! Anisu is made by anime fans, for anime fans, " +
                        "and we put a lot of care into every corner of it. Sign in with AniList to sync your lists, " +
                        "add sources to start streaming, and make the app yours in Settings.\n\n" +
                        "Found a bug or got an idea? Come hang out with us on Telegram, your feedback shapes what Anisu becomes. " +
                        "Now grab some snacks and enjoy the watch! \uD83C\uDF7F",
                    style = MaterialTheme.typography.bodyLarge,
                    color = AnsuColors.TextSecondary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 10.dp),
                )
                Text(
                    text = "With love, Anisu Team \u2764\uFE0F",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = AnsuColors.TextPrimary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 18.dp),
                )
                val uriHandler = LocalUriHandler.current
                OutlinedButton(
                    onClick = { runCatching { uriHandler.openUri(TELEGRAM_URL) } },
                    shape = RoundedCornerShape(20.dp),
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.22f)),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = AnsuColors.TextPrimary),
                    modifier = Modifier
                        .padding(top = 22.dp)
                        .fillMaxWidth()
                        .height(52.dp),
                ) {
                    Text(
                        text = "Join us on Telegram",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                    )
                }
                Button(
                    onClick = onDismiss,
                    shape = RoundedCornerShape(20.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color.White.copy(alpha = 0.16f),
                        contentColor = AnsuColors.TextPrimary,
                    ),
                    modifier = Modifier
                        .padding(top = 12.dp)
                        .fillMaxWidth()
                        .height(58.dp),
                ) {
                    Text(
                        text = "Let's go",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        }
    }
}

/** The logo's "A" centred on a Material cookie shape that turns slowly, in place of the error card's badge. */
@Composable
private fun RotatingLogoBadge() {
    val turn by rememberInfiniteTransition(label = "welcomeShape").animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(16_000, easing = LinearEasing), RepeatMode.Restart),
        label = "welcomeShapeTurn",
    )
    val shape = remember { cookiePath() }
    val accent = AnsuColors.Accent

    Box(modifier = Modifier.size(96.dp), contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val radius = size.minDimension / 2f - 2.dp.toPx()
            val strokeWidth = 1.5.dp.toPx()
            rotate(degrees = turn, pivot = center) {
                withTransform({
                    translate(center.x, center.y)
                    scale(radius, radius, Offset.Zero)
                }) {
                    drawPath(shape, accent.copy(alpha = 0.30f))
                    drawPath(shape, accent.copy(alpha = 0.60f), style = Stroke(width = strokeWidth / radius))
                }
            }
        }
        Image(
            painter = painterResource(R.drawable.splash_logo_a),
            contentDescription = "Anisu",
            contentScale = ContentScale.Fit,
            modifier = Modifier.size(44.dp),
        )
    }
}

/** A circle with soft scallops (the Material "cookie"), scaled so its farthest point sits at radius 1. */
private fun cookiePath(): Path {
    val steps = 180
    val radii = FloatArray(steps) { 1f + 0.09f * cos(9f * (it * 2 * PI / steps).toFloat()) }
    val maxR = radii.max()
    return Path().apply {
        for (i in 0 until steps) {
            val a = (i * 2 * PI / steps).toFloat()
            val x = cos(a) * radii[i] / maxR
            val y = sin(a) * radii[i] / maxR
            if (i == 0) moveTo(x, y) else lineTo(x, y)
        }
        close()
    }
}
