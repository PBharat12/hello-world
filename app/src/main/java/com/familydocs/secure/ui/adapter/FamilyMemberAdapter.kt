package com.familydocs.secure.ui.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.familydocs.secure.R
import com.familydocs.secure.databinding.ItemFamilyMemberBinding
import com.familydocs.secure.data.entity.FamilyMember
import com.familydocs.secure.utils.FileEncryptionHelper
import java.io.File

class FamilyMemberAdapter(
    private val onItemClick: (FamilyMember) -> Unit
) : ListAdapter<FamilyMember, FamilyMemberAdapter.ViewHolder>(DiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemFamilyMemberBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class ViewHolder(
        private val binding: ItemFamilyMemberBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        private val encryptionHelper = FileEncryptionHelper(itemView.context)

        fun bind(member: FamilyMember) {
            binding.apply {
                textViewName.text = member.name
                textViewRelation.text = member.relation
                
                member.profileImage?.let { imagePath ->
                    try {
                        val decryptedFile = encryptionHelper.decryptFile(File(imagePath))
                        Glide.with(itemView.context)
                            .load(decryptedFile)
                            .circleCrop()
                            .placeholder(R.drawable.ic_person)
                            .error(R.drawable.ic_person)
                            .into(imageViewProfile)
                    } catch (e: Exception) {
                        imageViewProfile.setImageResource(R.drawable.ic_person)
                    }
                } ?: run {
                    imageViewProfile.setImageResource(R.drawable.ic_person)
                }
                
                root.setOnClickListener { onItemClick(member) }
            }
        }
    }

    class DiffCallback : DiffUtil.ItemCallback<FamilyMember>() {
        override fun areItemsTheSame(oldItem: FamilyMember, newItem: FamilyMember): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: FamilyMember, newItem: FamilyMember): Boolean {
            return oldItem == newItem
        }
    }
}