package com.artest.egresine.arcore

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.util.Log
import android.view.View
import com.google.ar.core.Plane
import kotlin.math.min

/**
 * Overlay pour afficher les plans AR détectés
 */
class PlaneDetectionOverlay @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    companion object {
        private const val DOT_RADIUS = 4f
        private const val DOT_SPACING = 30f
        private const val MAX_DOTS_PER_PLANE = 200
        private const val FADE_DURATION = 300L
    }

    private val dotPaint = Paint().apply {
        color = Color.WHITE
        style = Paint.Style.FILL
        alpha = 180
        isAntiAlias = true
    }

    private val linePaint = Paint().apply {
        color = Color.argb(100, 255, 255, 255)
        style = Paint.Style.STROKE
        strokeWidth = 2f
        isAntiAlias = true
    }

    private var detectedPlanes = listOf<Plane>()
    private val planeDots = mutableMapOf<Int, List<PointF>>()

    private var lastUpdateTime = 0L
    private var fadeAlpha = 0f

    fun updatePlanes(planes: List<Plane>) {
        detectedPlanes = planes

        // Générer les points pour chaque plan
        planes.forEach { plane ->
            if (!planeDots.containsKey(plane.hashCode())) {
                planeDots[plane.hashCode()] = generateDotsForPlane(plane)
            }
        }

        // Animation fade-in
        if (planes.isNotEmpty()) {
            val currentTime = System.currentTimeMillis()
            if (lastUpdateTime == 0L) {
                lastUpdateTime = currentTime
            }

            val elapsed = currentTime - lastUpdateTime
            fadeAlpha = min(1f, elapsed / FADE_DURATION.toFloat())
            dotPaint.alpha = (180 * fadeAlpha).toInt()
        }

        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        if (detectedPlanes.isEmpty()) return

        // Dessiner les points pour chaque plan
        planeDots.forEach { (_, dots) ->
            dots.forEach { dot ->
                canvas.drawCircle(dot.x, dot.y, DOT_RADIUS, dotPaint)
            }
        }
    }

    /**
     * Génère une grille de points pour un plan
     */
    private fun generateDotsForPlane(plane: Plane): List<PointF> {
        val dots = mutableListOf<PointF>()

        try {
            // Obtenir les coordonnées du polygone du plan
            val polygon = plane.polygon

            if (polygon.remaining() < 6) return emptyList()

            // Convertir en liste de points 2D
            val points = mutableListOf<PointF>()
            polygon.rewind()

            while (polygon.hasRemaining() && points.size < 100) {
                // CORRECTION: Utiliser .get() au lieu de .float
                val x = polygon.get()
                val z = polygon.get()

                // Projection simple 3D -> 2D (pour visualisation)
                val screenX = (x * width / 4f) + width / 2f
                val screenY = (z * height / 4f) + height / 2f

                points.add(PointF(screenX, screenY))
            }

            if (points.size < 3) return emptyList()

            // Calculer le rectangle englobant
            val minX = points.minOf { it.x }
            val maxX = points.maxOf { it.x }
            val minY = points.minOf { it.y }
            val maxY = points.maxOf { it.y }

            // Générer une grille de points
            var x = minX
            var dotCount = 0

            while (x <= maxX && dotCount < MAX_DOTS_PER_PLANE) {
                var y = minY

                while (y <= maxY && dotCount < MAX_DOTS_PER_PLANE) {
                    val point = PointF(x, y)

                    // Vérifier si le point est dans le polygone
                    if (isPointInPolygon(point, points)) {
                        dots.add(point)
                        dotCount++
                    }

                    y += DOT_SPACING
                }

                x += DOT_SPACING
            }

        } catch (e: Exception) {
            Log.e("PlaneOverlay", "Erreur génération dots", e)
        }

        return dots
    }

    /**
     * Test si un point est dans un polygone (Ray casting)
     */
    private fun isPointInPolygon(point: PointF, polygon: List<PointF>): Boolean {
        if (polygon.size < 3) return false

        var inside = false
        var j = polygon.size - 1

        for (i in polygon.indices) {
            val pi = polygon[i]
            val pj = polygon[j]

            if ((pi.y > point.y) != (pj.y > point.y) &&
                point.x < (pj.x - pi.x) * (point.y - pi.y) / (pj.y - pi.y) + pi.x
            ) {
                inside = !inside
            }

            j = i
        }

        return inside
    }

    fun clear() {
        detectedPlanes = emptyList()
        planeDots.clear()
        fadeAlpha = 0f
        lastUpdateTime = 0L
        invalidate()
    }
}