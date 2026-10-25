package com.example.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import java.io.File
import java.io.FileOutputStream

object PhotoStorageManager {

    /**
     * Saves a captured Bitmap directly inside the app's private internal storage directory
     * (/data/data/com.example/files/photos/).
     * This file is strictly private to the app and inaccessible to external apps or galleries.
     * Returns the absolute file path of the saved JPEG image.
     */
    fun savePhotoToInternalStorage(context: Context, bitmap: Bitmap): String {
        return try {
            val photosDir = File(context.filesDir, "photos")
            if (!photosDir.exists()) {
                photosDir.mkdirs()
            }
            val fileName = "entry_photo_${System.currentTimeMillis()}.jpg"
            val photoFile = File(photosDir, fileName)

            FileOutputStream(photoFile).use { out ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 90, out)
            }
            photoFile.absolutePath
        } catch (e: Exception) {
            e.printStackTrace()
            ""
        }
    }

    /**
     * Safely loads a Bitmap from an internal private photo path.
     */
    fun loadPhotoFromInternalStorage(path: String): Bitmap? {
        if (path.isBlank()) return null
        return try {
            val file = File(path)
            if (file.exists()) {
                BitmapFactory.decodeFile(file.absolutePath)
            } else {
                null
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /**
     * Deletes a photo file from internal storage if no longer needed.
     */
    fun deletePhoto(path: String): Boolean {
        if (path.isBlank()) return false
        return try {
            val file = File(path)
            if (file.exists()) file.delete() else false
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Permanently deletes all stored photos from internal private storage (/data/data/com.example/files/photos/).
     */
    fun clearAllPhotos(context: Context) {
        try {
            val photosDir = File(context.filesDir, "photos")
            if (photosDir.exists()) {
                photosDir.listFiles()?.forEach { file ->
                    try {
                        if (file.isFile) file.delete()
                    } catch (_: Exception) {}
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /**
     * Exports an image (from internal storage or a content URI) to the public gallery
     * (Pictures/Parkia) so the user can download and access it externally.
     */
    fun exportImageToGallery(
        context: Context,
        imagePathOrUri: String,
        displayNamePrefix: String = "Parkia"
    ): Boolean {
        if (imagePathOrUri.isBlank()) return false
        return try {
            val cleanPath = imagePathOrUri.removePrefix("file://")
            val file = File(cleanPath)
            val inputStream: java.io.InputStream? = if (file.exists() && file.isFile) {
                java.io.FileInputStream(file)
            } else {
                try {
                    context.contentResolver.openInputStream(android.net.Uri.parse(imagePathOrUri))
                } catch (_: Exception) {
                    null
                }
            }

            if (inputStream == null) return false

            val timeStamp = java.text.SimpleDateFormat("yyyyMMdd_HHmmss", java.util.Locale.US).format(java.util.Date())
            val fileName = "${displayNamePrefix}_$timeStamp.jpg"

            val resolver = context.contentResolver
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                val contentValues = android.content.ContentValues().apply {
                    put(android.provider.MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                    put(android.provider.MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
                    put(android.provider.MediaStore.MediaColumns.RELATIVE_PATH, android.os.Environment.DIRECTORY_PICTURES + java.io.File.separator + "Parkia")
                    put(android.provider.MediaStore.MediaColumns.IS_PENDING, 1)
                }
                val collection = android.provider.MediaStore.Images.Media.getContentUri(android.provider.MediaStore.VOLUME_EXTERNAL_PRIMARY)
                val itemUri = resolver.insert(collection, contentValues) ?: return false

                resolver.openOutputStream(itemUri)?.use { out ->
                    inputStream.use { input ->
                        input.copyTo(out)
                    }
                }

                contentValues.clear()
                contentValues.put(android.provider.MediaStore.MediaColumns.IS_PENDING, 0)
                resolver.update(itemUri, contentValues, null, null)
                true
            } else {
                val picturesDir = android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_PICTURES)
                val parkiaDir = File(picturesDir, "Parkia")
                if (!parkiaDir.exists()) parkiaDir.mkdirs()
                val destFile = File(parkiaDir, fileName)
                java.io.FileOutputStream(destFile).use { out ->
                    inputStream.use { input ->
                        input.copyTo(out)
                    }
                }
                android.media.MediaScannerConnection.scanFile(
                    context,
                    arrayOf(destFile.absolutePath),
                    arrayOf("image/jpeg"),
                    null
                )
                true
            }
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }
}

