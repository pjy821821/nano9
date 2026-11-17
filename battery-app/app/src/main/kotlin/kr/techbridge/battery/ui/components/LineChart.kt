package kr.techbridge.battery.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp

/** 한 시계열. [points]는 (x, y) 쌍이며 x는 ms, y는 임의 단위. */
data class Series(val points: List<Offset>, val color: Color, val fill: Boolean = false, val dashed: Boolean = false)

/**
 * 가벼운 선 그래프. 외부 차트 라이브러리 없이 Canvas로 그린다.
 * 모든 시계열은 같은 x 범위를 쓰고 y는 [yMin]~[yMax]로 정규화된다(시계열별 정규화가 필요하면 미리 맞춰 넣는다).
 */
@Composable
fun LineChart(
    series: List<Series>,
    yMin: Float,
    yMax: Float,
    modifier: Modifier = Modifier,
    gridLines: Int = 3,
    xMin: Float? = null,
    xMax: Float? = null,
) {
    val grid = MaterialTheme.colorScheme.outlineVariant
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        if (gridLines > 0) {
            for (i in 0..gridLines) {
                val y = h * i / gridLines
                drawLine(grid, Offset(0f, y), Offset(w, y), strokeWidth = 1.dp.toPx())
            }
        }
        val allX = series.flatMap { s -> s.points.map { it.x } }
        if (allX.isEmpty()) return@Canvas
        val x0 = xMin ?: allX.min()
        val x1 = xMax ?: allX.max()
        val xr = (x1 - x0).takeIf { it > 0f } ?: 1f
        val yr = (yMax - yMin).takeIf { it > 0f } ?: 1f

        series.forEach { s ->
            if (s.points.size < 2) return@forEach
            val path = Path()
            s.points.forEachIndexed { i, p ->
                val px = (p.x - x0) / xr * w
                val py = h - ((p.y - yMin) / yr).coerceIn(0f, 1f) * h
                if (i == 0) path.moveTo(px, py) else path.lineTo(px, py)
            }
            if (s.fill) {
                val fillPath = Path().apply {
                    addPath(path)
                    val last = s.points.last()
                    lineTo((last.x - x0) / xr * w, h)
                    lineTo((s.points.first().x - x0) / xr * w, h)
                    close()
                }
                drawPath(fillPath, s.color.copy(alpha = 0.15f))
            }
            drawPath(
                path,
                s.color,
                style = Stroke(
                    width = 2.dp.toPx(),
                    pathEffect = if (s.dashed) PathEffect.dashPathEffect(floatArrayOf(8f, 6f)) else null,
                ),
            )
        }
    }
}
