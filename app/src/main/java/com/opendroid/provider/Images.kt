package com.opendroid.provider

import android.util.Base64
import java.io.File

/** Encodage base64 des pièces jointes images pour les providers multimodaux. */
object Images {
    fun readBase64(path: String): String? = try {
        val file = File(path)
        if (!file.exists() || !file.isFile) null
        else Base64.encodeToString(file.readBytes(), Base64.NO_WRAP)
    } catch (_: Exception) {
        null
    }
}
