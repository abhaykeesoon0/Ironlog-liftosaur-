package com.ironlog.app.core.backup

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * A folder the user chose in a cloud storage app, written to through the
 * Storage Access Framework.
 *
 * This is how the sealed weekly backup reaches a server without the app
 * making a single network call of its own. The user picks a folder once, in
 * Google Drive, OneDrive, Dropbox or any other provider installed on the
 * phone, and Android hands back a permission that survives restarts. The
 * app writes the file into that folder; the provider's own app syncs it.
 * Only the sealed file ever leaves, so the provider holds ciphertext it
 * cannot open, and the passphrase never leaves the phone.
 *
 * No SDK, no account with the app, no keys to keep: the platform does the
 * plumbing, which is also why the offline design of the app holds.
 */
@Singleton
class CloudFolder @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val resolver get() = context.contentResolver

    /** Keeps the grant across restarts. Called once, when the folder is chosen. */
    fun take(treeUri: Uri) {
        resolver.takePersistableUriPermission(
            treeUri,
            Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        )
    }

    fun release(treeUri: Uri) {
        runCatching {
            resolver.releasePersistableUriPermission(
                treeUri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            )
        }
    }

    /** Whether the grant still stands; the user can revoke it from the provider's side. */
    fun isGranted(treeUri: Uri): Boolean =
        resolver.persistedUriPermissions.any { it.uri == treeUri && it.isReadPermission && it.isWritePermission }

    /** The folder's own name, for the settings row. */
    fun displayName(treeUri: Uri): String? {
        val doc = DocumentsContract.buildDocumentUriUsingTree(treeUri, DocumentsContract.getTreeDocumentId(treeUri))
        return resolver.query(doc, arrayOf(DocumentsContract.Document.COLUMN_DISPLAY_NAME), null, null, null)
            ?.use { c -> if (c.moveToFirst()) c.getString(0) else null }
    }

    /** Creates [name] in the folder and writes [bytes] into it. */
    fun write(treeUri: Uri, name: String, bytes: ByteArray): Uri {
        val parent = DocumentsContract.buildDocumentUriUsingTree(treeUri, DocumentsContract.getTreeDocumentId(treeUri))
        val file = DocumentsContract.createDocument(resolver, parent, MIME, name)
            ?: throw IllegalStateException("The folder refused a new file")
        resolver.openOutputStream(file, "w")?.use { it.write(bytes) }
            ?: throw IllegalStateException("The folder could not be written to")
        return file
    }

    /** Our backups in the folder, newest first. Other files there are never touched. */
    fun listBackups(treeUri: Uri): List<CloudFile> {
        val children = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, DocumentsContract.getTreeDocumentId(treeUri))
        val columns = arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_LAST_MODIFIED
        )
        val found = mutableListOf<CloudFile>()
        resolver.query(children, columns, null, null, null)?.use { c ->
            while (c.moveToNext()) {
                val name = c.getString(1) ?: continue
                if (!name.startsWith(PREFIX) || !name.endsWith(SUFFIX)) continue
                found += CloudFile(
                    uri = DocumentsContract.buildDocumentUriUsingTree(treeUri, c.getString(0)),
                    name = name,
                    modifiedAt = c.getLong(2)
                )
            }
        }
        // The name carries the timestamp the file was made, which is steadier
        // than a modified time a sync client may rewrite.
        return found.sortedByDescending { it.name }
    }

    fun delete(uri: Uri) {
        runCatching { DocumentsContract.deleteDocument(resolver, uri) }
    }

    data class CloudFile(val uri: Uri, val name: String, val modifiedAt: Long)

    companion object {
        const val PREFIX = "liftosaur_auto_"
        const val SUFFIX = ".liftosaur"
        private const val MIME = "application/octet-stream"
    }
}
