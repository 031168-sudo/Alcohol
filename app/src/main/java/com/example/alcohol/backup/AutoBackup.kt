package com.example.alcohol.backup

import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.example.alcohol.data.AppDatabase
import com.example.alcohol.data.Backup
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Папка для автосохранения и время последней удачной записи. */
object AutoBackupSettings {
    private const val PREFS = "auto_backup"
    private const val KEY_TREE = "tree_uri"
    private const val KEY_LAST = "last_saved_at"

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun treeUri(context: Context): Uri? =
        prefs(context).getString(KEY_TREE, null)?.let { Uri.parse(it) }

    fun isEnabled(context: Context): Boolean = treeUri(context) != null

    /** Запоминает папку и постоянный доступ к ней. false, если система доступ не дала. */
    fun enable(context: Context, uri: Uri): Boolean = try {
        context.contentResolver.takePersistableUriPermission(
            uri,
            Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
        )
        prefs(context).edit().putString(KEY_TREE, uri.toString()).apply()
        true
    } catch (_: SecurityException) {
        false
    }

    fun disable(context: Context) {
        val uri = treeUri(context) ?: return
        runCatching {
            context.contentResolver.releasePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
            )
        }
        prefs(context).edit().remove(KEY_TREE).apply()
        cancelAutoBackup(context)
    }

    fun lastSavedAt(context: Context): Long = prefs(context).getLong(KEY_LAST, 0L)

    fun markSaved(context: Context, at: Long) {
        prefs(context).edit().putLong(KEY_LAST, at).apply()
    }
}

/**
 * Автосохранение: раз в сутки перезаписывает один и тот же файл с копией
 * в папке, которую выбрал пользователь. Папка может быть и в облаке.
 */
object AutoBackup {
    const val FILE_NAME = "moi-napitki-autobackup.json"

    suspend fun save(context: Context, treeUri: Uri) {
        val json = Backup.export(AppDatabase.get(context))
        withContext(Dispatchers.IO) { write(context, treeUri, json) }
        AutoBackupSettings.markSaved(context, System.currentTimeMillis())
    }

    /** Название выбранной папки для экрана. */
    fun folderName(context: Context, treeUri: Uri): String = try {
        val doc = DocumentsContract.buildDocumentUriUsingTree(
            treeUri,
            DocumentsContract.getTreeDocumentId(treeUri),
        )
        context.contentResolver.query(
            doc,
            arrayOf(DocumentsContract.Document.COLUMN_DISPLAY_NAME),
            null,
            null,
            null,
        )?.use { cursor -> if (cursor.moveToFirst()) cursor.getString(0) else null }
            ?: treeUri.lastPathSegment.orEmpty()
    } catch (_: Exception) {
        treeUri.lastPathSegment.orEmpty()
    }

    private fun write(context: Context, treeUri: Uri, json: String) {
        val resolver = context.contentResolver
        val bytes = json.toByteArray(Charsets.UTF_8)
        val existing = findChild(context, treeUri, FILE_NAME)
        if (existing != null && tryOverwrite(resolver, existing, bytes)) return
        // Провайдер не умеет перезаписывать: убираем старый файл и создаём заново.
        if (existing != null) runCatching { resolver.delete(existing, null, null) }
        val parent = DocumentsContract.buildDocumentUriUsingTree(
            treeUri,
            DocumentsContract.getTreeDocumentId(treeUri),
        )
        val created = DocumentsContract.createDocument(resolver, parent, "application/json", FILE_NAME)
            ?: throw IllegalStateException("Папка больше не доступна")
        val out = resolver.openOutputStream(created, "wt")
            ?: throw IllegalStateException("Не удалось записать файл")
        out.use { it.write(bytes) }
    }

    private fun tryOverwrite(resolver: ContentResolver, uri: Uri, bytes: ByteArray): Boolean = try {
        val out = resolver.openOutputStream(uri, "wt")
        if (out == null) {
            false
        } else {
            out.use { it.write(bytes) }
            true
        }
    } catch (_: Exception) {
        false
    }

    private fun findChild(context: Context, treeUri: Uri, name: String): Uri? {
        val children = DocumentsContract.buildChildDocumentsUriUsingTree(
            treeUri,
            DocumentsContract.getTreeDocumentId(treeUri),
        )
        return try {
            context.contentResolver.query(
                children,
                arrayOf(
                    DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                    DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                ),
                null,
                null,
                null,
            )?.use { cursor ->
                var found: Uri? = null
                while (cursor.moveToNext()) {
                    if (cursor.getString(1) == name) {
                        found = DocumentsContract.buildDocumentUriUsingTree(treeUri, cursor.getString(0))
                        break
                    }
                }
                found
            }
        } catch (_: Exception) {
            null
        }
    }
}

private const val WORK_NAME = "auto-backup"

/** Включает ежедневную запись копии. Первая запись — сразу при включении, дальше раз в сутки. */
fun scheduleAutoBackup(context: Context) {
    val request = PeriodicWorkRequestBuilder<AutoBackupWorker>(1, TimeUnit.DAYS)
        // Первую копию сразу пишем сами в момент включения, фоновая — через сутки.
        .setInitialDelay(1, TimeUnit.DAYS)
        .build()
    WorkManager.getInstance(context)
        .enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.KEEP, request)
}

private fun cancelAutoBackup(context: Context) {
    WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
}

/** Фоновая запись копии: если папка пропала, следующий раз попробует снова. */
class AutoBackupWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val uri = AutoBackupSettings.treeUri(applicationContext) ?: return Result.success()
        return try {
            AutoBackup.save(applicationContext, uri)
            Result.success()
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            if (runAttemptCount < 1) Result.retry() else Result.success()
        }
    }
}
