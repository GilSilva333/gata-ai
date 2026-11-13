
package com.gataai.assistant.core

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

class ModelStore(private val context: Context) {

    suspend fun importModel(uri: Uri): File =
        withContext(Dispatchers.IO) {

            val folder = File(context.filesDir, "models")

            if (!folder.exists() && !folder.mkdirs()) {
                throw IllegalStateException(
                    "Não foi possível criar a pasta do modelo."
                )
            }

            val temporaryFile = File(folder, "gata-model.part")
            val finalFile = File(
                folder,
                "Qwen3-0.6B.litertlm"
            )

            try {
                val input = context.contentResolver
                    .openInputStream(uri)
                    ?: throw IllegalStateException(
                        "Não foi possível abrir o ficheiro."
                    )

                input.use { source ->
                    temporaryFile.outputStream().use { output ->
                        source.copyTo(output)
                    }
                }

                if (temporaryFile.length() == 0L) {
                    throw IllegalStateException(
                        "O ficheiro do modelo está vazio."
                    )
                }

                if (finalFile.exists()) {
                    finalFile.delete()
                }

                if (!temporaryFile.renameTo(finalFile)) {
                    throw IllegalStateException(
                        "Não foi possível guardar o modelo."
                    )
                }

                finalFile

            } catch (e: Exception) {
                temporaryFile.delete()
                throw e
            }
        }

    fun getModelFile(): File {
        return File(
            File(context.filesDir, "models"),
            "Qwen3-0.6B.litertlm"
        )
    }

    fun hasModel(): Boolean {
        val file = getModelFile()
        return file.exists() && file.length() > 0
    }
}
