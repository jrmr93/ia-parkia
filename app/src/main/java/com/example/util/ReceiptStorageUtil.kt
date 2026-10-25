package com.example.util

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.util.Log
import com.example.data.RechargeHistoryItem
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

object ReceiptStorageUtil {
    private const val TAG = "ReceiptStorageUtil"
    private const val RECEIPTS_DIR_NAME = "receipts"

    /**
     * Retorna el directorio interno de la aplicación donde se almacenan las fotos de comprobantes.
     * /data/data/com.example/files/receipts/
     */
    fun getReceiptsDir(context: Context): File {
        val dir = File(context.filesDir, RECEIPTS_DIR_NAME)
        if (!dir.exists()) {
            dir.mkdirs()
        }
        return dir
    }

    /**
     * Copia la imagen desde el Uri origen (ej. escáner ML Kit o galería)
     * hacia el almacenamiento interno privado de la aplicación.
     * Retorna la ruta absoluta del archivo guardado.
     */
    fun saveReceiptImage(context: Context, sourceUri: Uri): String? {
        return try {
            val receiptsDir = getReceiptsDir(context)
            val fileName = "receipt_${System.currentTimeMillis()}_${UUID.randomUUID().toString().take(6)}.jpg"
            val destFile = File(receiptsDir, fileName)

            context.contentResolver.openInputStream(sourceUri)?.use { input ->
                FileOutputStream(destFile).use { output ->
                    input.copyTo(output)
                }
            }
            Log.d(TAG, "Comprobante guardado exitosamente en: ${destFile.absolutePath}")
            destFile.absolutePath
        } catch (e: Exception) {
            Log.e(TAG, "Error al guardar comprobante en almacenamiento interno: ${e.message}", e)
            null
        }
    }

    /**
     * Guarda un Bitmap directamente en el directorio interno de comprobantes.
     */
    fun saveReceiptBitmap(context: Context, bitmap: Bitmap): String? {
        return try {
            val receiptsDir = getReceiptsDir(context)
            val fileName = "receipt_${System.currentTimeMillis()}_${UUID.randomUUID().toString().take(6)}.jpg"
            val destFile = File(receiptsDir, fileName)

            FileOutputStream(destFile).use { out ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 90, out)
            }
            Log.d(TAG, "Comprobante Bitmap guardado en: ${destFile.absolutePath}")
            destFile.absolutePath
        } catch (e: Exception) {
            Log.e(TAG, "Error al guardar Bitmap de comprobante: ${e.message}", e)
            null
        }
    }

    /**
     * Elimina el archivo de foto de un comprobante dado su path o URI.
     */
    fun deleteReceiptFile(filePathOrUri: String?) {
        if (filePathOrUri.isNullOrBlank()) return
        val paths = filePathOrUri.split("|").map { it.trim() }.filter { it.isNotBlank() }
        paths.forEach { singlePath ->
            try {
                val cleanPath = singlePath.removePrefix("file://")
                val file = File(cleanPath)
                if (file.exists() && file.isFile) {
                    val deleted = file.delete()
                    Log.d(TAG, "Archivo de comprobante eliminado ($deleted): ${file.absolutePath}")
                }
            } catch (t: Throwable) {
                Log.e(TAG, "Error al borrar comprobante: ${t.message}")
            }
        }
    }

    /**
     * Elimina todos los archivos de fotos de comprobantes almacenados en la app.
     */
    fun deleteAllReceiptFiles(context: Context) {
        try {
            val dir = getReceiptsDir(context)
            if (dir.exists() && dir.isDirectory) {
                val files = dir.listFiles()
                files?.forEach { file ->
                    if (file.isFile) {
                        file.delete()
                    }
                }
                Log.d(TAG, "Se eliminaron todos los comprobantes locales (${files?.size ?: 0} archivos)")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error al eliminar todos los comprobantes: ${e.message}", e)
        }
    }

    /**
     * Elimina las fotos asociadas a una lista de recargas.
     */
    fun deleteReceiptsForItems(items: List<RechargeHistoryItem>) {
        items.forEach { recharge ->
            deleteReceiptFile(recharge.receiptPhotoPath)
        }
    }
}
