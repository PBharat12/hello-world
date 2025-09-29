package com.example.home

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.provider.MediaStore
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.bumptech.glide.Glide
import com.example.home.databinding.ActivityDocumentUploadBinding
import com.karumi.dexter.Dexter
import com.karumi.dexter.MultiplePermissionsReport
import com.karumi.dexter.PermissionToken
import com.karumi.dexter.listener.PermissionRequest
import com.karumi.dexter.listener.multi.MultiplePermissionsListener
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream

class DocumentUploadActivity : AppCompatActivity() {

    private lateinit var binding: ActivityDocumentUploadBinding
    private lateinit var repository: FamilyRepository
    private lateinit var encryptionHelper: FileEncryptionHelper

    private var memberId: Long = 0
    private var documentType: String = ""
    private var documentTypeName: String = ""

    private var selectedImageUri: Uri? = null
    private var selectedDocumentUri: Uri? = null

    private val imagePickerLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            when {
                result.data?.data != null -> {
                    // Gallery image
                    selectedImageUri = result.data?.data
                }
                result.data?.extras?.get("data") != null -> {
                    // Camera image
                    val bitmap = result.data?.extras?.get("data") as android.graphics.Bitmap
                    selectedImageUri = saveImageToTemp(bitmap)
                }
            }
            
            selectedImageUri?.let { uri ->
                Glide.with(this)
                    .load(uri)
                    .into(binding.imageViewPreview)
                binding.textViewImageStatus.text = "Image selected"
                binding.buttonSelectImage.text = "Change Image"
            }
        }
    }

    private val documentPickerLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let {
            selectedDocumentUri = it
            val fileName = getFileName(it)
            binding.textViewDocumentStatus.text = "Document selected: $fileName"
            binding.buttonSelectDocument.text = "Change Document"
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityDocumentUploadBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Get intent extras
        memberId = intent.getLongExtra("MEMBER_ID", 0)
        documentType = intent.getStringExtra("DOCUMENT_TYPE") ?: ""
        documentTypeName = intent.getStringExtra("DOCUMENT_TYPE_NAME") ?: documentType

        setupDatabase()
        setupUI()
        setupClickListeners()
        requestPermissions()
    }

    private fun setupDatabase() {
        val database = AppDatabase.getDatabase(this)
        repository = FamilyRepository(
            database.familyMemberDao(),
            database.documentDao()
        )
        encryptionHelper = FileEncryptionHelper(this)
    }

    private fun setupUI() {
        binding.textViewTitle.text = "Add $documentTypeName"
        binding.textViewDescription.text = "Please upload both image and document for $documentTypeName"
    }

    private fun setupClickListeners() {
        binding.buttonSelectImage.setOnClickListener {
            openImagePicker()
        }

        binding.buttonSelectDocument.setOnClickListener {
            openDocumentPicker()
        }

        binding.buttonSave.setOnClickListener {
            saveDocument()
        }

        binding.buttonCancel.setOnClickListener {
            finish()
        }
    }

    private fun requestPermissions() {
        Dexter.withContext(this)
            .withPermissions(
                Manifest.permission.CAMERA,
                Manifest.permission.READ_EXTERNAL_STORAGE,
                Manifest.permission.WRITE_EXTERNAL_STORAGE
            )
            .withListener(object : MultiplePermissionsListener {
                override fun onPermissionsChecked(report: MultiplePermissionsReport?) {
                    if (!report?.areAllPermissionsGranted()!!) {
                        Toast.makeText(
                            this@DocumentUploadActivity,
                            "Please grant all permissions to use this feature",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }

                override fun onPermissionRationaleShouldBeShown(
                    permissions: MutableList<PermissionRequest>?,
                    token: PermissionToken?
                ) {
                    token?.continuePermissionRequest()
                }
            })
            .check()
    }

    private fun openImagePicker() {
        val options = arrayOf("Camera", "Gallery")
        
        val builder = androidx.appcompat.app.AlertDialog.Builder(this)
        builder.setTitle("Select Image")
        builder.setItems(options) { dialog, which ->
            when (which) {
                0 -> openCamera()
                1 -> openGallery()
            }
        }
        builder.show()
    }

    private fun openCamera() {
        val intent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
        if (intent.resolveActivity(packageManager) != null) {
            imagePickerLauncher.launch(intent)
        } else {
            Toast.makeText(this, "Camera not available", Toast.LENGTH_SHORT).show()
        }
    }

    private fun openGallery() {
        val intent = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
        intent.type = "image/*"
        imagePickerLauncher.launch(intent)
    }

    private fun saveImageToTemp(bitmap: android.graphics.Bitmap): Uri {
        val tempFile = File(cacheDir, "temp_camera_${System.currentTimeMillis()}.jpg")
        FileOutputStream(tempFile).use { out ->
            bitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 90, out)
        }
        return Uri.fromFile(tempFile)
    }

    private fun openDocumentPicker() {
        documentPickerLauncher.launch("application/pdf")
    }

    private fun saveDocument() {
        if (!validateInput()) {
            return
        }

        binding.buttonSave.isEnabled = false
        binding.progressBar.visibility = android.view.View.VISIBLE

        lifecycleScope.launch {
            try {
                val imagePath = saveFile(selectedImageUri!!, "image")
                val documentPath = selectedDocumentUri?.let { saveFile(it, "document") }

                val document = Document(
                    memberId = memberId,
                    documentType = documentType,
                    imagePath = imagePath,
                    documentPath = documentPath
                )

                repository.insertDocument(document)

                Toast.makeText(this@DocumentUploadActivity, "Document saved successfully", Toast.LENGTH_SHORT).show()

                setResult(Activity.RESULT_OK)
                finish()

            } catch (e: Exception) {
                Toast.makeText(this@DocumentUploadActivity, "Error saving document: ${e.message}", Toast.LENGTH_LONG).show()
            } finally {
                binding.buttonSave.isEnabled = true
                binding.progressBar.visibility = android.view.View.GONE
            }
        }
    }

    private fun validateInput(): Boolean {
        val imageValidation = ValidationHelper.validateImageFile(this, selectedImageUri)
        if (!imageValidation.isValid) {
            ValidationHelper.showErrorToast(this, imageValidation.message)
            return false
        }

        val documentValidation = ValidationHelper.validateDocumentFile(this, selectedDocumentUri)
        if (!documentValidation.isValid) {
            ValidationHelper.showErrorToast(this, documentValidation.message)
            return false
        }

        return true
    }

    private suspend fun saveFile(uri: Uri, type: String): String {
        val inputStream = contentResolver.openInputStream(uri)
        val extension = if (type == "image") "jpg" else "pdf"
        val tempFile = File(cacheDir, "temp_${type}_${System.currentTimeMillis()}.$extension")

        inputStream?.use { input ->
            FileOutputStream(tempFile).use { output ->
                input.copyTo(output)
            }
        }

        // Encrypt and save the file
        val encryptedDir = File(filesDir, "encrypted_documents")
        if (!encryptedDir.exists()) {
            encryptedDir.mkdirs()
        }

        val encryptedFileName = "${documentType}_${type}_${System.currentTimeMillis()}.enc"
        val encryptedPath = File(encryptedDir, encryptedFileName).absolutePath

        encryptionHelper.encryptAndSaveFile(tempFile, encryptedPath)

        // Delete temp file
        tempFile.delete()

        return encryptedPath
    }

    private fun getFileName(uri: Uri): String {
        var fileName = "Unknown"
        val cursor = contentResolver.query(uri, null, null, null, null)
        cursor?.use {
            if (it.moveToFirst()) {
                val nameIndex = it.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (nameIndex >= 0) {
                    fileName = it.getString(nameIndex)
                }
            }
        }
        return fileName
    }
}