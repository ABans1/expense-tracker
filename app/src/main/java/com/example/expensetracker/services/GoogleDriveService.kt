package com.example.expensetracker.services

import android.content.Context
import android.content.Intent
import android.util.Log
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.Scope
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential
import com.google.api.client.http.javanet.NetHttpTransport
import com.google.api.client.json.gson.GsonFactory
import com.google.api.services.drive.Drive
import com.google.api.services.drive.DriveScopes
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.util.Collections

class GoogleDriveService(private val context: Context) {

    private val googleSignInClient: GoogleSignInClient
    private var driveService: Drive? = null

    init {
        val googleSignInOptions = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestEmail()
            .requestScopes(Scope(DriveScopes.DRIVE_FILE))
            .requestIdToken("290685466968-bdiqinta2dv9lk7nco00umgatikkt4fa.apps.googleusercontent.com")
            .build()
        googleSignInClient = GoogleSignIn.getClient(context, googleSignInOptions)
        
        val account = GoogleSignIn.getLastSignedInAccount(context)
        if (account != null) {
            setupDriveService(account)
        }
    }

    private fun setupDriveService(googleAccount: GoogleSignInAccount) {
        val credential = GoogleAccountCredential.usingOAuth2(
            context, Collections.singleton(DriveScopes.DRIVE_FILE)
        )
        credential.selectedAccount = googleAccount.account
        driveService = Drive.Builder(
            NetHttpTransport(),
            GsonFactory.getDefaultInstance(),
            credential
        ).setApplicationName("ExpenseTracker").build()
    }

    fun getSignInIntent(): Intent {
        return googleSignInClient.signInIntent
    }

    fun handleSignInResult(data: Intent?, onSignedIn: (GoogleSignInAccount) -> Unit, onError: (Exception) -> Unit) {
        val task = GoogleSignIn.getSignedInAccountFromIntent(data)
        try {
            val account = task.getResult(ApiException::class.java)
            setupDriveService(account)
            onSignedIn(account)
        } catch (e: ApiException) {
            Log.e("GoogleDriveService", "Sign-in failed! Status Code: ${e.statusCode}. Message: ${e.message}")
            onError(e)
        }
    }

    fun signOut() {
        googleSignInClient.signOut()
        driveService = null
    }

    fun getLastSignedInAccount(): GoogleSignInAccount? {
        return GoogleSignIn.getLastSignedInAccount(context)
    }

    suspend fun uploadCsvFile(fileName: String, content: String): String? = withContext(Dispatchers.IO) {
        if (driveService == null) return@withContext null
        val existingFileId = getFileId(fileName)
        
        val fileMetadata = com.google.api.services.drive.model.File()
        fileMetadata.name = fileName
        val tempFile = File(context.cacheDir, fileName)
        tempFile.writeText(content)
        val mediaContent = com.google.api.client.http.FileContent("text/csv", tempFile)

        try {
            if (existingFileId != null) {
                // Return to original update logic
                val file = driveService?.files()?.update(existingFileId, null, mediaContent)?.execute()
                file?.id
            } else {
                val file = driveService?.files()?.create(fileMetadata, mediaContent)?.execute()
                file?.id
            }
        } catch (e: IOException) {
            Log.e("GoogleDriveService", "Upload failed", e)
            null
        }
    }

    suspend fun listCsvFiles(): List<Pair<String, String>> = withContext(Dispatchers.IO) {
        if (driveService == null) return@withContext emptyList()
        try {
            val files = driveService?.files()?.list()
                ?.setQ("mimeType='text/csv'")
                ?.setSpaces("drive")
                ?.execute()?.files
            files?.map { it.name to it.id } ?: emptyList()
        } catch (e: IOException) {
            emptyList()
        }
    }

    suspend fun downloadFile(fileId: String): String? = withContext(Dispatchers.IO) {
        if (driveService == null) return@withContext null
        try {
            val outputStream = ByteArrayOutputStream()
            driveService?.files()?.get(fileId)?.executeMediaAndDownloadTo(outputStream)
            outputStream.toString()
        } catch (e: IOException) {
            null
        }
    }

    private suspend fun getFileId(fileName: String): String? = withContext(Dispatchers.IO) {
        if (driveService == null) return@withContext null
        try {
            val files = driveService?.files()?.list()
                ?.setQ("mimeType='text/csv' and name='$fileName'")
                ?.setSpaces("drive")
                ?.execute()?.files
            files?.firstOrNull()?.id
        } catch (e: IOException) {
            null
        }
    }
}
