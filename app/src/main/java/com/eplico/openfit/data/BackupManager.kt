package com.eplico.openfit.data

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import androidx.core.content.FileProvider
import com.eplico.openfit.core.backup.BackupSpreadsheet
import com.eplico.openfit.core.backup.ParsedBackup
import com.eplico.openfit.core.backup.Xlsx
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.time.LocalDate

/** What an import changed. */
data class ImportSummary(
    val setsAdded: Int,
    val daysWithNewSets: Int,
    val exercisesAdded: Int,
    val presetsAdded: Int,
    /** Presets whose name already existed in the app; they are left untouched. */
    val presetsSkipped: Int,
    /** Exercises on a day that already had sets logged in the app; their spreadsheet sets were skipped. */
    val exerciseDaysSkipped: Int,
    val categoriesAdded: Int = 0,
)

/** A spreadsheet that has been read but not yet merged in, waiting on the user's category choice. */
data class PreparedImport(
    val parsed: ParsedBackup,
    /** Categories in the file that the app doesn't have yet. */
    val unknownCategories: List<String>,
)

data class ImportResult(
    val summary: ImportSummary,
    /** One line per spreadsheet row that couldn't be read. */
    val warnings: List<String>,
)

/** Moves data between the database and .xlsx files chosen through the system file picker. */
class BackupManager(
    private val context: Context,
    private val repository: WorkoutRepository,
) {
    fun suggestedFileName(today: LocalDate = LocalDate.now()): String = "OpenFit-$today.xlsx"

    /** Writes everything to [uri] (from a "create document" picker). Returns the number of sets written. */
    suspend fun exportTo(uri: Uri): Int = withContext(Dispatchers.IO) {
        val backup = repository.exportBackup()
        val out = context.contentResolver.openOutputStream(uri) ?: throw IOException("Couldn't open the file for writing")
        out.use { BackupSpreadsheet.write(backup, it) }
        backup.setCount
    }

    /** Writes everything to a private cache file and returns a content:// URI other apps can read. */
    suspend fun exportForSharing(): Uri = withContext(Dispatchers.IO) {
        val dir = File(context.cacheDir, SHARE_DIR).apply { mkdirs() }
        dir.listFiles()?.forEach { it.delete() }
        val file = File(dir, suggestedFileName())
        val backup = repository.exportBackup()
        file.outputStream().use { BackupSpreadsheet.write(backup, it) }
        FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    }

    /** Reads a spreadsheet from [uri] (from an "open document" picker) without changing anything yet. */
    suspend fun prepareImport(uri: Uri): PreparedImport = withContext(Dispatchers.IO) {
        val defaultUnit = repository.defaultUnit()
        val parsed = openSpreadsheet(uri).use { BackupSpreadsheet.read(it, defaultUnit) }
        PreparedImport(parsed, repository.unknownCategories(parsed.backup))
    }

    /** Merges a prepared spreadsheet into the database. */
    suspend fun commitImport(prepared: PreparedImport, addUnknownCategories: Boolean): ImportResult =
        withContext(Dispatchers.IO) {
            ImportResult(repository.importBackup(prepared.parsed.backup, addUnknownCategories), prepared.parsed.warnings)
        }

    /** Reads and merges in one go, adding any unknown categories. */
    suspend fun importFrom(uri: Uri): ImportResult = commitImport(prepareImport(uri), addUnknownCategories = true)

    private fun openSpreadsheet(uri: Uri): InputStream {
        val resolver = context.contentResolver
        // A Google Sheets document in Drive is "virtual": it has no file of its own, but Drive can
        // convert it to .xlsx on request.
        if (isVirtualDocument(uri)) {
            val type = resolver.getStreamTypes(uri, Xlsx.MIME_TYPE)?.firstOrNull()
                ?: throw IOException("This document can't be converted to a spreadsheet file")
            return resolver.openTypedAssetFileDescriptor(uri, type, null)?.createInputStream()
                ?: throw IOException("Couldn't open the file")
        }
        return resolver.openInputStream(uri) ?: throw IOException("Couldn't open the file")
    }

    private fun isVirtualDocument(uri: Uri): Boolean {
        if (!DocumentsContract.isDocumentUri(context, uri)) return false
        return runCatching {
            context.contentResolver.query(uri, arrayOf(DocumentsContract.Document.COLUMN_FLAGS), null, null, null)?.use { cursor ->
                cursor.moveToFirst() && (cursor.getInt(0) and DocumentsContract.Document.FLAG_VIRTUAL_DOCUMENT) != 0
            } ?: false
        }.getOrDefault(false)
    }

    companion object {
        /** Types offered in the import picker: .xlsx files, plus Google Sheets documents in Drive. */
        val IMPORT_MIME_TYPES = arrayOf(Xlsx.MIME_TYPE, "application/vnd.google-apps.spreadsheet", "application/octet-stream")

        /** Must match the path in res/xml/file_paths.xml. */
        private const val SHARE_DIR = "exports"
    }
}
