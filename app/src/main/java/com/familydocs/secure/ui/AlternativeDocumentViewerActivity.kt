package com.familydocs.secure.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.lifecycle.lifecycleScope
import com.bumptech.glide.Glide
import kotlinx.coroutines.launch
import com.familydocs.secure.databinding.ActivityAlternativeDocumentViewerBinding
import com.familydocs.secure.data.database.AppDatabase
import com.familydocs.secure.data.repository.FamilyRepository
import com.familydocs.secure.utils.FileEncryptionHelper
import com.familydocs.secure.utils.ValidationHelper
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream

class AlternativeDocumentViewerActivity : AppCompatActivity() {
    
    private lateinit var binding: ActivityAlternativeDocumentViewerBinding
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
        binding = ActivityAlternativeDocumentViewerBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        memberId = intent.getLongExtra("MEMBER_ID", 0)
        documentType = intent.getStringExtra("DOCUMENT_TYPE") ?: ""
        
        setupDatabase()
        setupWebView()
        setupClickListeners()
        loadDocument()
        checkPermissions()
    }
    
    override fun onResume() {
        super.onResume()
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
    
    private fun setupWebView() {
        binding.webViewPdf.apply {
            webViewClient = WebViewClient()
            settings.apply {
                javaScriptEnabled = true
                allowFileAccess = true
                allowContentAccess = true
            }
        }
    }
    
    private fun setupClickListeners() {
        binding.buttonDownloadImage.setOnClickListener {
            downloadImage()
        }
        
        binding.buttonDownloadDocument.setOnClickListener {
            downloadDocument()
        }
        
        binding.buttonOpenPdfExternal.setOnClickListener {
            openPdfInExternalApp()
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
                            Glide.with(this@AlternativeDocumentViewerActivity)
                                .load(decryptedImageFile)
                                .into(binding.imageViewDocument)
                        } catch (e: Exception) {
                            ValidationHelper.showErrorToast(this@AlternativeDocumentViewerActivity, 
                                "Error loading image: ${e.message}")
                        }
                    }
                    
                    // Handle PDF document
                    currentDocumentPath?.let { path ->
                        try {
                            val decryptedPdfFile = encryptionHelper.decryptFile(File(path))
                            // Use Google Docs Viewer for PDF display
                            val googleDocsUrl = "https://docs.google.com/gview?embedded=true&url=${Uri.fromFile(decryptedPdfFile)}"
                            binding.webViewPdf.loadUrl(googleDocsUrl)
                            binding.buttonOpenPdfExternal.isEnabled = true
                        } catch (e: Exception) {
                            binding.webViewPdf.loadData(
                                "<html><body><h3>PDF document available but cannot be previewed. Use 'Open in External App' or Download.</h3></body></html>",
                                "text/html",
                                "UTF-8"
                            )
                            ValidationHelper.showErrorToast(this@AlternativeDocumentViewerActivity, 
                                "PDF preview not available: ${e.message}")
                        }
                    } ?: run {
                        binding.webViewPdf.loadData(
                            "<html><body><h3>No PDF document available. Only image is stored.</h3></body></html>",
                            "text/html",
                            "UTF-8"
                        )
                        binding.buttonOpenPdfExternal.isEnabled = false
                    }
                    
                    binding.textViewDocumentStatus.text = "${getDocumentTypeName(documentType)} available"
                    binding.buttonAddDocument.text = "Update Document"
                } else {
                    binding.textViewDocumentStatus.text = "No ${getDocumentTypeName(documentType)} found. Click to add."
                    binding.buttonAddDocument.text = "Add Document"
                    binding.webViewPdf.loadData(
                        "<html><body><h3>No document found. Please add a document first.</h3></body></html>",
                        "text/html",
                        "UTF-8"
                    )
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
                    
                    ValidationHelper.showSuccessToast(this@AlternativeDocumentViewerActivity, 
                        "Image downloaded to Downloads folder")
                } catch (e: Exception) {
                    ValidationHelper.showErrorToast(this@AlternativeDocumentViewerActivity, 
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
                    
                    ValidationHelper.showSuccessToast(this@AlternativeDocumentViewerActivity, 
                        "Document downloaded to Downloads folder")
                } catch (e: Exception) {
                    ValidationHelper.showErrorToast(this@AlternativeDocumentViewerActivity, 
                        "Download failed: ${e.message}")
                }
            }
        } ?: run {
            ValidationHelper.showErrorToast(this, "No document available to download")
        }
    }
    
    private fun openPdfInExternalApp() {
        currentDocumentPath?.let { path ->
            lifecycleScope.launch {
                try {
                    val decryptedFile = encryptionHelper.decryptFile(File(path))
                    
                    // Create a temporary file in cache directory
                    val tempFile = File(cacheDir, "temp_pdf_${System.currentTimeMillis()}.pdf")
                    FileInputStream(decryptedFile).use { input ->
                        FileOutputStream(tempFile).use { output ->
                            input.copyTo(output)
                        }
                    }
                    
                    // Create content URI using FileProvider
                    val contentUri = FileProvider.getUriForFile(
                        this@AlternativeDocumentViewerActivity,
                        "${packageName}.fileprovider",
                        tempFile
                    )
                    
                    // Create intent to open PDF
                    val intent = Intent(Intent.ACTION_VIEW).apply {
                        setDataAndType(contentUri, "application/pdf")
                        flags = Intent.FLAG_GRANT_READ_URI_PERMISSION
                    }
                    
                    // Check if there's an app that can handle PDF files
                    if (intent.resolveActivity(packageManager) != null) {
                        startActivity(intent)
                    } else {
                        ValidationHelper.showErrorToast(this@AlternativeDocumentViewerActivity,
                            "No app found to open PDF files")
                    }
                    
                } catch (e: Exception) {
                    ValidationHelper.showErrorToast(this@AlternativeDocumentViewerActivity, 
                        "Error opening PDF: ${e.message}")
                }
            }
        } ?: run {
            ValidationHelper.showErrorToast(this, "No PDF document available")
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