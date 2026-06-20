/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.db

import android.annotation.SuppressLint
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.util.Log
import androidx.core.content.contentValuesOf
import androidx.room.AutoMigration
import androidx.room.Database
import androidx.room.DeleteColumn
import androidx.room.DeleteTable
import androidx.room.RenameColumn
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.AutoMigrationSpec
import androidx.room.migration.Migration
import androidx.room.withTransaction
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeout
import moe.rukamori.archivetune.db.entities.AlbumArtistMap
import moe.rukamori.archivetune.db.entities.AlbumEntity
import moe.rukamori.archivetune.db.entities.ArtistEntity
import moe.rukamori.archivetune.db.entities.Event
import moe.rukamori.archivetune.db.entities.FormatEntity
import moe.rukamori.archivetune.db.entities.LibraryTopMixEntity
import moe.rukamori.archivetune.db.entities.LibraryTopMixSongMap
import moe.rukamori.archivetune.db.entities.LyricsEntity
import moe.rukamori.archivetune.db.entities.PlayCountEntity
import moe.rukamori.archivetune.db.entities.PlaylistEntity
import moe.rukamori.archivetune.db.entities.PlaylistSongMap
import moe.rukamori.archivetune.db.entities.PlaylistSongMapPreview
import moe.rukamori.archivetune.db.entities.PlaylistTagMap
import moe.rukamori.archivetune.db.entities.RelatedSongMap
import moe.rukamori.archivetune.db.entities.SearchHistory
import moe.rukamori.archivetune.db.entities.SetVideoIdEntity
import moe.rukamori.archivetune.db.entities.SongAlbumMap
import moe.rukamori.archivetune.db.entities.SongArtistMap
import moe.rukamori.archivetune.db.entities.SongEntity
import moe.rukamori.archivetune.db.entities.SortedSongAlbumMap
import moe.rukamori.archivetune.db.entities.SortedSongArtistMap
import moe.rukamori.archivetune.db.entities.TagEntity
import moe.rukamori.archivetune.extensions.toSQLiteQuery
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.util.Date
import java.util.concurrent.Executor
import kotlin.coroutines.resume

private const val TAG = "MusicDatabase"
private const val CURRENT_VERSION = 31

class MusicDatabase(
    private val delegate: InternalDatabase,
) : DatabaseDao by delegate.dao {
    val openHelper: SupportSQLiteOpenHelper
        get() = delegate.openHelper

    fun query(block: MusicDatabase.() -> Unit) =
        with(delegate) {
            queryExecutor.execute {
                block(this@MusicDatabase)
            }
        }

    fun transaction(block: MusicDatabase.() -> Unit) =
        with(delegate) {
            transactionExecutor.execute {
                runInTransaction {
                    block(this@MusicDatabase)
                }
            }
        }

    suspend fun <R> withTransaction(block: suspend MusicDatabase.() -> R): R =
        delegate.withTransaction {
            block(this@MusicDatabase)
        }

    suspend fun awaitIdle(timeoutMs: Long = 5_000L) {
        withTimeout(timeoutMs) {
            awaitExecutor(delegate.queryExecutor)
            awaitExecutor(delegate.transactionExecutor)
        }
    }

    fun close() = delegate.close()

    private suspend fun awaitExecutor(executor: Executor) {
        suspendCancellableCoroutine { cont ->
            executor.execute {
                if (cont.isActive) cont.resume(Unit)
            }
        }
    }
}

@Database(
    entities = [
        SongEntity::class,
        ArtistEntity::class,
        AlbumEntity::class,
        PlaylistEntity::class,
        SongArtistMap::class,
        SongAlbumMap::class,
        AlbumArtistMap::class,
        PlaylistSongMap::class,
        SearchHistory::class,
        FormatEntity::class,
        LyricsEntity::class,
        Event::class,
        RelatedSongMap::class,
        SetVideoIdEntity::class,
        PlayCountEntity::class,
        TagEntity::class,
        PlaylistTagMap::class,
        LibraryTopMixEntity::class,
        LibraryTopMixSongMap::class,
    ],
    views = [
        SortedSongArtistMap::class,
        SortedSongAlbumMap::class,
        PlaylistSongMapPreview::class,
    ],
    version = CURRENT_VERSION,
    exportSchema = true,
    autoMigrations = [
        AutoMigration(from = 2, to = 3),
        AutoMigration(from = 3, to = 4),
        AutoMigration(from = 4, to = 5),
        AutoMigration(from = 5, to = 6, spec = Migration5To6::class),
        AutoMigration(from = 6, to = 7, spec = Migration6To7::class),
        AutoMigration(from = 7, to = 8, spec = Migration7To8::class),
        AutoMigration(from = 8, to = 9),
        AutoMigration(from = 9, to = 10, spec = Migration9To10::class),
        AutoMigration(from = 10, to = 11, spec = Migration10To11::class),
        AutoMigration(from = 11, to = 12, spec = Migration11To12::class),
        AutoMigration(from = 12, to = 13, spec = Migration12To13::class),
        AutoMigration(from = 13, to = 14, spec = Migration13To14::class),
        AutoMigration(from = 14, to = 15),
        AutoMigration(from = 15, to = 16),
        AutoMigration(from = 16, to = 17, spec = Migration16To17::class),
        AutoMigration(from = 17, to = 18),
        AutoMigration(from = 18, to = 19, spec = Migration18To19::class),
        AutoMigration(from = 19, to = 20, spec = Migration19To20::class),
        AutoMigration(from = 20, to = 21, spec = Migration20To21::class),
        AutoMigration(from = 21, to = 22),
    ],
)
@TypeConverters(Converters::class)
abstract class InternalDatabase : RoomDatabase() {
    abstract val dao: DatabaseDao

    companion object {
        const val DB_NAME = "song.db"

        fun newInstance(context: Context): MusicDatabase {
            val universalMigrations =
                (2 until CURRENT_VERSION)
                    .map { from -> UniversalMigration(context, from, CURRENT_VERSION) }
                    .toTypedArray()

            fun build(): InternalDatabase =
                Room
                    .databaseBuilder(context, InternalDatabase::class.java, DB_NAME)
                    .addMigrations(
                        MIGRATION_1_2,
                        *universalMigrations,
                    ).addCallback(DatabaseCallback())
                    .fallbackToDestructiveMigration()
                    .fallbackToDestructiveMigrationOnDowngrade()
                    .setJournalMode(JournalMode.WRITE_AHEAD_LOGGING)
                    .setTransactionExecutor(
                        java.util.concurrent.Executors
                            .newFixedThreadPool(4),
                    ).setQueryExecutor(
                        java.util.concurrent.Executors
                            .newFixedThreadPool(4),
                    ).build()

            fun shouldResetDb(t: Throwable): Boolean {
                val msg = (t.message ?: "").lowercase()
                return msg.contains("room cannot verify the data integrity") ||
                    msg.contains("forgot to update the version number") ||
                    msg.contains("migration didn't properly handle") ||
                    msg.contains("room openhelper verification failed")
            }

            var db = build()
            try {
                db.openHelper.writableDatabase
            } catch (t: Throwable) {
                if (!shouldResetDb(t)) throw t
                Log.e(TAG, "Database open failed, attempting schema repair", t)
                runCatching { db.close() }

                val repaired =
                    runCatching { SchemaTools.repairDatabaseFile(context = context, name = DB_NAME) }
                        .onFailure { Log.e(TAG, "Schema repair failed, recreating database", it) }
                        .isSuccess

                db = build()
                runCatching { db.openHelper.writableDatabase }.getOrElse { openError ->
                    Log.e(TAG, "Database still failed to open after schema repair=$repaired, recreating database", openError)
                    runCatching { db.close() }
                    runCatching { context.deleteDatabase(DB_NAME) }
                    db = build()
                    db.openHelper.writableDatabase
                }
            }

            return MusicDatabase(delegate = db)
        }
    }
}

private class DatabaseCallback : RoomDatabase.Callback() {
    override fun onOpen(db: SupportSQLiteDatabase) {
        super.onOpen(db)
        java.util.concurrent.Executors.newSingleThreadExecutor().execute {
            try {
                db.query("PRAGMA busy_timeout = 60000").close()
                db.query("PRAGMA cache_size = -16000").close()
                db.query("PRAGMA wal_autocheckpoint = 1000").close()
                db.query("PRAGMA synchronous = NORMAL").close()
                db.query("PRAGMA temp_store = MEMORY").close()
                db.query("PRAGMA mmap_size = 268435456").close()

                cleanupDuplicatePlaylistsOnOpen(db)
                ensurePlaylistBrowseIdIndex(db)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to set PRAGMA settings", e)
            }
        }
    }

    private fun cleanupDuplicatePlaylistsOnOpen(db: SupportSQLiteDatabase) {
        try {
            db.execSQL(
                """
                DELETE FROM playlist_song_map WHERE playlistId IN (
                    SELECT p1.id FROM playlist p1
                    WHERE p1.browseId IS NOT NULL
                    AND EXISTS (
                        SELECT 1 FROM playlist p2 
                        WHERE p2.browseId = p1.browseId 
                        AND p2.id != p1.id
                        AND (
                            (SELECT COUNT(*) FROM playlist_song_map WHERE playlistId = p2.id) >
                            (SELECT COUNT(*) FROM playlist_song_map WHERE playlistId = p1.id)
                            OR (
                                (SELECT COUNT(*) FROM playlist_song_map WHERE playlistId = p2.id) =
                                (SELECT COUNT(*) FROM playlist_song_map WHERE playlistId = p1.id)
                                AND p2.rowid < p1.rowid
                            )
                        )
                    )
                )
            """,
            )

            db.execSQL(
                """
                DELETE FROM playlist WHERE id IN (
                    SELECT p1.id FROM playlist p1
                    WHERE p1.browseId IS NOT NULL
                    AND EXISTS (
                        SELECT 1 FROM playlist p2 
                        WHERE p2.browseId = p1.browseId 
                        AND p2.id != p1.id
                        AND (
                            (SELECT COUNT(*) FROM playlist_song_map WHERE playlistId = p2.id) >
                            (SELECT COUNT(*) FROM playlist_song_map WHERE playlistId = p1.id)
                            OR (
                                (SELECT COUNT(*) FROM playlist_song_map WHERE playlistId = p2.id) =
                                (SELECT COUNT(*) FROM playlist_song_map WHERE playlistId = p1.id)
                                AND p2.rowid < p1.rowid
                            )
                        )
                    )
                )
            """,
            )
        } catch (e: Exception) {
            Log.w(TAG, "Duplicate playlist cleanup skipped", e)
        }
    }

    private fun ensurePlaylistBrowseIdIndex(db: SupportSQLiteDatabase) {
        try {
            db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_playlist_browseId ON playlist (browseId) WHERE browseId IS NOT NULL")
        } catch (e: Exception) {
            Log.w(TAG, "Failed to create browseId index", e)
        }
    }
}

// =============================================================================
// UNIVERSAL MIGRATION - Handles schema upgrade to current version
// =============================================================================

/**
 * Universal migration that properly handles schema changes for any source version.
 * Recreates tables with correct schema when needed to fix default value issues.
 */
private class UniversalMigration(
    private val context: Context,
    startVersion: Int,
    endVersion: Int,
) : Migration(startVersion, endVersion) {
    override fun migrate(db: SupportSQLiteDatabase) {
        val from = startVersion
        val to = endVersion
        Log.i(TAG, "Running universal migration from $from to $to")

        val expectedDb = Room.inMemoryDatabaseBuilder(context, InternalDatabase::class.java).build()
        try {
            val expected = expectedDb.openHelper.writableDatabase
            SchemaTools.reconcileDatabase(db = db, expectedDb = expected)
            Log.i(TAG, "Migration completed successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Migration failed", e)
            throw e
        } finally {
            expectedDb.close()
        }
    }
}

private object SchemaTools {
    private val IGNORED_TABLES = setOf("android_metadata", "room_master_table", "sqlite_sequence")

    fun repairDatabaseFile(
        context: Context,
        name: String,
    ) {
        val expectedDb = Room.inMemoryDatabaseBuilder(context, InternalDatabase::class.java).build()
        val fileHelper =
            FrameworkSQLiteOpenHelperFactory()
                .create(
                    SupportSQLiteOpenHelper.Configuration
                        .builder(context)
                        .name(name)
                        .callback(
                            object : SupportSQLiteOpenHelper.Callback(CURRENT_VERSION) {
                                override fun onCreate(db: SupportSQLiteDatabase) = Unit

                                override fun onUpgrade(
                                    db: SupportSQLiteDatabase,
                                    oldVersion: Int,
                                    newVersion: Int,
                                ) = Unit

                                override fun onDowngrade(
                                    db: SupportSQLiteDatabase,
                                    oldVersion: Int,
                                    newVersion: Int,
                                ) = Unit
                            },
                        ).build(),
                )

        try {
            val expected = expectedDb.openHelper.writableDatabase
            val identityHash = readIdentityHash(expected)
            val db = fileHelper.writableDatabase

            reconcileDatabase(db = db, expectedDb = expected)
            if (identityHash != null) {
                updateIdentityHash(db = db, identityHash = identityHash)
            }
        } finally {
            runCatching { fileHelper.close() }
            runCatching { expectedDb.close() }
        }
    }

    fun reconcileDatabase(
        db: SupportSQLiteDatabase,
        expectedDb: SupportSQLiteDatabase,
    ) {
        val expectedMaster = readMasterEntries(expectedDb)
        val expectedTables = expectedMaster.filter { it.type == "table" && it.name !in IGNORED_TABLES }
        val expectedIndices =
            expectedMaster.filter { it.type == "index" && it.sql != null && it.tblName !in IGNORED_TABLES }
        val expectedViews = expectedMaster.filter { it.type == "view" && it.sql != null }
        val expectedTriggers = expectedMaster.filter { it.type == "trigger" && it.sql != null }

        db.execSQL("PRAGMA foreign_keys=OFF")
        dropNonTableObjects(db)

        expectedTables.forEach { table ->
            ensureTableSchema(db = db, expectedDb = expectedDb, table = table, expectedIndices = expectedIndices)
        }

        expectedViews.forEach { db.execSQL(it.sql!!) }
        expectedTriggers.forEach { db.execSQL(it.sql!!) }

        db.execSQL("PRAGMA foreign_keys=ON")
    }

    private fun readIdentityHash(db: SupportSQLiteDatabase): String? =
        runCatching {
            db.query("SELECT identity_hash FROM room_master_table WHERE id = 42").use { cursor ->
                if (!cursor.moveToFirst()) return null
                cursor.getString(0)
            }
        }.getOrNull()

    private fun updateIdentityHash(
        db: SupportSQLiteDatabase,
        identityHash: String,
    ) {
        db.execSQL("CREATE TABLE IF NOT EXISTS room_master_table (id INTEGER PRIMARY KEY,identity_hash TEXT)")
        db.execSQL(
            "INSERT OR REPLACE INTO room_master_table (id, identity_hash) VALUES (42, ?)",
            arrayOf(identityHash),
        )
    }

    private fun ensureTableSchema(
        db: SupportSQLiteDatabase,
        expectedDb: SupportSQLiteDatabase,
        table: MasterEntry,
        expectedIndices: List<MasterEntry>,
    ) {
        val expectedColumns = readColumns(expectedDb, table.name)
        if (expectedColumns.isEmpty()) return

        val existing = tableExists(db, table.name)
        if (!existing) {
            db.execSQL(table.sql!!)
            expectedIndices.filter { it.tblName == table.name }.forEach { db.execSQL(it.sql!!) }
            return
        }

        val actualColumns = readColumns(db, table.name)
        if (!schemaMismatch(expectedColumns, actualColumns)) {
            expectedIndices.filter { it.tblName == table.name }.forEach { db.execSQL(it.sql!!) }
            return
        }

        val oldTable = "_old_${table.name}"
        db.execSQL("ALTER TABLE `${table.name}` RENAME TO `$oldTable`")
        db.execSQL(table.sql!!)

        val expectedOrdered = expectedColumns.values.sortedBy { it.cid }
        val insertColumns = expectedOrdered.joinToString(",") { "`${it.name}`" }
        val selectExpr =
            expectedOrdered.joinToString(",") { col ->
                val old = actualColumns[col.name]
                when {
                    old != null -> "`${col.name}`"
                    col.defaultValue != null -> col.defaultValue
                    col.notNull -> defaultLiteral(col.type)
                    else -> "NULL"
                }
            }

        db.execSQL("INSERT INTO `${table.name}` ($insertColumns) SELECT $selectExpr FROM `$oldTable`")
        db.execSQL("DROP TABLE `$oldTable`")
        expectedIndices.filter { it.tblName == table.name }.forEach { db.execSQL(it.sql!!) }

        if (table.sql
                .orEmpty()
                .uppercase()
                .contains("AUTOINCREMENT")
        ) {
            val idColumn = expectedColumns.values.firstOrNull { it.name.equals("id", ignoreCase = true) }?.name ?: "id"
            runCatching {
                db.execSQL("DELETE FROM sqlite_sequence WHERE name = ?", arrayOf(table.name))
                db.execSQL(
                    "INSERT INTO sqlite_sequence(name, seq) SELECT ?, IFNULL(MAX(`$idColumn`), 0) FROM `${table.name}`",
                    arrayOf(table.name),
                )
            }
        }
    }

    private fun defaultLiteral(type: String?): String {
        val t = normalizeType(type)
        return when {
            t.contains("INT") -> "0"
            t.contains("CHAR") || t.contains("CLOB") || t.contains("TEXT") -> "''"
            t.contains("BLOB") -> "X''"
            t.contains("REAL") || t.contains("FLOA") || t.contains("DOUB") -> "0.0"
            else -> "NULL"
        }
    }

    private fun dropNonTableObjects(db: SupportSQLiteDatabase) {
        db.query("SELECT type, name FROM sqlite_master WHERE sql IS NOT NULL").use { cursor ->
            val typeIdx = cursor.getColumnIndex("type")
            val nameIdx = cursor.getColumnIndex("name")
            while (cursor.moveToNext()) {
                val type = cursor.getString(typeIdx)
                val name = cursor.getString(nameIdx)
                if (type == "view") db.execSQL("DROP VIEW IF EXISTS `$name`")
                if (type == "trigger") db.execSQL("DROP TRIGGER IF EXISTS `$name`")
                if (type == "index") db.execSQL("DROP INDEX IF EXISTS `$name`")
            }
        }
    }

    private fun schemaMismatch(
        expected: Map<String, ColumnInfo>,
        actual: Map<String, ColumnInfo>,
    ): Boolean {
        if (expected.keys != actual.keys) return true
        expected.forEach { (name, e) ->
            val a = actual[name] ?: return true
            if (normalizeType(e.type) != normalizeType(a.type)) return true
            if (e.notNull != a.notNull) return true
            val ed = e.defaultValue?.trim()
            val ad = a.defaultValue?.trim()
            if (ed != ad) return true
        }
        return false
    }

    private fun normalizeType(type: String?): String = (type ?: "").trim().uppercase().substringBefore(' ')

    private fun tableExists(
        db: SupportSQLiteDatabase,
        name: String,
    ): Boolean = db.query("SELECT 1 FROM sqlite_master WHERE type='table' AND name=?", arrayOf(name)).use { it.moveToFirst() }

    private fun readColumns(
        db: SupportSQLiteDatabase,
        table: String,
    ): Map<String, ColumnInfo> {
        val cols = linkedMapOf<String, ColumnInfo>()
        db.query("PRAGMA table_info(`$table`)").use { cursor ->
            val cidIdx = cursor.getColumnIndex("cid")
            val nameIdx = cursor.getColumnIndex("name")
            val typeIdx = cursor.getColumnIndex("type")
            val notNullIdx = cursor.getColumnIndex("notnull")
            val defaultIdx = cursor.getColumnIndex("dflt_value")
            while (cursor.moveToNext()) {
                val name = cursor.getString(nameIdx)
                cols[name] =
                    ColumnInfo(
                        cid = cursor.getInt(cidIdx),
                        name = name,
                        type = cursor.getString(typeIdx),
                        notNull = cursor.getInt(notNullIdx) == 1,
                        defaultValue = if (cursor.isNull(defaultIdx)) null else cursor.getString(defaultIdx),
                    )
            }
        }
        return cols
    }

    private fun readMasterEntries(db: SupportSQLiteDatabase): List<MasterEntry> {
        val items = mutableListOf<MasterEntry>()
        db.query("SELECT type, name, tbl_name, sql FROM sqlite_master WHERE sql IS NOT NULL").use { cursor ->
            val typeIdx = cursor.getColumnIndex("type")
            val nameIdx = cursor.getColumnIndex("name")
            val tblIdx = cursor.getColumnIndex("tbl_name")
            val sqlIdx = cursor.getColumnIndex("sql")
            while (cursor.moveToNext()) {
                items.add(
                    MasterEntry(
                        type = cursor.getString(typeIdx),
                        name = cursor.getString(nameIdx),
                        tblName = cursor.getString(tblIdx),
                        sql = cursor.getString(sqlIdx),
                    ),
                )
            }
        }
        return items
    }

    private data class MasterEntry(
        val type: String,
        val name: String,
        val tblName: String,
        val sql: String?,
    )

    private data class ColumnInfo(
        val cid: Int,
        val name: String,
        val type: String?,
        val notNull: Boolean,
        val defaultValue: String?,
    )
}

// =============================================================================
// LEGACY MIGRATION v1 -> v2 (Major schema rewrite, must be kept)
// =============================================================================

private val MIGRATION_1_2 =
    object : Migration(1, 2) {
        override fun migrate(db: SupportSQLiteDatabase) {
            data class OldSong(
                val id: String,
                val title: String,
                val duration: Int,
                val liked: Boolean,
                val totalPlayTime: Long,
                val downloadState: Int,
                val createDate: LocalDateTime,
                val modifyDate: LocalDateTime,
            )

            val converters = Converters()
            val artistMap = mutableMapOf<Int, String>()
            val artists = mutableListOf<ArtistEntity>()

            db.query("SELECT * FROM artist".toSQLiteQuery()).use { cursor ->
                while (cursor.moveToNext()) {
                    val oldId = cursor.getInt(0)
                    val newId = ArtistEntity.generateArtistId()
                    artistMap[oldId] = newId
                    artists.add(ArtistEntity(id = newId, name = cursor.getString(1)))
                }
            }

            val playlistMap = mutableMapOf<Int, String>()
            val playlists = mutableListOf<PlaylistEntity>()
            db.query("SELECT * FROM playlist".toSQLiteQuery()).use { cursor ->
                while (cursor.moveToNext()) {
                    val oldId = cursor.getInt(0)
                    val newId = PlaylistEntity.generatePlaylistId()
                    playlistMap[oldId] = newId
                    playlists.add(PlaylistEntity(id = newId, name = cursor.getString(1)))
                }
            }

            val playlistSongMaps = mutableListOf<PlaylistSongMap>()
            db.query("SELECT * FROM playlist_song".toSQLiteQuery()).use { cursor ->
                while (cursor.moveToNext()) {
                    playlistSongMaps.add(
                        PlaylistSongMap(
                            playlistId = playlistMap[cursor.getInt(1)]!!,
                            songId = cursor.getString(2),
                            position = cursor.getInt(3),
                        ),
                    )
                }
            }
            playlistSongMaps.sortBy { it.position }

            val songs = mutableListOf<OldSong>()
            val songArtistMaps = mutableListOf<SongArtistMap>()
            db.query("SELECT * FROM song".toSQLiteQuery()).use { cursor ->
                while (cursor.moveToNext()) {
                    val songId = cursor.getString(0)
                    songs.add(
                        OldSong(
                            id = songId,
                            title = cursor.getString(1),
                            duration = cursor.getInt(3),
                            liked = cursor.getInt(4) == 1,
                            totalPlayTime = 0,
                            downloadState = 0,
                            createDate = Instant.ofEpochMilli(Date(cursor.getLong(8)).time).atZone(ZoneOffset.UTC).toLocalDateTime(),
                            modifyDate = Instant.ofEpochMilli(Date(cursor.getLong(9)).time).atZone(ZoneOffset.UTC).toLocalDateTime(),
                        ),
                    )
                    songArtistMaps.add(SongArtistMap(songId = songId, artistId = artistMap[cursor.getInt(2)]!!, position = 0))
                }
            }

            // Drop old tables and create new schema
            db.execSQL("DROP TABLE IF EXISTS song")
            db.execSQL("DROP TABLE IF EXISTS artist")
            db.execSQL("DROP TABLE IF EXISTS playlist")
            db.execSQL("DROP TABLE IF EXISTS playlist_song")

            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `song` (`id` TEXT NOT NULL, `title` TEXT NOT NULL, `duration` INTEGER NOT NULL, `thumbnailUrl` TEXT, `albumId` TEXT, `albumName` TEXT, `liked` INTEGER NOT NULL, `totalPlayTime` INTEGER NOT NULL, `isTrash` INTEGER NOT NULL, `download_state` INTEGER NOT NULL, `create_date` INTEGER NOT NULL, `modify_date` INTEGER NOT NULL, PRIMARY KEY(`id`))",
            )
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `artist` (`id` TEXT NOT NULL, `name` TEXT NOT NULL, `thumbnailUrl` TEXT, `bannerUrl` TEXT, `description` TEXT, `createDate` INTEGER NOT NULL, `lastUpdateTime` INTEGER NOT NULL, PRIMARY KEY(`id`))",
            )
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `album` (`id` TEXT NOT NULL, `title` TEXT NOT NULL, `year` INTEGER, `thumbnailUrl` TEXT, `songCount` INTEGER NOT NULL, `duration` INTEGER NOT NULL, `createDate` INTEGER NOT NULL, `lastUpdateTime` INTEGER NOT NULL, PRIMARY KEY(`id`))",
            )
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `playlist` (`id` TEXT NOT NULL, `name` TEXT NOT NULL, `author` TEXT, `authorId` TEXT, `year` INTEGER, `thumbnailUrl` TEXT, `createDate` INTEGER NOT NULL, `lastUpdateTime` INTEGER NOT NULL, PRIMARY KEY(`id`))",
            )
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `song_artist_map` (`songId` TEXT NOT NULL, `artistId` TEXT NOT NULL, `position` INTEGER NOT NULL, PRIMARY KEY(`songId`, `artistId`), FOREIGN KEY(`songId`) REFERENCES `song`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE, FOREIGN KEY(`artistId`) REFERENCES `artist`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE)",
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_song_artist_map_songId` ON `song_artist_map` (`songId`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_song_artist_map_artistId` ON `song_artist_map` (`artistId`)")
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `song_album_map` (`songId` TEXT NOT NULL, `albumId` TEXT NOT NULL, `index` INTEGER, PRIMARY KEY(`songId`, `albumId`), FOREIGN KEY(`songId`) REFERENCES `song`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE, FOREIGN KEY(`albumId`) REFERENCES `album`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE)",
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_song_album_map_songId` ON `song_album_map` (`songId`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_song_album_map_albumId` ON `song_album_map` (`albumId`)")
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `album_artist_map` (`albumId` TEXT NOT NULL, `artistId` TEXT NOT NULL, `order` INTEGER NOT NULL, PRIMARY KEY(`albumId`, `artistId`), FOREIGN KEY(`albumId`) REFERENCES `album`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE, FOREIGN KEY(`artistId`) REFERENCES `artist`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE)",
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_album_artist_map_albumId` ON `album_artist_map` (`albumId`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_album_artist_map_artistId` ON `album_artist_map` (`artistId`)")
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `playlist_song_map` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `playlistId` TEXT NOT NULL, `songId` TEXT NOT NULL, `position` INTEGER NOT NULL, FOREIGN KEY(`playlistId`) REFERENCES `playlist`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE, FOREIGN KEY(`songId`) REFERENCES `song`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE)",
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_playlist_song_map_playlistId` ON `playlist_song_map` (`playlistId`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_playlist_song_map_songId` ON `playlist_song_map` (`songId`)")
            db.execSQL("CREATE TABLE IF NOT EXISTS `download` (`id` INTEGER NOT NULL, `songId` TEXT NOT NULL, PRIMARY KEY(`id`))")
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `search_history` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `query` TEXT NOT NULL)",
            )
            db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_search_history_query` ON `search_history` (`query`)")
            db.execSQL("CREATE VIEW `sorted_song_artist_map` AS SELECT * FROM song_artist_map ORDER BY position")
            db.execSQL("CREATE VIEW `playlist_song_map_preview` AS SELECT * FROM playlist_song_map WHERE position <= 3 ORDER BY position")

            // Insert data
            artists.forEach {
                db.insert(
                    "artist",
                    SQLiteDatabase.CONFLICT_ABORT,
                    contentValuesOf(
                        "id" to it.id,
                        "name" to it.name,
                        "createDate" to converters.dateToTimestamp(it.lastUpdateTime),
                        "lastUpdateTime" to converters.dateToTimestamp(it.lastUpdateTime),
                    ),
                )
            }

            songs.forEach {
                db.insert(
                    "song",
                    SQLiteDatabase.CONFLICT_ABORT,
                    contentValuesOf(
                        "id" to it.id,
                        "title" to it.title,
                        "duration" to it.duration,
                        "liked" to it.liked,
                        "totalPlayTime" to it.totalPlayTime,
                        "isTrash" to false,
                        "download_state" to it.downloadState,
                        "create_date" to converters.dateToTimestamp(it.createDate),
                        "modify_date" to converters.dateToTimestamp(it.modifyDate),
                    ),
                )
            }

            songArtistMaps.forEach {
                db.insert(
                    "song_artist_map",
                    SQLiteDatabase.CONFLICT_ABORT,
                    contentValuesOf("songId" to it.songId, "artistId" to it.artistId, "position" to it.position),
                )
            }

            playlists.forEach {
                db.insert(
                    "playlist",
                    SQLiteDatabase.CONFLICT_ABORT,
                    contentValuesOf(
                        "id" to it.id,
                        "name" to it.name,
                        "createDate" to converters.dateToTimestamp(LocalDateTime.now()),
                        "lastUpdateTime" to converters.dateToTimestamp(LocalDateTime.now()),
                    ),
                )
            }

            playlistSongMaps.forEach {
                db.insert(
                    "playlist_song_map",
                    SQLiteDatabase.CONFLICT_ABORT,
                    contentValuesOf("playlistId" to it.playlistId, "songId" to it.songId, "position" to it.position),
                )
            }
        }
    }

// =============================================================================
// AUTO MIGRATION SPECS (Required by Room's AutoMigration annotations)
// =============================================================================

@DeleteColumn.Entries(
    DeleteColumn(tableName = "song", columnName = "isTrash"),
    DeleteColumn(tableName = "playlist", columnName = "author"),
    DeleteColumn(tableName = "playlist", columnName = "authorId"),
    DeleteColumn(tableName = "playlist", columnName = "year"),
    DeleteColumn(tableName = "playlist", columnName = "thumbnailUrl"),
    DeleteColumn(tableName = "playlist", columnName = "createDate"),
    DeleteColumn(tableName = "playlist", columnName = "lastUpdateTime"),
)
@RenameColumn.Entries(
    RenameColumn(tableName = "song", fromColumnName = "download_state", toColumnName = "downloadState"),
    RenameColumn(tableName = "song", fromColumnName = "create_date", toColumnName = "createDate"),
    RenameColumn(tableName = "song", fromColumnName = "modify_date", toColumnName = "modifyDate"),
)
class Migration5To6 : AutoMigrationSpec {
    override fun onPostMigrate(db: SupportSQLiteDatabase) {
        db.query("SELECT id FROM playlist WHERE id NOT LIKE 'LP%'").use { cursor ->
            while (cursor.moveToNext()) {
                db.execSQL("UPDATE playlist SET browseId = '${cursor.getString(0)}' WHERE id = '${cursor.getString(0)}'")
            }
        }
    }
}

class Migration6To7 : AutoMigrationSpec {
    override fun onPostMigrate(db: SupportSQLiteDatabase) {
        db.query("SELECT id, createDate FROM song").use { cursor ->
            while (cursor.moveToNext()) {
                db.execSQL("UPDATE song SET inLibrary = ${cursor.getLong(1)} WHERE id = '${cursor.getString(0)}'")
            }
        }
    }
}

@DeleteColumn.Entries(
    DeleteColumn(tableName = "song", columnName = "createDate"),
    DeleteColumn(tableName = "song", columnName = "modifyDate"),
)
class Migration7To8 : AutoMigrationSpec

@DeleteTable.Entries(DeleteTable(tableName = "download"))
class Migration9To10 : AutoMigrationSpec

@DeleteColumn.Entries(
    DeleteColumn(tableName = "song", columnName = "downloadState"),
    DeleteColumn(tableName = "artist", columnName = "bannerUrl"),
    DeleteColumn(tableName = "artist", columnName = "description"),
    DeleteColumn(tableName = "artist", columnName = "createDate"),
)
class Migration10To11 : AutoMigrationSpec

@DeleteColumn.Entries(DeleteColumn(tableName = "album", columnName = "createDate"))
class Migration11To12 : AutoMigrationSpec {
    override fun onPostMigrate(db: SupportSQLiteDatabase) {
        db.execSQL("UPDATE album SET bookmarkedAt = lastUpdateTime")
        db.query("SELECT DISTINCT albumId, albumName FROM song").use { cursor ->
            while (cursor.moveToNext()) {
                val albumId = cursor.getString(0)
                val albumName = cursor.getString(1)
                db.insert(
                    "album",
                    SQLiteDatabase.CONFLICT_IGNORE,
                    contentValuesOf("id" to albumId, "title" to albumName, "songCount" to 0, "duration" to 0, "lastUpdateTime" to 0),
                )
            }
        }
        db.query("CREATE INDEX IF NOT EXISTS `index_song_albumId` ON `song` (`albumId`)")
    }
}

class Migration12To13 : AutoMigrationSpec

class Migration13To14 : AutoMigrationSpec {
    @SuppressLint("Range")
    override fun onPostMigrate(db: SupportSQLiteDatabase) {
        val now = Converters().dateToTimestamp(LocalDateTime.now())
        db.execSQL("UPDATE playlist SET createdAt = '$now'")
        db.execSQL("UPDATE playlist SET lastUpdateTime = '$now'")
    }
}

@DeleteColumn.Entries(
    DeleteColumn(tableName = "song", columnName = "isLocal"),
    DeleteColumn(tableName = "song", columnName = "localPath"),
    DeleteColumn(tableName = "artist", columnName = "isLocal"),
    DeleteColumn(tableName = "playlist", columnName = "isLocal"),
)
class Migration16To17 : AutoMigrationSpec {
    override fun onPostMigrate(db: SupportSQLiteDatabase) {
        db.execSQL("UPDATE playlist SET bookmarkedAt = lastUpdateTime")
        db.execSQL("UPDATE playlist SET isEditable = 1 WHERE browseId IS NOT NULL")
    }
}

class Migration18To19 : AutoMigrationSpec {
    override fun onPostMigrate(db: SupportSQLiteDatabase) {
        db.execSQL("UPDATE song SET explicit = 0 WHERE explicit IS NULL")
    }
}

class Migration19To20 : AutoMigrationSpec {
    override fun onPostMigrate(db: SupportSQLiteDatabase) {
        db.execSQL("UPDATE song SET explicit = 0 WHERE explicit IS NULL")
    }
}

@DeleteColumn.Entries(DeleteColumn(tableName = "song", columnName = "artistName"))
class Migration20To21 : AutoMigrationSpec
