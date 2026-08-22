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

    /**
     * Uploads the CSV file. Version 4: Enhanced logging and error reporting.
     */
    suspend fun uploadCsvFile(fileName: String, content: String): String = withContext(Dispatchers.IO) {
        val service = driveService ?: throw Exception("Google Drive service not initialized. Are you signed in?")
        
        Log.d("GoogleDriveService", "Starting upload for $fileName. Content length: ${content.length}")
        if (content.isEmpty()) {
            Log.w("GoogleDriveService", "Content is empty for $fileName")
        }

        val existingFileId = getFileId(fileName)
        
        // Use unique temp files to avoid conflicts
        val localFileName = "sync_" + fileName.hashCode().toString() + ".csv"
        val tempFile = File(context.cacheDir, localFileName)
        try {
            tempFile.writeText(content)
        } catch (e: Exception) {
            throw Exception("Failed to write local temp file: ${e.message}")
        }
        
        val mediaContent = com.google.api.client.http.FileContent("text/csv", tempFile)

        return@withContext try {
            if (existingFileId != null) {
                Log.d("GoogleDriveService", "Updating existing file ID: $existingFileId")
                service.files().update(existingFileId, null, mediaContent).execute().id
            } else {
                Log.d("GoogleDriveService", "No existing file found. Creating new one.")
                val fileMetadata = com.google.api.services.drive.model.File().apply {
                    name = fileName
                }
                service.files().create(fileMetadata, mediaContent).execute().id
            }
        } catch (e: Exception) {
            Log.e("GoogleDriveService", "Google Drive API failure for $fileName", e)
            throw Exception("Drive API Error: ${e.localizedMessage ?: e.message ?: e.javaClass.simpleName}")
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
            null
        }
    }

    private suspend fun getFileId(fileName: String): String? = withContext(Dispatchers.IO) {
        val service = driveService ?: return@withContext null
        try {
            val escapedName = fileName.replace("'", "\\'")
            val result = service.files().list()
                .setQ("name = '$escapedName' and trashed = false")
                .setSpaces("drive")
                .execute()
            result.files?.firstOrNull()?.id
        } catch (e: Exception) {
            null
        }
    }
}
