package com.moneyset.app.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.moneyset.app.R
import com.moneyset.app.ui.theme.AppGradients
import com.moneyset.app.ui.theme.Indigo
import com.moneyset.app.ui.theme.Ink
import com.moneyset.app.ui.theme.InkSoft
import com.moneyset.app.ui.theme.RoseRed
import com.moneyset.app.ui.theme.TrackColor

@Composable
fun GradientCard(
    colors: List<Color>,
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(28.dp),
    onClick: (() -> Unit)? = null,
    content: @Composable BoxScope.() -> Unit,
) {
    Box(
        modifier
            .shadow(12.dp, shape, spotColor = colors.last().copy(alpha = 0.45f), ambientColor = colors.last().copy(alpha = 0.2f))
            .clip(shape)
            .background(Brush.linearGradient(colors))
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
        content = content,
    )
}

@Composable
fun WhiteCard(
    modifier: Modifier = Modifier,
    containerColor: Color = Color.White,
    borderColor: Color? = null,
    shape: Shape = RoundedCornerShape(24.dp),
    contentPadding: PaddingValues = PaddingValues(18.dp),
    verticalArrangement: Arrangement.Vertical = Arrangement.Top,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier
            .fillMaxWidth()
            .shadow(6.dp, shape, spotColor = Indigo.copy(alpha = 0.18f), ambientColor = Indigo.copy(alpha = 0.08f))
            .clip(shape)
            .background(containerColor)
            .then(if (borderColor != null) Modifier.border(1.5.dp, borderColor, shape) else Modifier)
            .padding(contentPadding),
        verticalArrangement = verticalArrangement,
        content = content,
    )
}

@Composable
fun Mascot(modifier: Modifier = Modifier) {
    Image(painter = painterResource(R.drawable.mascot), contentDescription = "Money Set", modifier = modifier)
}

/** Banner gradient có linh vật + bong bóng thoại (dùng ở đầu mỗi màn). */
@Composable
fun MascotBanner(
    colors: List<Color>,
    badge: String,
    badgeIcon: ImageVector,
    message: AnnotatedString,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
) {
    GradientCard(colors, modifier.fillMaxWidth(), onClick = onClick) {
        Canvas(Modifier.matchParentSize()) {
            drawCircle(Color.White.copy(alpha = 0.12f), radius = size.height * 0.6f, center = Offset(size.width * 0.12f, size.height * 1.1f))
            drawCircle(Color.White.copy(alpha = 0.10f), radius = size.height * 0.35f, center = Offset(size.width * 0.6f, -size.height * 0.05f))
        }
        Row(
            Modifier.fillMaxWidth().padding(start = 16.dp, top = 16.dp, bottom = 16.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Row(
                    Modifier
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.22f))
                        .padding(horizontal = 10.dp, vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(badgeIcon, null, tint = Color.White, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(badge, color = Color.White, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
                }
                Spacer(Modifier.height(12.dp))
                Text(
                    message,
                    modifier = Modifier
                        .clip(RoundedCornerShape(18.dp))
                        .background(Color.White)
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    color = Ink,
                )
            }
            Mascot(Modifier.size(118.dp))
        }
    }
}

@Composable
fun GradientProgressBar(
    progress: Float,
    colors: List<Color>,
    modifier: Modifier = Modifier,
    height: Dp = 10.dp,
    trackColor: Color = TrackColor,
) {
    val animated by animateFloatAsState(progress.coerceIn(0f, 1f), tween(800), label = "progress")
    val barColors = if (colors.size >= 2) colors else List(2) { colors.firstOrNull() ?: Indigo }
    Box(
        modifier
            .fillMaxWidth()
            .height(height)
            .clip(CircleShape)
            .background(trackColor),
    ) {
        if (animated > 0f) {
            Box(
                Modifier
                    .fillMaxWidth(animated)
                    .fillMaxHeight()
                    .clip(CircleShape)
                    .background(Brush.horizontalGradient(barColors)),
            )
        }
    }
}

@Composable
fun Pill(
    text: String,
    contentColor: Color,
    containerColor: Color,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
) {
    Row(
        modifier
            .clip(CircleShape)
            .background(containerColor)
            .padding(horizontal = 10.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(icon, null, tint = contentColor, modifier = Modifier.size(12.dp))
            Spacer(Modifier.width(4.dp))
        }
        Text(text, color = contentColor, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold, maxLines = 1)
    }
}

@Composable
fun CircleIconButton(
    icon: ImageVector,
    contentDescription: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    showBadge: Boolean = false,
    tint: Color = Ink,
) {
    Box(
        modifier
            .size(44.dp)
            .shadow(4.dp, CircleShape, spotColor = Indigo.copy(alpha = 0.25f))
            .clip(CircleShape)
            .background(Color.White)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription, tint = tint, modifier = Modifier.size(22.dp))
        if (showBadge) {
            Box(
                Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 9.dp, end = 10.dp)
                    .size(9.dp)
                    .clip(CircleShape)
                    .background(RoseRed),
            )
        }
    }
}

/** Thanh tiêu đề: nút back, dòng nhỏ + tiêu đề lớn ở giữa, action bên phải. */
@Composable
fun ScreenTopBar(
    overline: String,
    title: String,
    onBack: (() -> Unit)?,
    modifier: Modifier = Modifier,
    actions: @Composable RowScope.() -> Unit = {},
) {
    Row(
        modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (onBack != null) {
            CircleIconButton(Icons.AutoMirrored.Rounded.ArrowBack, "Quay lại", onBack)
        } else {
            Spacer(Modifier.size(44.dp))
        }
        Column(Modifier.weight(1f).padding(horizontal = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(overline, style = MaterialTheme.typography.labelLarge, color = InkSoft, textAlign = TextAlign.Center)
            Text(title, style = MaterialTheme.typography.titleLarge, color = Ink, textAlign = TextAlign.Center)
        }
        Row(Modifier.widthIn(min = 44.dp), horizontalArrangement = Arrangement.End, content = actions)
    }
}

@Composable
fun SectionTitle(title: String, modifier: Modifier = Modifier, trailing: @Composable () -> Unit = {}) {
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(title, style = MaterialTheme.typography.titleMedium, color = Ink, modifier = Modifier.weight(1f))
        trailing()
    }
}

@Composable
fun IconTile(icon: ImageVector, colors: List<Color>, modifier: Modifier = Modifier, size: Dp = 44.dp, shape: Shape = CircleShape) {
    Box(
        modifier
            .size(size)
            .clip(shape)
            .background(Brush.linearGradient(if (colors.size >= 2) colors else List(2) { colors.first() })),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, null, tint = Color.White, modifier = Modifier.size(size * 0.5f))
    }
}

/** Nút gradient full chiều ngang. */
@Composable
fun GradientButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    colors: List<Color> = AppGradients.Button,
    icon: ImageVector? = null,
    loading: Boolean = false,
    enabled: Boolean = true,
) {
    val shape = RoundedCornerShape(20.dp)
    Row(
        modifier
            .fillMaxWidth()
            .height(54.dp)
            .shadow(if (enabled) 10.dp else 0.dp, shape, spotColor = colors.last().copy(alpha = 0.5f))
            .clip(shape)
            .background(Brush.horizontalGradient(colors), alpha = if (enabled) 1f else 0.5f)
            .clickable(enabled = enabled && !loading, onClick = onClick),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (loading) {
            CircularProgressIndicator(Modifier.size(22.dp), color = Color.White, strokeWidth = 2.5.dp)
        } else {
            if (icon != null) {
                Icon(icon, null, tint = Color.White, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
            }
            Text(text, color = Color.White, style = MaterialTheme.typography.titleMedium)
        }
    }
}
