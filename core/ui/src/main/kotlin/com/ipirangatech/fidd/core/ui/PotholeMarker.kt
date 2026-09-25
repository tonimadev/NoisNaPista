package com.ipirangatech.fidd.core.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import androidx.core.graphics.ColorUtils
import androidx.core.graphics.PathParser
import androidx.core.graphics.createBitmap

/**
 * Marcador de buraco no mapa: a cratera do ícone do app vista de cima — redonda, num remendo de
 * asfalto com contorno branco pra destacar do mapa, e a borda interna mais escura dando
 * profundidade, como no ícone. Desenhado num [Bitmap] para servir tanto aos Markers do GoogleMap
 * quanto a sobreposições em Compose. É um buraco no chão, não um alfinete: ancore no centro
 * (0.5, 0.5).
 */
object PotholeMarker {
    const val COLOR_STRONG = 0xFFE53935.toInt()
    const val COLOR_MEDIUM = 0xFFFF9800.toInt()
    const val COLOR_LIGHT = 0xFFFDD835.toInt()

    /** Buracos relatados pela comunidade, diferentes das detecções deste aparelho. */
    const val COLOR_COMMUNITY = 0xFF8E24AA.toInt()

    private const val ASPHALT = 0xFF1E1E1E.toInt()

    // Contornos irregulares num viewport 24x24, centrados em (12, 12).
    private const val ASPHALT_PATH =
        "M22.30,12.00C22.24,13.72 21.59,15.53 20.75,17.05C19.91,18.57 18.71,20.23 17.25,21.09" +
            "C15.79,21.95 13.74,22.21 12.00,22.20C10.26,22.19 8.24,21.87 6.80,21.01" +
            "C5.36,20.14 4.19,18.50 3.34,17.00C2.49,15.50 1.77,13.71 1.70,12.00C1.63,10.29 2.03,8.21 2.91,6.75" +
            "C3.78,5.29 5.43,4.11 6.95,3.25C8.47,2.39 10.31,1.61 12.00,1.60C13.69,1.59 15.58,2.31 17.10,3.17" +
            "C18.62,4.02 20.23,5.28 21.09,6.75C21.96,8.22 22.36,10.28 22.30,12.00Z"
    private const val CRATER_PATH =
        "M19.15,13.45C18.87,14.61 18.05,15.71 17.25,16.63C16.45,17.56 15.46,18.63 14.35,19.02" +
            "C13.24,19.40 11.79,19.22 10.59,18.96C9.39,18.70 8.02,18.26 7.17,17.47" +
            "C6.31,16.68 5.83,15.34 5.46,14.19C5.09,13.04 4.78,11.75 4.94,10.57C5.11,9.39 5.65,7.97 6.45,7.10" +
            "C7.26,6.23 8.61,5.72 9.77,5.36C10.94,5.00 12.28,4.72 13.43,4.94C14.58,5.16 15.78,5.89 16.70,6.68" +
            "C17.62,7.47 18.56,8.53 18.97,9.66C19.38,10.79 19.44,12.29 19.15,13.45Z"

    // Área desenhada, com folga pro contorno.
    private const val VIEW_ORIGIN = 0.8f
    private const val VIEW_SIZE = 23.2f
    private const val OUTLINE_WIDTH = 1.4f

    /** Quanto o fundo claro da cratera desce em relação à borda, deixando a parede de cima à sombra. */
    private const val DEPTH_OFFSET = 2.2f

    private val cache = HashMap<Pair<Int, Float>, Bitmap>()

    fun severityColor(severity: Float): Int =
        when {
            severity > 20f -> COLOR_STRONG
            severity > 10f -> COLOR_MEDIUM
            else -> COLOR_LIGHT
        }

    /** [craterColor] pinta o fundo da cratera; a parede de cima é a mesma cor escurecida. */
    fun bitmap(
        context: Context,
        craterColor: Int,
        sizeDp: Float = 30f,
    ): Bitmap =
        synchronized(cache) {
            cache.getOrPut(craterColor to sizeDp) { draw(context, craterColor, sizeDp) }
        }

    private fun draw(
        context: Context,
        craterColor: Int,
        sizeDp: Float,
    ): Bitmap {
        val sizePx = (sizeDp * context.resources.displayMetrics.density).toInt()
        val scale = sizePx / VIEW_SIZE
        val bitmap = createBitmap(sizePx, sizePx)
        val canvas = Canvas(bitmap)
        canvas.scale(scale, scale)
        canvas.translate(-VIEW_ORIGIN, -VIEW_ORIGIN)

        val asphalt = path(ASPHALT_PATH)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = OUTLINE_WIDTH
        paint.color = Color.WHITE
        canvas.drawPath(asphalt, paint)

        paint.style = Paint.Style.FILL
        paint.color = ASPHALT
        canvas.drawPath(asphalt, paint)

        val crater = path(CRATER_PATH)
        paint.color = ColorUtils.blendARGB(craterColor, Color.BLACK, 0.45f)
        canvas.drawPath(crater, paint)
        canvas.save()
        canvas.clipPath(crater)
        canvas.translate(0f, DEPTH_OFFSET)
        paint.color = craterColor
        canvas.drawPath(crater, paint)
        canvas.restore()
        return bitmap
    }

    private fun path(data: String): Path = PathParser.createPathFromPathData(data)
}
