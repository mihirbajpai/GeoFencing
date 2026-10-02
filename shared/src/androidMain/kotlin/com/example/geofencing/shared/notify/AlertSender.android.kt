package com.example.geofencing.shared.notify

import android.content.Context
import android.util.Base64
import android.util.Log
import com.example.geofencing.shared.AppConfig
import com.example.geofencing.shared.platform.PlatformContext
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.security.KeyFactory
import java.security.Signature
import java.security.spec.PKCS8EncodedKeySpec

actual fun createAlertSender(context: PlatformContext): AlertSender = FcmAlertSender(context)

actual fun subscribeToAlerts() {
    FirebaseMessaging.getInstance().subscribeToTopic(AppConfig.FCM_TOPIC)
        .addOnFailureListener { Log.e("AlertSender", "topic subscribe failed", it) }
}

/** Sends alerts through FCM v1 directly, since there's no backend. Auth uses the bundled service account. */
private class FcmAlertSender(context: Context) : AlertSender {
    private val account by lazy {
        JSONObject(
            context.applicationContext.assets.open("service-account.json").reader()
                .use { it.readText() })
    }
    private var token: String? = null
    private var tokenExpiry = 0L

    override suspend fun sendExitAlert(senderId: String, senderName: String) =
        withContext(Dispatchers.IO) {
            // data-only so receivers handle it in onMessageReceived even in background
            val message = JSONObject()
                .put("topic", AppConfig.FCM_TOPIC)
                .put("data", JSONObject(AlertPayload.exit(senderId, senderName)))
                .put("android", JSONObject().put("priority", "HIGH"))

            val projectId = account.getString("project_id")
            post(
                "https://fcm.googleapis.com/v1/projects/$projectId/messages:send",
                JSONObject().put("message", message).toString(),
                "application/json",
                accessToken(),
            )
            Unit
        }

    @Synchronized
    private fun accessToken(): String {
        val now = System.currentTimeMillis()
        token?.let { if (now < tokenExpiry - 60_000) return it }

        val tokenUri = account.getString("token_uri")
        val form = "grant_type=" + URLEncoder.encode(
            "urn:ietf:params:oauth:grant-type:jwt-bearer",
            "UTF-8"
        ) +
                "&assertion=" + signedJwt(now / 1000, tokenUri)
        val response = JSONObject(post(tokenUri, form, "application/x-www-form-urlencoded"))
        tokenExpiry = now + response.getLong("expires_in") * 1000
        return response.getString("access_token").also { token = it }
    }

    private fun signedJwt(nowSec: Long, audience: String): String {
        val header = JSONObject().put("alg", "RS256").put("typ", "JWT")
        val claims = JSONObject()
            .put("iss", account.getString("client_email"))
            .put("scope", "https://www.googleapis.com/auth/firebase.messaging")
            .put("aud", audience)
            .put("iat", nowSec)
            .put("exp", nowSec + 3600)
        val unsigned =
            b64(header.toString().toByteArray()) + "." + b64(claims.toString().toByteArray())

        val pem = account.getString("private_key")
            .replace("-----BEGIN PRIVATE KEY-----", "")
            .replace("-----END PRIVATE KEY-----", "")
            .replace(Regex("\\s"), "")
        val key = KeyFactory.getInstance("RSA")
            .generatePrivate(PKCS8EncodedKeySpec(Base64.decode(pem, Base64.DEFAULT)))
        val signature = Signature.getInstance("SHA256withRSA").run {
            initSign(key)
            update(unsigned.toByteArray())
            sign()
        }
        return unsigned + "." + b64(signature)
    }

    private fun post(
        url: String,
        body: String,
        contentType: String,
        bearer: String? = null
    ): String {
        val conn = URL(url).openConnection() as HttpURLConnection
        try {
            conn.requestMethod = "POST"
            conn.doOutput = true
            conn.connectTimeout = 10_000
            conn.readTimeout = 10_000
            conn.setRequestProperty("Content-Type", contentType)
            bearer?.let { conn.setRequestProperty("Authorization", "Bearer $it") }
            conn.outputStream.use { it.write(body.toByteArray()) }

            val ok = conn.responseCode in 200..299
            val text =
                (if (ok) conn.inputStream else conn.errorStream)?.reader()?.use { it.readText() }
                    .orEmpty()
            check(ok) { "HTTP ${conn.responseCode}: $text" }
            return text
        } finally {
            conn.disconnect()
        }
    }

    private fun b64(bytes: ByteArray) =
        Base64.encodeToString(bytes, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
}
