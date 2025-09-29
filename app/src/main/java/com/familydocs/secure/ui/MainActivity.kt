package com.familydocs.secure.ui

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.appcompat.widget.SearchView
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.collect
import com.familydocs.secure.databinding.ActivityMainBinding
import com.familydocs.secure.data.database.AppDatabase
import com.familydocs.secure.data.repository.FamilyRepository
import com.familydocs.secure.ui.adapter.FamilyMemberAdapter
import com.familydocs.secure.data.entity.FamilyMember
import com.familydocs.secure.utils.ValidationHelper

class MainActivity : AppCompatActivity() {
    
    private lateinit var binding: ActivityMainBinding
    private lateinit var repository: FamilyRepository
    private lateinit var adapter: FamilyMemberAdapter
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        setupDatabase()
        setupRecyclerView()
        setupSearchView()
        setupFab()
        observeMembers()
    }
    
    override fun onResume() {
        super.onResume()
        // Refresh the list when returning from other activities
        observeMembers()
    }
    
    private fun setupDatabase() {
        val database = AppDatabase.getDatabase(this)
        repository = FamilyRepository(
            database.familyMemberDao(),
            database.documentDao()
        )
    }
    
    private fun setupRecyclerView() {
        adapter = FamilyMemberAdapter { member ->
            val intent = Intent(this, MemberDetailsActivity::class.java)
            intent.putExtra("MEMBER_ID", member.id)
            startActivity(intent)
        }
        
        binding.recyclerViewMembers.apply {
            layoutManager = LinearLayoutManager(this@MainActivity)
            adapter = this@MainActivity.adapter
        }
    }
    
    private fun setupSearchView() {
        binding.searchView.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?): Boolean {
                return false
            }
            
            override fun onQueryTextChange(newText: String?): Boolean {
                searchMembers(newText ?: "")
                return true
            }
        })
        
        binding.searchView.setOnCloseListener {
            observeMembers()
            false
        }
    }
    
    private fun setupFab() {
        binding.fabAddMember.setOnClickListener {
            startActivity(Intent(this, AddMemberActivity::class.java))
        }
    }
    
    private fun observeMembers() {
        lifecycleScope.launch {
            try {
                repository.getAllMembers().collect { members ->
                    updateUI(members)
                }
            } catch (e: Exception) {
                ValidationHelper.showErrorToast(this@MainActivity, 
                    "Error loading family members: ${e.message}")
            }
        }
    }
    
    private fun searchMembers(query: String) {
        lifecycleScope.launch {
            try {
                if (query.isEmpty()) {
                    repository.getAllMembers().collect { members ->
                        updateUI(members)
                    }
                } else {
                    repository.searchMembers(query).collect { members ->
                        updateUI(members, query)
                    }
                }
            } catch (e: Exception) {
                ValidationHelper.showErrorToast(this@MainActivity, 
                    "Error searching members: ${e.message}")
            }
        }
    }
    
    private fun updateUI(members: List<FamilyMember>, searchQuery: String = "") {
        if (members.isEmpty()) {
            binding.recyclerViewMembers.visibility = View.GONE
            binding.textViewNoMembers.visibility = View.VISIBLE
            
            binding.textViewNoMembers.text = if (searchQuery.isNotEmpty()) {
                "No members found matching '$searchQuery'"
            } else {
                "No family members added yet.\nTap + to add your first family member."
            }
        } else {
            binding.recyclerViewMembers.visibility = View.VISIBLE
            binding.textViewNoMembers.visibility = View.GONE
            adapter.submitList(members)
        }
    }
}