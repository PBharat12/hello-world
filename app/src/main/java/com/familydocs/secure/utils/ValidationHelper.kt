package com.familydocs.secure.utils

import android.content.Context
import android.widget.Toast

object ValidationHelper {
    
    fun validateMemberName(name: String): ValidationResult {
        return when {
            name.isBlank() -> ValidationResult(false, "Name cannot be empty")
            name.length < 2 -> ValidationResult(false, "Name must be at least 2 characters")
            name.length > 50 -> ValidationResult(false, "Name cannot exceed 50 characters")
            !name.matches(Regex("^[a-zA-Z\\s.]+$")) -> ValidationResult(false, "Name can only contain letters, spaces, and dots")
            else -> ValidationResult(true, "Valid name")
        }
    }
    
    fun validateImageFile(context: Context, uri: android.net.Uri?): ValidationResult {
        if (uri == null) {
            return ValidationResult(false, "Please select an image")
        }
        
        try {
            val mimeType = context.contentResolver.getType(uri)
            if (mimeType == null || !mimeType.startsWith("image/")) {
                return ValidationResult(false, "Selected file is not a valid image")
            }
            
            val inputStream = context.contentResolver.openInputStream(uri)
            val fileSize = inputStream?.available() ?: 0
            inputStream?.close()
            
            // Limit image size to 10MB
            if (fileSize > 10 * 1024 * 1024) {
                return ValidationResult(false, "Image size cannot exceed 10MB")
            }
            
            return ValidationResult(true, "Valid image file")
        } catch (e: Exception) {
            return ValidationResult(false, "Error reading image file: ${e.message}")
        }
    }
    
    fun validateDocumentFile(context: Context, uri: android.net.Uri?): ValidationResult {
        if (uri == null) {
            return ValidationResult(true, "Document is optional") // Document is optional
        }
        
        try {
            val mimeType = context.contentResolver.getType(uri)
            if (mimeType != "application/pdf") {
                return ValidationResult(false, "Only PDF documents are supported")
            }
            
            val inputStream = context.contentResolver.openInputStream(uri)
            val fileSize = inputStream?.available() ?: 0
            inputStream?.close()
            
            // Limit document size to 20MB
            if (fileSize > 20 * 1024 * 1024) {
                return ValidationResult(false, "Document size cannot exceed 20MB")
            }
            
            return ValidationResult(true, "Valid PDF document")
        } catch (e: Exception) {
            return ValidationResult(false, "Error reading document file: ${e.message}")
        }
    }
    
    fun showErrorToast(context: Context, message: String) {
        Toast.makeText(context, message, Toast.LENGTH_LONG).show()
    }
    
    fun showSuccessToast(context: Context, message: String) {
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
    }
}

data class ValidationResult(
    val isValid: Boolean,
    val message: String
)