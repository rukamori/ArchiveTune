/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.backup

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.db.InternalDatabase
import java.io.File
import java.io.FileOutputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption.ATOMIC_MOVE
import java.nio.file.StandardCopyOption.REPLACE_EXISTING

internal object PendingDatabaseRestore {
    private const val PENDING_DATABASE_NAME = "${InternalDatabase.DB_NAME}.restore"

    fun prepare(context: Context, directory: File, currentVersion: Int) {
        val databaseFile = directory.resolve(InternalDatabase.DB_NAME)
        check(databaseFile.isFile) { context.getString(R.string.restore_missing_content) }
        SQLiteDatabase.openDatabase(
            databaseFile.absolutePath,
            null,
            SQLiteDatabase.OPEN_READWRITE or SQLiteDatabase.NO_LOCALIZED_COLLATORS,
        ).use { database ->
            check(database.version in 1..currentVersion) { context.getString(R.string.restore_invalid_file) }
            database.rawQuery("PRAGMA quick_check", null).use { cursor ->
                check(cursor.moveToFirst() && cursor.getString(0) == "ok" && !cursor.moveToNext()) {
                    context.getString(R.string.restore_corrupted)
                }
            }
            database.rawQuery("PRAGMA journal_mode = DELETE", null).use { cursor ->
                check(cursor.moveToFirst() && cursor.getString(0).equals("delete", ignoreCase = true)) {
                    context.getString(R.string.restore_failed)
                }
            }
        }
        FileOutputStream(databaseFile, true).use { it.fd.sync() }
    }

    fun commit(context: Context, directory: File) {
        Files.move(
            directory.resolve(InternalDatabase.DB_NAME).toPath(),
            context.getDatabasePath(PENDING_DATABASE_NAME).toPath(),
            ATOMIC_MOVE,
            REPLACE_EXISTING,
        )
    }

    fun install(context: Context) {
        val pendingFile = context.getDatabasePath(PENDING_DATABASE_NAME)
        if (!pendingFile.exists()) return

        listOf("-wal", "-shm", "-journal").forEach { suffix ->
            val sidecar = context.getDatabasePath("${InternalDatabase.DB_NAME}$suffix")
            check(!sidecar.exists() || sidecar.delete()) { "Failed to clear database sidecar" }
        }
        Files.move(
            pendingFile.toPath(),
            context.getDatabasePath(InternalDatabase.DB_NAME).toPath(),
            ATOMIC_MOVE,
            REPLACE_EXISTING,
        )
    }
}
