package com.familydocs.secure

import android.app.Application
import com.familydocs.secure.data.database.AppDatabase
import java.io.File

class FamilyDocsApplication : Application() {
    
    val database by lazy { AppDatabase.getDatabase(this) }
    
    override fun onCreate() {
        super.onCreate()
        
        // Create encrypted directories if they don't exist
        val encryptedDocsDir = File(filesDir, "encrypted_documents")
        val encryptedProfilesDir = File(filesDir, "encrypted_profiles")
        
        if (!encryptedDocsDir.exists()) {
            encryptedDocsDir.mkdirs()
        }
        
        if (!encryptedProfilesDir.exists()) {
            encryptedProfilesDir.mkdirs()
        }
    }
}