package com.geomeasure.app.rtk

object NmeaGgaParser {
    private val ggaPrefix = Regex("^\\$[A-Z0-9]{2}GGA,")
    private val utcTime = Regex("^\\d{6}(?:\\.\\d+)?$")

    fun parse(line: String): RtkPosition? {
        val sentence = line.trim()
        if (!ggaPrefix.containsMatchIn(sentence)) return null
        if (!checksumValid(sentence)) return null

        val body = sentence.substringBefore('*')
        val p = body.split(',')
        if (p.size < 15) return null

        val timestamp = p[1].ifBlank { null }
        if (timestamp != null && !utcTime.matches(timestamp)) return null
        val lat = parseCoordinate(p[2], p[3], isLatitude = true) ?: return null
        val lon = parseCoordinate(p[4], p[5], isLatitude = false) ?: return null
        val quality = p[6].toIntOrNull() ?: return null
        val satellites = p[7].toIntOrNull()?.takeIf { it in 0..99 } ?: return null
        val hdop = p[8].toDoubleOrNull()?.takeIf { it.isFinite() && it >= 0.0 }
        val orthometric = p[9].toDoubleOrNull()?.takeIf(Double::isFinite)
        val geoidSeparation = p[11].toDoubleOrNull()?.takeIf(Double::isFinite)
        val correctionAge = p[13].toDoubleOrNull()?.takeIf { it.isFinite() && it >= 0.0 }
        val ellipsoidal = if (orthometric != null && geoidSeparation != null) orthometric + geoidSeparation else null

        return RtkPosition(
            latitudeDeg = lat,
            longitudeDeg = lon,
            orthometricHeightM = orthometric,
            geoidSeparationM = geoidSeparation,
            ellipsoidalHeightM = ellipsoidal,
            fixType = when (quality) {
                0 -> RtkFixType.NONE
                1 -> RtkFixType.AUTONOMOUS
                2 -> RtkFixType.DGPS
                4 -> RtkFixType.RTK_FIXED
                5 -> RtkFixType.RTK_FLOAT
                6 -> RtkFixType.ESTIMATED
                else -> RtkFixType.UNKNOWN
            },
            satellites = satellites,
            hdop = hdop,
            correctionAgeSeconds = correctionAge,
            stationId = p[14].ifBlank { null },
            timestampUtc = timestamp,
        )
    }

    private fun parseCoordinate(raw: String, hemisphere: String, isLatitude: Boolean): Double? {
        if (raw.isBlank()) return null
        val normalizedHemisphere = hemisphere.uppercase()
        if (isLatitude && normalizedHemisphere !in setOf("N", "S")) return null
        if (!isLatitude && normalizedHemisphere !in setOf("E", "W")) return null

        val degreeDigits = if (isLatitude) 2 else 3
        if (raw.length <= degreeDigits) return null
        val degrees = raw.substring(0, degreeDigits).toDoubleOrNull() ?: return null
        val minutes = raw.substring(degreeDigits).toDoubleOrNull() ?: return null
        if (!degrees.isFinite() || !minutes.isFinite() || minutes !in 0.0..<60.0) return null
        val maxDegrees = if (isLatitude) 90.0 else 180.0
        if (degrees !in 0.0..maxDegrees) return null
        if (degrees == maxDegrees && minutes != 0.0) return null

        var value = degrees + minutes / 60.0
        if (normalizedHemisphere == "S" || normalizedHemisphere == "W") value = -value
        if (isLatitude && value !in -90.0..90.0) return null
        if (!isLatitude && value !in -180.0..180.0) return null
        return value
    }

    private fun checksumValid(sentence: String): Boolean {
        val star = sentence.indexOf('*')
        if (star < 0) return true // Some receiver streams legitimately omit a checksum.
        if (star + 3 != sentence.length) return false
        var checksum = 0
        for (i in 1 until star) checksum = checksum xor sentence[i].code
        val expected = sentence.substring(star + 1, star + 3).toIntOrNull(16) ?: return false
        return checksum == expected
    }
}
