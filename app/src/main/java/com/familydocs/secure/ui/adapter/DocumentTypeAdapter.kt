package com.familydocs.secure.ui.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.familydocs.secure.R
import com.familydocs.secure.databinding.ItemDocumentTypeBinding

class DocumentTypeAdapter(
    private val documentTypes: List<Pair<String, String>>,
    private val onItemClick: (Pair<String, String>) -> Unit
) : RecyclerView.Adapter<DocumentTypeAdapter.ViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemDocumentTypeBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(documentTypes[position])
    }

    override fun getItemCount(): Int = documentTypes.size

    inner class ViewHolder(
        private val binding: ItemDocumentTypeBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(documentType: Pair<String, String>) {
            binding.apply {
                textViewDocumentName.text = documentType.second
                
                // Set icons based on document type
                val iconRes = when (documentType.first) {
                    "AADHAAR_CARD" -> R.drawable.ic_aadhaar
                    "VOTER_ID" -> R.drawable.ic_voter_id
                    "PAN_CARD" -> R.drawable.ic_pan_card
                    "PASSPORT" -> R.drawable.ic_passport
                    "DRIVING_LICENSE" -> R.drawable.ic_driving_license
                    else -> R.drawable.ic_document_default
                }
                
                imageViewDocumentIcon.setImageResource(iconRes)
                
                root.setOnClickListener { 
                    onItemClick(documentType) 
                }
            }
        }
    }
}