package com.geomeasure.app.geodesy

import net.sf.geographiclib.Geodesic
import net.sf.geographiclib.PolygonArea
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

object Wgs84 {
    private const val A = 6378137.0
    private const val F = 1.0 / 298.257223563
    private const val E2 = F * (2.0 - F)
    private const val DUPLICATE_VERTEX_TOLERANCE_M = 0.01

    data class Ecef(val x: Double, val y: Double, val z: Double)
    data class Enu(val east: Double, val north: Double, val up: Double)
    data class Geo(val latitudeDeg: Double, val longitudeDeg: Double, val heightM: Double)

    data class PolygonMeasurement(
        val areaSquareMeters: Double,
        val perimeterMeters: Double,
        val isValid: Boolean,
        val issue: String? = null,
    )

    fun toEcef(latitudeDeg: Double, longitudeDeg: Double, heightM: Double = 0.0): Ecef {
        require(latitudeDeg.isFinite() && latitudeDeg in -90.0..90.0)
        require(longitudeDeg.isFinite() && longitudeDeg in -180.0..180.0)
        require(heightM.isFinite())
        val lat = Math.toRadians(latitudeDeg)
        val lon = Math.toRadians(longitudeDeg)
        val sinLat = sin(lat)
        val cosLat = cos(lat)
        val n = A / sqrt(1.0 - E2 * sinLat * sinLat)
        return Ecef(
            x = (n + heightM) * cosLat * cos(lon),
            y = (n + heightM) * cosLat * sin(lon),
            z = (n * (1.0 - E2) + heightM) * sinLat,
        )
    }

    fun ecefToEnu(point: Ecef, origin: Geo): Enu {
        val originEcef = toEcef(origin.latitudeDeg, origin.longitudeDeg, origin.heightM)
        val dx = point.x - originEcef.x
        val dy = point.y - originEcef.y
        val dz = point.z - originEcef.z
        val lat = Math.toRadians(origin.latitudeDeg)
        val lon = Math.toRadians(origin.longitudeDeg)

        val east = -sin(lon) * dx + cos(lon) * dy
        val north = -sin(lat) * cos(lon) * dx - sin(lat) * sin(lon) * dy + cos(lat) * dz
        val up = cos(lat) * cos(lon) * dx + cos(lat) * sin(lon) * dy + sin(lat) * dz
        return Enu(east, north, up)
    }

    /** Robust ellipsoidal inverse geodesic using Karney's GeographicLib algorithm. */
    fun distanceMeters(lat1Deg: Double, lon1Deg: Double, lat2Deg: Double, lon2Deg: Double): Double {
        validateLatLon(lat1Deg, lon1Deg)
        validateLatLon(lat2Deg, lon2Deg)
        return Geodesic.WGS84.Inverse(lat1Deg, lon1Deg, lat2Deg, lon2Deg).s12
    }

    /** Kept for source compatibility with the MVP; implementation is now Karney, not Vincenty. */
    fun inverseVincentyMeters(lat1Deg: Double, lon1Deg: Double, lat2Deg: Double, lon2Deg: Double): Double =
        distanceMeters(lat1Deg, lon1Deg, lat2Deg, lon2Deg)

    fun measurePolygon(points: List<Geo>): PolygonMeasurement {
        if (points.isEmpty() || points.size == 1) {
            return PolygonMeasurement(0.0, 0.0, true)
        }
        points.forEach { validateLatLon(it.latitudeDeg, it.longitudeDeg) }

        if (points.size == 2) {
            val distance = distanceMeters(
                points[0].latitudeDeg,
                points[0].longitudeDeg,
                points[1].latitudeDeg,
                points[1].longitudeDeg,
            )
            if (distance < DUPLICATE_VERTEX_TOLERANCE_M) {
                return PolygonMeasurement(Double.NaN, Double.NaN, false, "Os dois vértices representam praticamente o mesmo ponto.")
            }
            // Two points define a segment, not a closed parcel perimeter.
            return PolygonMeasurement(0.0, distance, true)
        }

        val topologyIssue = validateTopology(points)
        if (topologyIssue != null) {
            return PolygonMeasurement(Double.NaN, Double.NaN, false, topologyIssue)
        }

        val polygon = PolygonArea(Geodesic.WGS84, false)
        points.forEach { polygon.AddPoint(it.latitudeDeg, it.longitudeDeg) }
        val result = polygon.Compute(false, true)
        val area = abs(result.area)
        val perimeter = result.perimeter
        if (!area.isFinite() || !perimeter.isFinite()) {
            return PolygonMeasurement(Double.NaN, Double.NaN, false, "Não foi possível calcular a geometria geodésica do terreno.")
        }
        return PolygonMeasurement(area, perimeter, true)
    }

    fun polygonAreaSquareMeters(points: List<Geo>): Double = measurePolygon(points).areaSquareMeters

    fun polygonPerimeterMeters(points: List<Geo>): Double = measurePolygon(points).perimeterMeters

    /**
     * Parcel topology validation is performed in a local ENU plane. For ordinary land parcels this
     * preserves segment intersection topology while the actual area/perimeter remains ellipsoidal.
     */
    private fun validateTopology(points: List<Geo>): String? {
        val heights = points.map { it.heightM }.filter { it.isFinite() }
        val origin = Geo(
            latitudeDeg = points.map { it.latitudeDeg }.average(),
            longitudeDeg = circularLongitudeMean(points.map { it.longitudeDeg }),
            heightM = heights.takeIf { it.isNotEmpty() }?.average() ?: 0.0,
        )
        val local = points.map { point ->
            val enu = ecefToEnu(toEcef(point.latitudeDeg, point.longitudeDeg, point.heightM), origin)
            Vec2(enu.east, enu.north)
        }

        for (i in points.indices) {
            val next = (i + 1) % points.size
            if (distance(local[i], local[next]) < DUPLICATE_VERTEX_TOLERANCE_M) {
                return "Há vértices consecutivos repetidos ou uma aresta praticamente nula (P${i + 1}–P${next + 1})."
            }
        }

        for (i in points.indices) {
            for (j in i + 1 until points.size) {
                if (areAdjacentVertices(i, j, points.size)) continue
                if (distance(local[i], local[j]) < DUPLICATE_VERTEX_TOLERANCE_M) {
                    return "O limite repete um vértice não adjacente (P${i + 1} e P${j + 1})."
                }
            }
        }

        for (i in points.indices) {
            val a1 = local[i]
            val a2 = local[(i + 1) % points.size]
            for (j in i + 1 until points.size) {
                if (areAdjacentEdges(i, j, points.size)) continue
                val b1 = local[j]
                val b2 = local[(j + 1) % points.size]
                if (segmentsIntersectOrTouch(a1, a2, b1, b2)) {
                    return "O limite do terreno se cruza, se toca ou possui lados sobrepostos. Corrija a ordem dos vértices."
                }
            }
        }
        return null
    }

    private fun areAdjacentVertices(i: Int, j: Int, n: Int): Boolean =
        j == i + 1 || (i == 0 && j == n - 1)

    private fun areAdjacentEdges(i: Int, j: Int, n: Int): Boolean =
        i == j || (i + 1) % n == j || (j + 1) % n == i

    private fun segmentsIntersectOrTouch(a: Vec2, b: Vec2, c: Vec2, d: Vec2): Boolean {
        val eps = 1e-7
        val o1 = orientation(a, b, c)
        val o2 = orientation(a, b, d)
        val o3 = orientation(c, d, a)
        val o4 = orientation(c, d, b)

        if (((o1 > eps && o2 < -eps) || (o1 < -eps && o2 > eps)) &&
            ((o3 > eps && o4 < -eps) || (o3 < -eps && o4 > eps))
        ) return true

        if (abs(o1) <= eps && onSegment(a, b, c, eps)) return true
        if (abs(o2) <= eps && onSegment(a, b, d, eps)) return true
        if (abs(o3) <= eps && onSegment(c, d, a, eps)) return true
        if (abs(o4) <= eps && onSegment(c, d, b, eps)) return true
        return false
    }

    private fun orientation(a: Vec2, b: Vec2, c: Vec2): Double =
        (b.x - a.x) * (c.y - a.y) - (b.y - a.y) * (c.x - a.x)

    private fun onSegment(a: Vec2, b: Vec2, p: Vec2, eps: Double): Boolean =
        p.x >= minOf(a.x, b.x) - eps && p.x <= maxOf(a.x, b.x) + eps &&
            p.y >= minOf(a.y, b.y) - eps && p.y <= maxOf(a.y, b.y) + eps

    private fun distance(a: Vec2, b: Vec2): Double {
        val dx = a.x - b.x
        val dy = a.y - b.y
        return sqrt(dx * dx + dy * dy)
    }

    private fun validateLatLon(latitudeDeg: Double, longitudeDeg: Double) {
        require(latitudeDeg.isFinite() && latitudeDeg in -90.0..90.0) { "Invalid latitude: $latitudeDeg" }
        require(longitudeDeg.isFinite() && longitudeDeg in -180.0..180.0) { "Invalid longitude: $longitudeDeg" }
    }

    private fun circularLongitudeMean(longitudesDeg: List<Double>): Double {
        val radians = longitudesDeg.map(Math::toRadians)
        val x = radians.sumOf { kotlin.math.cos(it) }
        val y = radians.sumOf { kotlin.math.sin(it) }
        return Math.toDegrees(kotlin.math.atan2(y, x))
    }

    private data class Vec2(val x: Double, val y: Double)
}
