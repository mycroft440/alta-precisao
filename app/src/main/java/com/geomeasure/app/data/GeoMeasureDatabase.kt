package com.geomeasure.app.data

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteException
import android.database.sqlite.SQLiteOpenHelper
import com.geomeasure.app.model.PointQuality
import com.geomeasure.app.model.SurveyPoint
import com.geomeasure.app.model.SurveyProject

class GeoMeasureDatabase(context: Context) : SQLiteOpenHelper(context, DB_NAME, null, DB_VERSION) {
    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE projects (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                name TEXT NOT NULL,
                created_at INTEGER NOT NULL,
                updated_at INTEGER NOT NULL
            )
            """.trimIndent(),
        )
        db.execSQL(
            """
            CREATE TABLE survey_points (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                project_id INTEGER NOT NULL,
                sequence_no INTEGER NOT NULL,
                latitude REAL NOT NULL,
                longitude REAL NOT NULL,
                ellipsoidal_height REAL,
                horizontal_accuracy REAL NOT NULL,
                vertical_accuracy REAL,
                satellites_used INTEGER NOT NULL,
                average_cn0 REAL,
                raw_measurements INTEGER NOT NULL,
                captured_at INTEGER NOT NULL,
                observation_count INTEGER NOT NULL,
                dispersion REAL NOT NULL,
                ellipse_major REAL,
                ellipse_minor REAL,
                ellipse_azimuth REAL,
                quality TEXT NOT NULL,
                FOREIGN KEY(project_id) REFERENCES projects(id) ON DELETE CASCADE,
                UNIQUE(project_id, sequence_no)
            )
            """.trimIndent(),
        )
        db.execSQL("CREATE INDEX idx_points_project ON survey_points(project_id, sequence_no)")
    }

    override fun onConfigure(db: SQLiteDatabase) {
        super.onConfigure(db)
        db.setForeignKeyConstraintsEnabled(true)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        // Never silently reinterpret an old survey database. A future schema bump must provide an
        // explicit migration so stored field measurements are not corrupted or dropped unnoticed.
        throw SQLiteException("Unsupported GeoMeasure database migration: $oldVersion -> $newVersion")
    }

    fun createProject(name: String): SurveyProject {
        val normalizedName = name.trim().ifBlank { "Novo terreno" }
        val now = System.currentTimeMillis()
        val id = writableDatabase.insertOrThrow(
            "projects",
            null,
            ContentValues().apply {
                put("name", normalizedName)
                put("created_at", now)
                put("updated_at", now)
            },
        )
        return SurveyProject(id, normalizedName, now, now)
    }

    fun listProjects(): List<SurveyProject> {
        val out = mutableListOf<SurveyProject>()
        readableDatabase.query(
            "projects",
            arrayOf("id", "name", "created_at", "updated_at"),
            null, null, null, null,
            "updated_at DESC",
        ).use { c ->
            while (c.moveToNext()) {
                out += SurveyProject(
                    id = c.getLong(0),
                    name = c.getString(1),
                    createdAtMillis = c.getLong(2),
                    updatedAtMillis = c.getLong(3),
                )
            }
        }
        return out
    }

    fun savePoint(projectId: Long, point: SurveyPoint): Long {
        val db = writableDatabase
        var id = -1L
        db.beginTransaction()
        try {
            id = db.insertOrThrow("survey_points", null, point.toValues(projectId))
            touchProject(db, projectId)
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
        return id
    }

    fun loadPoints(projectId: Long): List<SurveyPoint> {
        val out = mutableListOf<SurveyPoint>()
        readableDatabase.query(
            "survey_points",
            POINT_COLUMNS,
            "project_id=?",
            arrayOf(projectId.toString()),
            null, null,
            "sequence_no ASC",
        ).use { c ->
            while (c.moveToNext()) {
                fun nullableDouble(index: Int): Double? = if (c.isNull(index)) null else c.getDouble(index)
                out += SurveyPoint(
                    databaseId = c.getLong(0),
                    projectId = c.getLong(1),
                    id = c.getInt(2),
                    latitudeDeg = c.getDouble(3),
                    longitudeDeg = c.getDouble(4),
                    ellipsoidalHeightM = nullableDouble(5),
                    horizontalAccuracyM = c.getDouble(6),
                    verticalAccuracyM = nullableDouble(7),
                    satellitesUsed = c.getInt(8),
                    averageCn0DbHz = nullableDouble(9),
                    rawMeasurements = c.getInt(10),
                    capturedAtMillis = c.getLong(11),
                    observationCount = c.getInt(12),
                    dispersionM = c.getDouble(13),
                    ellipseSemiMajorM = nullableDouble(14),
                    ellipseSemiMinorM = nullableDouble(15),
                    ellipseAzimuthDeg = nullableDouble(16),
                    quality = runCatching { PointQuality.valueOf(c.getString(17)) }.getOrDefault(PointQuality.POOR),
                )
            }
        }
        return out
    }

    fun deleteLastPoint(projectId: Long) {
        val db = writableDatabase
        db.beginTransaction()
        try {
            db.execSQL(
                "DELETE FROM survey_points WHERE id=(SELECT id FROM survey_points WHERE project_id=? ORDER BY sequence_no DESC LIMIT 1)",
                arrayOf(projectId),
            )
            touchProject(db, projectId)
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    fun clearProjectPoints(projectId: Long) {
        val db = writableDatabase
        db.beginTransaction()
        try {
            db.delete("survey_points", "project_id=?", arrayOf(projectId.toString()))
            touchProject(db, projectId)
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    private fun touchProject(db: SQLiteDatabase, projectId: Long) {
        db.update(
            "projects",
            ContentValues().apply { put("updated_at", System.currentTimeMillis()) },
            "id=?",
            arrayOf(projectId.toString()),
        )
    }

    private fun SurveyPoint.toValues(projectId: Long) = ContentValues().apply {
        put("project_id", projectId)
        put("sequence_no", id)
        put("latitude", latitudeDeg)
        put("longitude", longitudeDeg)
        putNullable("ellipsoidal_height", ellipsoidalHeightM)
        put("horizontal_accuracy", horizontalAccuracyM)
        putNullable("vertical_accuracy", verticalAccuracyM)
        put("satellites_used", satellitesUsed)
        putNullable("average_cn0", averageCn0DbHz)
        put("raw_measurements", rawMeasurements)
        put("captured_at", capturedAtMillis)
        put("observation_count", observationCount)
        put("dispersion", dispersionM)
        putNullable("ellipse_major", ellipseSemiMajorM)
        putNullable("ellipse_minor", ellipseSemiMinorM)
        putNullable("ellipse_azimuth", ellipseAzimuthDeg)
        put("quality", quality.name)
    }

    private fun ContentValues.putNullable(key: String, value: Double?) {
        if (value == null) putNull(key) else put(key, value)
    }

    companion object {
        private const val DB_NAME = "geomeasure.db"
        private const val DB_VERSION = 1
        private val POINT_COLUMNS = arrayOf(
            "id", "project_id", "sequence_no", "latitude", "longitude", "ellipsoidal_height",
            "horizontal_accuracy", "vertical_accuracy", "satellites_used", "average_cn0",
            "raw_measurements", "captured_at", "observation_count", "dispersion",
            "ellipse_major", "ellipse_minor", "ellipse_azimuth", "quality",
        )
    }
}
