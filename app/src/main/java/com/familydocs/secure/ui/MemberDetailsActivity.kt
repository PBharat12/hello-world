package com.familydocs.secure.ui

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import kotlinx.coroutines.launch
import com.familydocs.secure.databinding.ActivityMemberDetailsBinding
import com.familydocs.secure.data.database.AppDatabase
import com.familydocs.secure.data.repository.FamilyRepository
import com.familydocs.secure.ui.adapter.DocumentTypeAdapter
import com.familydocs.secure.data.entity.FamilyMember

class MemberDetailsActivity : AppCompatActivity() {
    
    private lateinit var binding: ActivityMemberDetailsBinding
    private lateinit var repository: FamilyRepository
    private var memberId: Long = 0
    private var currentMember: FamilyMember? = null
    
    private val documentTypes = listOf(
        "AADHAAR_CARD" to "Aadhaar Card",
        "VOTER_ID" to "Voter ID",
        "PAN_CARD" to "PAN Card",
        "PASSPORT" to "Passport",
        "DRIVING_LICENSE" to "Driving License"
    )
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMemberDetailsBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        memberId = intent.getLongExtra("MEMBER_ID", 0)
        
        setupDatabase()
        setupRecyclerView()
        loadMemberDetails()
    }
    
    private fun setupDatabase() {
        val database = AppDatabase.getDatabase(this)
        repository = FamilyRepository(
            database.familyMemberDao(),
            database.documentDao()
        )
    }
    
    private fun setupRecyclerView() {
        val adapter = DocumentTypeAdapter(documentTypes) { documentType ->
            openDocumentViewer(documentType.first)
        }
        
        binding.recyclerViewDocuments.apply {
            layoutManager = GridLayoutManager(this@MemberDetailsActivity, 2)
            adapter = adapter
        }
    }
    
    private fun loadMemberDetails() {
        lifecycleScope.launch {
            currentMember = repository.getMemberById(memberId)
            currentMember?.let { member ->
                binding.textViewMemberName.text = member.name
                binding.textViewMemberRelation.text = member.relation
            }
        }
    }
    
    private fun openDocumentViewer(documentType: String) {
        lifecycleScope.launch {
            val document = repository.getDocumentByType(memberId, documentType)
            
            if (document != null) {
                // Document exists, open viewer
                val intent = Intent(this@MemberDetailsActivity, DocumentViewerActivity::class.java)
                intent.putExtra("MEMBER_ID", memberId)
                intent.putExtra("DOCUMENT_TYPE", documentType)
                startActivity(intent)
            } else {
                // Document doesn't exist, open upload activity
                val documentTypeName = documentTypes.find { it.first == documentType }?.second ?: documentType
                val intent = Intent(this@MemberDetailsActivity, DocumentUploadActivity::class.java)
                intent.putExtra("MEMBER_ID", memberId)
                intent.putExtra("DOCUMENT_TYPE", documentType)
                intent.putExtra("DOCUMENT_TYPE_NAME", documentTypeName)
                startActivity(intent)
            }
        }
    }
}