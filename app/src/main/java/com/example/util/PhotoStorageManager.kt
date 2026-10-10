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
}
