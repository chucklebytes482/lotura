package com.example.drive

import android.accounts.Account
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.IntentSenderRequest
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.api.Scope
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

object GoogleAuthManager {
    const val DRIVE_FULL_SCOPE = "https://www.googleapis.com/auth/drive"
    private const val GOOGLE_ACCOUNT_TYPE = "com.google"

    /**
     * Membangun permintaan otorisasi Drive. Jika [email] diisi, permintaan diikat ke akun
     * Google tersebut sehingga token yang diambil selalu milik akun itu.
     */
    fun buildDriveAuthRequest(email: String? = null): AuthorizationRequest {
        val builder = AuthorizationRequest.builder()
            .setRequestedScopes(listOf(Scope(DRIVE_FULL_SCOPE)))
        if (!email.isNullOrBlank()) {
            builder.setAccount(Account(email, GOOGLE_ACCOUNT_TYPE))
        }
        return builder.build()
    }

    /**
     * Requests Google Drive OAuth access token via Play Services AuthorizationClient.
     * If user consent is needed, launches the PendingIntent via [resolutionLauncher].
     */
    fun authorizeDriveInteractive(
        activity: Activity,
        resolutionLauncher: ActivityResultLauncher<IntentSenderRequest>,
        onAccessToken: (String) -> Unit,
        onError: (String) -> Unit
    ) {
        Identity.getAuthorizationClient(activity)
            .authorize(buildDriveAuthRequest())
            .addOnSuccessListener { result ->
                if (result.hasResolution()) {
                    val pendingIntent = result.pendingIntent
                    if (pendingIntent != null) {
                        resolutionLauncher.launch(
                            IntentSenderRequest.Builder(pendingIntent.intentSender).build()
                        )
                    } else {
                        onError("Otorisasi Google Drive memerlukan persetujuan ulang.")
                    }
                } else {
                    val token = result.accessToken
                    if (!token.isNullOrBlank()) {
                        onAccessToken(token)
                    } else {
                        onError("Token akses Google Drive tidak diterima. Pastikan OAuth Client Android & SHA-1 sudah terdaftar.")
                    }
                }
            }
            .addOnFailureListener { e ->
                onError(
                    e.localizedMessage
                        ?: "Gagal menghubungi Google AuthorizationClient. Periksa SHA-1 & OAuth Client di Google Cloud Console."
                )
            }
    }

    /**
     * Parses the activity result from [authorizeDriveInteractive] resolution flow.
     */
    fun extractAccessTokenFromResult(context: Context, data: Intent?): Result<String> {
        return runCatching {
            val result = Identity.getAuthorizationClient(context)
                .getAuthorizationResultFromIntent(data)
            val token = result.accessToken
            if (token.isNullOrBlank()) {
                throw IllegalStateException("Token akses Google Drive kosong setelah otorisasi.")
            }
            token
        }
    }

    /**
     * Mengambil token Drive tanpa UI untuk akun [email] yang sudah pernah diotorisasi.
     * Mengembalikan null jika perlu interaksi user.
     */
    suspend fun tryGetSilentAccessToken(context: Context, email: String): String? =
        suspendCancellableCoroutine { cont ->
            Identity.getAuthorizationClient(context)
                .authorize(buildDriveAuthRequest(email))
                .addOnSuccessListener { result ->
                    if (!result.hasResolution() && !result.accessToken.isNullOrBlank()) {
                        cont.resume(result.accessToken)
                    } else {
                        cont.resume(null)
                    }
                }
                .addOnFailureListener {
                    cont.resume(null)
                }
        }
}
