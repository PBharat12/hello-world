package com.familydocs.secure.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Environment
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.bumptech.glide.Glide
import com.github.barteksc.pdfviewer.listener.OnLoadCompleteListener
import kotlinx.coroutines.launch
import com.familydocs.secure.databinding.ActivityDocumentViewerBinding
import com.familydocs.secure.data.database.AppDatabase
import com.familydocs.secure.data.repository.FamilyRepository
import com.familydocs.secure.utils.FileEncryptionHelper
import com.familydocs.secure.utils.ValidationHelper
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream

class DocumentViewerActivity : AppCompatActivity() {
    
    private lateinit var binding: ActivityDocumentViewerBinding
    private lateinit var repository: FamilyRepository
    private lateinit var encryptionHelper: FileEncryptionHelper
    
    private var memberId: Long = 0
    private var documentType: String = ""
    private var currentImagePath: String? = null
    private var currentDocumentPath: String? = null
    
    companion object {
        private const val STORAGE_PERMISSION_CODE = 100
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityDocumentViewerBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        memberId = intent.getLongExtra("MEMBER_ID", 0)
        documentType = intent.getStringExtra("DOCUMENT_TYPE") ?: ""
        
        setupDatabase()
        setupClickListeners()
        loadDocument()
        checkPermissions()
    }
    
    override fun onResume() {
        super.onResume()
        // Reload document when returning from upload activity
        loadDocument()
    }
    
    private fun setupDatabase() {
        val database = AppDatabase.getDatabase(this)
        repository = FamilyRepository(
            database.familyMemberDao(),
            database.documentDao()
        )
        encryptionHelper = FileEncryptionHelper(this)
    }
    
    private fun setupClickListeners() {
        binding.buttonDownloadImage.setOnClickListener {
            downloadImage()
        }
        
        binding.buttonDownloadDocument.setOnClickListener {
            downloadDocument()
        }
        
        binding.buttonAddDocument.setOnClickListener {
            val intent = Intent(this, DocumentUploadActivity::class.java)
            intent.putExtra("MEMBER_ID", memberId)
            intent.putExtra("DOCUMENT_TYPE", documentType)
            intent.putExtra("DOCUMENT_TYPE_NAME", getDocumentTypeName(documentType))
            startActivity(intent)
        }
    }
    
    private fun loadDocument() {
        lifecycleScope.launch {
            try {
                val document = repository.getDocumentByType(memberId, documentType)
                
                if (document != null) {
                    currentImagePath = document.imagePath
                    currentDocumentPath = document.documentPath
                    
                    // Load image
                    currentImagePath?.let { path ->
                        try {
                            val decryptedImageFile = encryptionHelper.decryptFile(File(path))
                            Glide.with(this@DocumentViewerActivity)
                                .load(decryptedImageFile)
                                .into(binding.imageViewDocument)
                        } catch (e: Exception) {
                            ValidationHelper.showErrorToast(this@DocumentViewerActivity, 
                                "Error loading image: ${e.message}")
                        }
                    }
                    
                    // Load PDF if available
                    currentDocumentPath?.let { path ->
                        try {
                            val decryptedPdfFile = encryptionHelper.decryptFile(File(path))
                            binding.pdfView.fromFile(decryptedPdfFile)
                                .enableSwipe(true)
                                .swipeHorizontal(false)
                                .enableDoubletap(true)
                                .defaultPage(0)
                                .onLoad(OnLoadCompleteListener { 
                                    binding.textViewDocumentStatus.text = "PDF loaded successfully"
                                })
                                .load()
                        } catch (e: Exception) {
                            ValidationHelper.showErrorToast(this@DocumentViewerActivity, 
                                "Error loading PDF: ${e.message}")
                        }
                    }
                    
                    binding.textViewDocumentStatus.text = "${getDocumentTypeName(documentType)} available"
                    binding.buttonAddDocument.text = "Update Document"
                } else {
                    binding.textViewDocumentStatus.text = "No ${getDocumentTypeName(documentType)} found. Click to add."
                    binding.buttonAddDocument.text = "Add Document"
                }
            } catch (e: Exception) {
                ValidationHelper.showErrorToast(this, "Error loading document: ${e.message}")
            }
        }
    }
    
    private fun checkPermissions() {
        if (ContextCompat.checkSelfPermission(
                this, Manifest.permission.WRITE_EXTERNAL_STORAGE
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE),
                STORAGE_PERMISSION_CODE
            )
        }
    }
    
    private fun downloadImage() {
        currentImagePath?.let { path ->
            lifecycleScope.launch {
                try {
                    val decryptedFile = encryptionHelper.decryptFile(File(path))
                    val downloadsDir = Environment.getExternalStoragePublicDirectory(
                        Environment.DIRECTORY_DOWNLOADS
                    )
                    val outputFile = File(downloadsDir, "${documentType}_image_${System.currentTimeMillis()}.jpg")
                    
                    FileInputStream(decryptedFile).use { input ->
                        FileOutputStream(outputFile).use { output ->
                            input.copyTo(output)
                        }
                    }
                    
                    ValidationHelper.showSuccessToast(this@DocumentViewerActivity, 
                        "Image downloaded to Downloads folder")
                } catch (e: Exception) {
                    ValidationHelper.showErrorToast(this@DocumentViewerActivity, 
                        "Download failed: ${e.message}")
                }
            }
        } ?: run {
            ValidationHelper.showErrorToast(this, "No image available to download")
        }
    }
    
    private fun downloadDocument() {
        currentDocumentPath?.let { path ->
            lifecycleScope.launch {
                try {
                    val decryptedFile = encryptionHelper.decryptFile(File(path))
                    val downloadsDir = Environment.getExternalStoragePublicDirectory(
                        Environment.DIRECTORY_DOWNLOADS
                    )
                    val outputFile = File(downloadsDir, "${documentType}_doc_${System.currentTimeMillis()}.pdf")
                    
                    FileInputStream(decryptedFile).use { input ->
                        FileOutputStream(outputFile).use { output ->
                            input.copyTo(output)
                        }
                    }
                    
                    ValidationHelper.showSuccessToast(this@DocumentViewerActivity, 
                        "Document downloaded to Downloads folder")
                } catch (e: Exception) {
                    ValidationHelper.showErrorToast(this@DocumentViewerActivity, 
                        "Download failed: ${e.message}")
                }
            }
        } ?: run {
            ValidationHelper.showErrorToast(this, "No document available to download")
        }
    }
    
    private fun getDocumentTypeName(type: String): String {
        return when (type) {
            "AADHAAR_CARD" -> "Aadhaar Card"
            "VOTER_ID" -> "Voter ID"
            "PAN_CARD" -> "PAN Card"
            "PASSPORT" -> "Passport"
            "DRIVING_LICENSE" -> "Driving License"
            else -> type
        }
    }
}