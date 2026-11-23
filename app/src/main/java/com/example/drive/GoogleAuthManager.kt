package com.example.drive

import android.accounts.Account
import android.accounts.AccountManager
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
     * Pemilih akun Google di perangkat. Selalu ditampilkan, supaya user bisa memilih akun
     * lain walaupun sudah ada akun yang terhubung. Nama akun ada di KEY_ACCOUNT_NAME.
     */
    fun buildAccountPickerIntent(): Intent =
        AccountManager.newChooseAccountIntent(
            null, null, arrayOf(GOOGLE_ACCOUNT_TYPE), true, null, null, null, null
        )

    /** Nama akun yang dipilih di pemilih, atau null jika user membatalkan. */
    fun emailFromPickerResult(data: Intent?): String? =
        data?.getStringExtra(AccountManager.KEY_ACCOUNT_NAME)?.takeIf { it.isNotBlank() }

    /**
     * Requests Google Drive OAuth access token via Play Services AuthorizationClient untuk
     * akun [email]. Tanpa akun eksplisit, Play Services memakai akun default dan tidak
     * menampilkan pilihan akun. Jika perlu persetujuan, PendingIntent dijalankan lewat [resolutionLauncher].
     */
    fun authorizeDriveInteractive(
        activity: Activity,
        resolutionLauncher: ActivityResultLauncher<IntentSenderRequest>,
        email: String,
        onAccessToken: (String) -> Unit,
        onError: (String) -> Unit
    ) {
        Identity.getAuthorizationClient(activity)
            .authorize(buildDriveAuthRequest(email))
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
