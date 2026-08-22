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
        try {
            val credential = GoogleAccountCredential.usingOAuth2(
                context, Collections.singleton(DriveScopes.DRIVE_FILE)
            )
            credential.selectedAccount = googleAccount.account
            driveService = Drive.Builder(
                NetHttpTransport(),
                GsonFactory.getDefaultInstance(),
                credential
            ).setApplicationName("ExpenseTracker").build()
            Log.d("GoogleDriveService", "Drive service setup successful for ${googleAccount.email}")
        } catch (e: Exception) {
            Log.e("GoogleDriveService", "Failed to setup Drive service", e)
        }
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
            Log.e("GoogleDriveService", "Sign-in failed! Code: ${e.statusCode}")
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
        val service = driveService
        if (service == null) {
            Log.e("GoogleDriveService", "Upload failed: Drive service is null")
            return@withContext null
        }
        
        val existingFileId = getFileId(fileName)
        Log.d("GoogleDriveService", "Uploading $fileName. Existing ID: $existingFileId")
        
        val localFileName = fileName.replace(Regex("[\\\\/:*?\"<>|]"), "_")
        val tempFile = File(context.cacheDir, localFileName)
        tempFile.writeText(content)
        val mediaContent = com.google.api.client.http.FileContent("text/csv", tempFile)

        return@withContext try {
            if (existingFileId != null) {
                // Pass an empty file object instead of null to be safer
                val metadata = com.google.api.services.drive.model.File()
                val updatedFile = service.files().update(existingFileId, metadata, mediaContent).execute()
                Log.d("GoogleDriveService", "File updated successfully: ${updatedFile.id}")
                updatedFile.id
            } else {
                val fileMetadata = com.google.api.services.drive.model.File().apply {
                    name = fileName
                }
                val createdFile = service.files().create(fileMetadata, mediaContent).execute()
                Log.d("GoogleDriveService", "File created successfully: ${createdFile.id}")
                createdFile.id
            }
        } catch (e: Exception) {
            Log.e("GoogleDriveService", "Upload failed for $fileName: ${e.message}", e)
            null
        }
    }

    suspend fun listCsvFiles(): List<Pair<String, String>> = withContext(Dispatchers.IO) {
        val service = driveService ?: return@withContext emptyList()
        try {
            val result = service.files().list()
                .setQ("name contains 'ExpenseTracker' and trashed = false")
                .setSpaces("drive")
                .execute()
            result.files?.map { it.name to it.id } ?: emptyList()
        } catch (e: Exception) {
            Log.e("GoogleDriveService", "List failed", e)
            emptyList()
        }
    }

    suspend fun downloadFile(fileId: String): String? = withContext(Dispatchers.IO) {
        val service = driveService ?: return@withContext null
        try {
            val outputStream = ByteArrayOutputStream()
            service.files().get(fileId).executeMediaAndDownloadTo(outputStream)
            outputStream.toString()
        } catch (e: Exception) {
            Log.e("GoogleDriveService", "Download failed", e)
            null
        }
    }

    private suspend fun getFileId(fileName: String): String? = withContext(Dispatchers.IO) {
        val service = driveService ?: return@withContext null
        try {
            val escapedName = fileName.replace("'", "\\'")
            val result = service.files().list()
                .setQ("mimeType='text/csv' and name='$escapedName' and trashed = false")
                .setSpaces("drive")
                .execute()
            val fileId = result.files?.firstOrNull()?.id
            Log.d("GoogleDriveService", "Found file ID for $fileName: $fileId")
            fileId
        } catch (e: Exception) {
            Log.e("GoogleDriveService", "getFileId failed for $fileName", e)
            null
        }
    }
}
