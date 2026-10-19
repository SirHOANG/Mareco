package com.mareco.app.share

import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.OpenableColumns
import java.io.File
import java.io.FileNotFoundException

/** Lets other apps read a .mareco file that the user chose to share (read-only, one file at a time). */
class MacroShareProvider : ContentProvider() {

    override fun onCreate(): Boolean = true

    override fun query(
        uri: Uri, projection: Array<out String>?, selection: String?,
        selectionArgs: Array<out String>?, sortOrder: String?
    ): Cursor? {
        val f = resolve(uri) ?: return null
        val cols: Array<String> =
            if (projection != null) Array(projection.size) { i -> projection[i] }
            else arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE)
        val cursor = MatrixCursor(cols, 1)
        val row = Array<Any?>(cols.size) { i ->
            when (cols[i]) {
                OpenableColumns.DISPLAY_NAME -> f.name
                OpenableColumns.SIZE -> f.length()
                else -> null
            }
        }
        cursor.addRow(row)
        return cursor
    }

    override fun getType(uri: Uri): String? = "application/x-mareco"

    override fun insert(uri: Uri, values: ContentValues?): Uri? = null

    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0

    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?): Int = 0

    override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor? {
        val f = resolve(uri) ?: throw FileNotFoundException(uri.toString())
        return ParcelFileDescriptor.open(f, ParcelFileDescriptor.MODE_READ_ONLY)
    }

    private fun resolve(uri: Uri): File? {
        val ctx = context ?: return null
        val name = uri.lastPathSegment ?: return null
        if (name.contains('/') || name.contains('\\') || name.contains("..")) return null
        val dir = shareDir(ctx)
        val f = File(dir, name)
        return if (f.isFile && f.parentFile == dir) f else null
    }

    companion object {
        fun authority(ctx: Context): String = ctx.packageName + ".share"

        fun shareDir(ctx: Context): File {
            val d = File(ctx.cacheDir, "share")
            d.mkdirs()
            return d
        }

        fun uriFor(ctx: Context, file: File): Uri =
            Uri.Builder().scheme("content").authority(authority(ctx)).appendPath(file.name).build()

        fun safeFileName(name: String): String =
            name.replace(Regex("[^A-Za-z0-9._ -]"), "_").trim().ifEmpty { "macro" }.take(60)
    }
}
