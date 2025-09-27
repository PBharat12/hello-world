package com.familydocs.secure.ui

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.bumptech.glide.Glide
import com.github.dhaval2404.imagepicker.ImagePicker
import com.karumi.dexter.Dexter
import com.karumi.dexter.MultiplePermissionsReport
import com.karumi.dexter.PermissionToken
import com.karumi.dexter.listener.PermissionRequest
import com.karumi.dexter.listener.multi.MultiplePermissionsListener
import kotlinx.coroutines.launch
import com.familydocs.secure.R
import com.familydocs.secure.databinding.ActivityAddMemberBinding
import com.familydocs.secure.data.database.AppDatabase
import com.familydocs.secure.data.repository.FamilyRepository
import com.familydocs.secure.data.entity.FamilyMember
import com.familydocs.secure.utils.FileEncryptionHelper
import com.familydocs.secure.utils.ValidationHelper
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream

class AddMemberActivity : AppCompatActivity() {
    
    private lateinit var binding: ActivityAddMemberBinding
    private lateinit var repository: FamilyRepository
    private lateinit var encryptionHelper: FileEncryptionHelper
    
    private var selectedImageUri: Uri? = null
    private var profileImagePath: String? = null
    
    private val relations = arrayOf(
        "Father", "Mother", "Son", "Daughter", "Brother", "Sister",
        "Grandfather", "Grandmother", "Uncle", "Aunt", "Cousin", "Other"
    )
    
    private val imagePickerLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            selectedImageUri = result.data?.data
            selectedImageUri?.let { uri ->
                Glide.with(this)
                    .load(uri)
                    .circleCrop()
                    .into(binding.imageViewProfile)
                binding.textViewSelectImage.text = "Image Selected"
            }
        }
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAddMemberBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        setupDatabase()
        setupSpinner()
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
    
    private fun setupSpinner() {
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, relations)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        binding.spinnerRelation.adapter = adapter
    }
    
    private fun setupClickListeners() {
        binding.imageViewProfile.setOnClickListener {
            openImagePicker()
        }
        
        binding.textViewSelectImage.setOnClickListener {
            openImagePicker()
        }
        
        binding.buttonSave.setOnClickListener {
            saveMember()
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
                            this@AddMemberActivity,
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
        ImagePicker.with(this)
            .compress(1024)
            .maxResultSize(1080, 1080)
            .createIntent { intent ->
                imagePickerLauncher.launch(intent)
            }
    }
    
    private fun saveMember() {
        val name = binding.editTextName.text.toString().trim()
        val relation = binding.spinnerRelation.selectedItem.toString()
        
        if (!validateInput(name)) {
            return
        }
        
        binding.buttonSave.isEnabled = false
        binding.progressBar.visibility = android.view.View.VISIBLE
        
        lifecycleScope.launch {
            try {
                // Save profile image if selected
                selectedImageUri?.let { uri ->
                    profileImagePath = saveProfileImage(uri)
                }
                
                // Create and save family member
                val member = FamilyMember(
                    name = name,
                    relation = relation,
                    profileImage = profileImagePath
                )
                
                val memberId = repository.insertMember(member)
                
                Toast.makeText(this@AddMemberActivity, "Member added successfully", Toast.LENGTH_SHORT).show()
                
                // Return to previous activity
                val resultIntent = Intent()
                resultIntent.putExtra("MEMBER_ID", memberId)
                setResult(Activity.RESULT_OK, resultIntent)
                finish()
                
            } catch (e: Exception) {
                Toast.makeText(this@AddMemberActivity, "Error saving member: ${e.message}", Toast.LENGTH_LONG).show()
            } finally {
                binding.buttonSave.isEnabled = true
                binding.progressBar.visibility = android.view.View.GONE
            }
        }
    }
    
    private fun validateInput(name: String): Boolean {
        val nameValidation = ValidationHelper.validateMemberName(name)
        if (!nameValidation.isValid) {
            binding.editTextName.error = nameValidation.message
            binding.editTextName.requestFocus()
            return false
        }
        
        val imageValidation = ValidationHelper.validateImageFile(this, selectedImageUri)
        if (!imageValidation.isValid && selectedImageUri != null) {
            ValidationHelper.showErrorToast(this, imageValidation.message)
            return false
        }
        
        return true
    }
    
    private suspend fun saveProfileImage(uri: Uri): String {
        val inputStream = contentResolver.openInputStream(uri)
        val tempFile = File(cacheDir, "temp_profile_${System.currentTimeMillis()}.jpg")
        
        inputStream?.use { input ->
            FileOutputStream(tempFile).use { output ->
                input.copyTo(output)
            }
        }
        
        // Encrypt and save the profile image
        val encryptedDir = File(filesDir, "encrypted_profiles")
        if (!encryptedDir.exists()) {
            encryptedDir.mkdirs()
        }
        
        val encryptedFileName = "profile_${System.currentTimeMillis()}.enc"
        val encryptedPath = File(encryptedDir, encryptedFileName).absolutePath
        
        encryptionHelper.encryptAndSaveFile(tempFile, encryptedPath)
        
        // Delete temp file
        tempFile.delete()
        
        return encryptedPath
    }
}