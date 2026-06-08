package com.prplegryn.moe.data.local

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import com.prplegryn.moe.data.model.ActressInfo
import com.prplegryn.moe.data.model.AppSettings
import com.prplegryn.moe.data.model.CloudAuthState
import com.prplegryn.moe.data.model.CloudFile
import com.prplegryn.moe.data.model.LibraryItem
import com.prplegryn.moe.data.model.LibrarySnapshot
import com.prplegryn.moe.data.model.MediaResource
import com.prplegryn.moe.data.model.MovieMetadata
import com.prplegryn.moe.data.model.WatchProgress
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

class MoeDatabase(context: Context) : SQLiteOpenHelper(context, "moe.db", null, 2) {
    private val json = Json { ignoreUnknownKeys = true }

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE auth_state (
                id INTEGER PRIMARY KEY CHECK (id = 1),
                access_token TEXT NOT NULL,
                refresh_token TEXT,
                expires_at INTEGER,
                device_id TEXT NOT NULL,
                phone TEXT
            )
            """.trimIndent(),
        )
        db.execSQL(
            """
            CREATE TABLE resources (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                cloud_file_id TEXT NOT NULL UNIQUE,
                parent_id TEXT,
                name TEXT NOT NULL,
                size INTEGER NOT NULL,
                file_type INTEGER NOT NULL,
                is_directory INTEGER NOT NULL,
                download_url TEXT,
                imported_at INTEGER NOT NULL,
                updated_at INTEGER NOT NULL
            )
            """.trimIndent(),
        )
        db.execSQL(
            """
            CREATE TABLE metadata (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                resource_id INTEGER NOT NULL UNIQUE,
                content_id TEXT NOT NULL,
                title TEXT NOT NULL,
                original_title TEXT NOT NULL,
                description TEXT,
                release_date TEXT,
                runtime_minutes INTEGER NOT NULL,
                director TEXT,
                maker TEXT,
                label TEXT,
                series TEXT,
                rating_score REAL,
                rating_votes INTEGER,
                poster_url TEXT,
                cover_url TEXT,
                trailer_url TEXT,
                source_name TEXT NOT NULL,
                source_url TEXT NOT NULL,
                actresses TEXT NOT NULL,
                genres TEXT NOT NULL,
                screenshots TEXT NOT NULL,
                updated_at INTEGER NOT NULL,
                FOREIGN KEY(resource_id) REFERENCES resources(id) ON DELETE CASCADE
            )
            """.trimIndent(),
        )
        db.execSQL(
            """
            CREATE TABLE progress (
                resource_id INTEGER PRIMARY KEY,
                position_ms INTEGER NOT NULL,
                duration_ms INTEGER NOT NULL,
                updated_at INTEGER NOT NULL,
                FOREIGN KEY(resource_id) REFERENCES resources(id) ON DELETE CASCADE
            )
            """.trimIndent(),
        )
        createSettingsTable(db)
        db.execSQL("CREATE INDEX idx_resources_updated ON resources(updated_at DESC)")
        db.execSQL("CREATE INDEX idx_metadata_content_id ON metadata(content_id)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 2) {
            createSettingsTable(db)
        }
    }

    private fun createSettingsTable(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS app_settings (
                key TEXT PRIMARY KEY,
                value TEXT NOT NULL
            )
            """.trimIndent(),
        )
    }

    fun snapshot(): LibrarySnapshot {
        val auth = getAuth()
        val settings = getSettings()
        val metadata = getMetadata().associateBy { it.resourceId }
        val progress = getProgress().associateBy { it.resourceId }
        val items = getResources().map { resource ->
            LibraryItem(
                resource = resource,
                metadata = metadata[resource.id],
                progress = progress[resource.id],
            )
        }
        return LibrarySnapshot(auth, items, settings)
    }

    fun getSettings(): AppSettings = AppSettings(
        importPath = getSetting("import_path").orEmpty(),
    )

    fun saveImportPath(path: String) {
        saveSetting("import_path", path.trim())
    }

    private fun getSetting(key: String): String? = readableDatabase.query(
        "app_settings",
        arrayOf("value"),
        "key = ?",
        arrayOf(key),
        null,
        null,
        null,
    ).use { cursor ->
        if (!cursor.moveToFirst()) return@use null
        cursor.requiredString("value")
    }

    private fun saveSetting(key: String, value: String) {
        writableDatabase.insertWithOnConflict(
            "app_settings",
            null,
            ContentValues().apply {
                put("key", key)
                put("value", value)
            },
            SQLiteDatabase.CONFLICT_REPLACE,
        )
    }

    fun getAuth(): CloudAuthState? = readableDatabase.query(
        "auth_state",
        null,
        "id = 1",
        null,
        null,
        null,
        null,
    ).use { cursor ->
        if (!cursor.moveToFirst()) return@use null
        CloudAuthState(
            accessToken = cursor.requiredString("access_token"),
            refreshToken = cursor.optionalString("refresh_token"),
            expiresAtMillis = cursor.optionalLong("expires_at"),
            deviceId = cursor.requiredString("device_id"),
            phone = cursor.optionalString("phone"),
        )
    }

    fun saveAuth(auth: CloudAuthState) {
        writableDatabase.insertWithOnConflict(
            "auth_state",
            null,
            ContentValues().apply {
                put("id", 1)
                put("access_token", auth.accessToken)
                put("refresh_token", auth.refreshToken)
                put("expires_at", auth.expiresAtMillis)
                put("device_id", auth.deviceId)
                put("phone", auth.phone)
            },
            SQLiteDatabase.CONFLICT_REPLACE,
        )
    }

    fun clearAuth() {
        writableDatabase.delete("auth_state", "id = 1", null)
    }

    fun upsertResources(files: List<CloudFile>): Int {
        if (files.isEmpty()) return 0
        val db = writableDatabase
        val now = System.currentTimeMillis()
        var changed = 0
        db.beginTransaction()
        try {
            for (file in files) {
                val values = ContentValues().apply {
                    put("cloud_file_id", file.fileId)
                    put("parent_id", file.parentId)
                    put("name", file.name)
                    put("size", file.size)
                    put("file_type", file.fileType)
                    put("is_directory", if (file.isDirectory) 1 else 0)
                    put("updated_at", file.updatedAt.takeIf { it > 0L } ?: now)
                }
                val insertValues = ContentValues(values).apply {
                    put("imported_at", now)
                }
                val inserted = db.insertWithOnConflict(
                    "resources",
                    null,
                    insertValues,
                    SQLiteDatabase.CONFLICT_IGNORE,
                )
                if (inserted == -1L) {
                    db.update(
                        "resources",
                        values,
                        "cloud_file_id = ?",
                        arrayOf(file.fileId),
                    )
                }
                changed++
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
        return changed
    }

    fun updateDownloadUrl(resourceId: Long, url: String) {
        writableDatabase.update(
            "resources",
            ContentValues().apply {
                put("download_url", url)
                put("updated_at", System.currentTimeMillis())
            },
            "id = ?",
            arrayOf(resourceId.toString()),
        )
    }

    fun getResources(): List<MediaResource> = readableDatabase.query(
        "resources",
        null,
        "is_directory = 0",
        null,
        null,
        null,
        "updated_at DESC",
    ).use { cursor ->
        buildList {
            while (cursor.moveToNext()) add(cursor.toResource())
        }
    }

    fun saveMetadata(metadata: MovieMetadata) {
        val now = System.currentTimeMillis()
        writableDatabase.insertWithOnConflict(
            "metadata",
            null,
            ContentValues().apply {
                put("resource_id", metadata.resourceId)
                put("content_id", metadata.contentId)
                put("title", metadata.title)
                put("original_title", metadata.originalTitle)
                put("description", metadata.description)
                put("release_date", metadata.releaseDate)
                put("runtime_minutes", metadata.runtimeMinutes)
                put("director", metadata.director)
                put("maker", metadata.maker)
                put("label", metadata.label)
                put("series", metadata.series)
                put("rating_score", metadata.ratingScore)
                put("rating_votes", metadata.ratingVotes)
                put("poster_url", metadata.posterUrl)
                put("cover_url", metadata.coverUrl)
                put("trailer_url", metadata.trailerUrl)
                put("source_name", metadata.sourceName)
                put("source_url", metadata.sourceUrl)
                put("actresses", encodeActresses(metadata.actresses))
                put("genres", encodeStrings(metadata.genres))
                put("screenshots", encodeStrings(metadata.screenshots))
                put("updated_at", now)
            },
            SQLiteDatabase.CONFLICT_REPLACE,
        )
    }

    fun getMetadata(): List<MovieMetadata> = readableDatabase.query(
        "metadata",
        null,
        null,
        null,
        null,
        null,
        "updated_at DESC",
    ).use { cursor ->
        buildList {
            while (cursor.moveToNext()) add(cursor.toMetadata())
        }
    }

    fun saveProgress(resourceId: Long, positionMs: Long, durationMs: Long) {
        writableDatabase.insertWithOnConflict(
            "progress",
            null,
            ContentValues().apply {
                put("resource_id", resourceId)
                put("position_ms", positionMs.coerceAtLeast(0L))
                put("duration_ms", durationMs.coerceAtLeast(0L))
                put("updated_at", System.currentTimeMillis())
            },
            SQLiteDatabase.CONFLICT_REPLACE,
        )
    }

    fun getProgress(): List<WatchProgress> = readableDatabase.query(
        "progress",
        null,
        null,
        null,
        null,
        null,
        "updated_at DESC",
    ).use { cursor ->
        buildList {
            while (cursor.moveToNext()) {
                add(
                    WatchProgress(
                        resourceId = cursor.requiredLong("resource_id"),
                        positionMs = cursor.requiredLong("position_ms"),
                        durationMs = cursor.requiredLong("duration_ms"),
                        updatedAt = cursor.requiredLong("updated_at"),
                    ),
                )
            }
        }
    }

    private fun Cursor.toResource() = MediaResource(
        id = requiredLong("id"),
        cloudFileId = requiredString("cloud_file_id"),
        parentId = optionalString("parent_id"),
        name = requiredString("name"),
        size = requiredLong("size"),
        fileType = requiredInt("file_type"),
        isDirectory = requiredInt("is_directory") == 1,
        downloadUrl = optionalString("download_url"),
        importedAt = requiredLong("imported_at"),
        updatedAt = requiredLong("updated_at"),
    )

    private fun Cursor.toMetadata() = MovieMetadata(
        id = requiredLong("id"),
        resourceId = requiredLong("resource_id"),
        contentId = requiredString("content_id"),
        title = requiredString("title"),
        originalTitle = requiredString("original_title"),
        description = optionalString("description"),
        releaseDate = optionalString("release_date"),
        runtimeMinutes = requiredInt("runtime_minutes"),
        director = optionalString("director"),
        maker = optionalString("maker"),
        label = optionalString("label"),
        series = optionalString("series"),
        ratingScore = optionalDouble("rating_score"),
        ratingVotes = optionalInt("rating_votes"),
        posterUrl = optionalString("poster_url"),
        coverUrl = optionalString("cover_url"),
        trailerUrl = optionalString("trailer_url"),
        sourceName = requiredString("source_name"),
        sourceUrl = requiredString("source_url"),
        actresses = decodeActresses(requiredString("actresses")),
        genres = decodeStrings(requiredString("genres")),
        screenshots = decodeStrings(requiredString("screenshots")),
        updatedAt = requiredLong("updated_at"),
    )

    private fun encodeActresses(value: List<ActressInfo>) =
        json.encodeToString(ListSerializer(ActressInfo.serializer()), value)

    private fun decodeActresses(value: String) = runCatching {
        json.decodeFromString(ListSerializer(ActressInfo.serializer()), value)
    }.getOrDefault(emptyList())

    private fun encodeStrings(value: List<String>) =
        json.encodeToString(ListSerializer(String.serializer()), value)

    private fun decodeStrings(value: String) = runCatching {
        json.decodeFromString(ListSerializer(String.serializer()), value)
    }.getOrDefault(emptyList())
}

private fun Cursor.requiredString(name: String): String = getString(column(name))
private fun Cursor.requiredLong(name: String): Long = getLong(column(name))
private fun Cursor.requiredInt(name: String): Int = getInt(column(name))

private fun Cursor.optionalString(name: String): String? {
    val index = column(name)
    return if (isNull(index)) null else getString(index)
}

private fun Cursor.optionalLong(name: String): Long? {
    val index = column(name)
    return if (isNull(index)) null else getLong(index)
}

private fun Cursor.optionalInt(name: String): Int? {
    val index = column(name)
    return if (isNull(index)) null else getInt(index)
}

private fun Cursor.optionalDouble(name: String): Double? {
    val index = column(name)
    return if (isNull(index)) null else getDouble(index)
}

private fun Cursor.column(name: String): Int = getColumnIndexOrThrow(name)
