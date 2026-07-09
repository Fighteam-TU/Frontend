package com.fighteam.wannawear.data.remote

import android.content.Context
import android.net.Uri
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody

/**
 * 갤러리에서 고른 이미지를 POST /api/uploads로 업로드하고 서버가 준 fileUrl을 반환한다.
 * purpose: "item_image" | "wearing_image" | "avatar" (API_SPEC.md 8절)
 * 실패하면 null (호출부에서 스낵바 등으로 안내).
 */
suspend fun uploadPickedImage(context: Context, uri: Uri, purpose: String): String? {
    return try {
        val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: return null
        val mime = context.contentResolver.getType(uri) ?: "image/jpeg"
        val ext = when {
            mime.contains("png")  -> "png"
            mime.contains("webp") -> "webp"
            mime.contains("gif")  -> "gif"
            else                  -> "jpg"
        }
        val body = bytes.toRequestBody(mime.toMediaTypeOrNull())
        val part = MultipartBody.Part.createFormData("file", "upload.$ext", body)
        val purposeBody = purpose.toRequestBody("text/plain".toMediaTypeOrNull())
        val res = RetrofitClient.api.uploadFile(part, purposeBody)
        if (res.success) res.data?.fileUrl else null
    } catch (e: Exception) {
        null
    }
}
