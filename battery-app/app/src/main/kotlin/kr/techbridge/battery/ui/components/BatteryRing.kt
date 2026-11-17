package kr.techbridge.battery.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** 잔량 링 게이지. 와이어프레임의 홈 화면 중앙 요소. */
@Composable
fun BatteryRing(
    levelPercent: Int,
    color: Color,
    trackColor: Color = MaterialTheme.colorScheme.surfaceVariant,
    size: Dp = 236.dp,
    strokeWidth: Dp = 18.dp,
    stateLabel: String,
    subLabel: String?,
    modifier: Modifier = Modifier,
) {
    val progress by animateFloatAsState(
        targetValue = levelPercent.coerceIn(0, 100) / 100f,
        animationSpec = tween(600),
        label = "level",
    )
    Box(modifier = modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val stroke = strokeWidth.toPx()
            val inset = stroke / 2
            val arcSize = Size(this.size.width - stroke, this.size.height - stroke)
            val topLeft = Offset(inset, inset)
            drawArc(trackColor, 0f, 360f, false, topLeft, arcSize, style = Stroke(stroke))
            drawArc(color, -90f, 360f * progress, false, topLeft, arcSize, style = Stroke(stroke, cap = StrokeCap.Round))
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "$levelPercent%",
                fontSize = 52.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                letterSpacing = (-1.5).sp,
            )
            Text(text = stateLabel, style = MaterialTheme.typography.labelLarge, color = color, fontWeight = FontWeight.Medium)
            if (subLabel != null) {
                Text(text = subLabel, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
