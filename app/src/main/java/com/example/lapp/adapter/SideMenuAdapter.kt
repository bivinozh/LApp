package com.example.lapp.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.lapp.R
import com.example.lapp.model.IconItem

class SideMenuAdapter(
    private val onItemClick: (position: Int) -> Unit,
    private val onItemLongClick: (position: Int) -> Unit
) : ListAdapter<IconItem?, SideMenuAdapter.ViewHolder>(DIFF_CALLBACK) {

    class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val iconImageView: ImageView = itemView.findViewById(R.id.iv_icon)
        val labelTextView: TextView = itemView.findViewById(R.id.tv_label)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_side_menu_icon, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val icon = getItem(position)
        
        if (icon != null) {
            holder.iconImageView.setImageResource(icon.iconRes)
            holder.labelTextView.text = icon.label
            holder.iconImageView.alpha = if (icon.isEnabled) 1.0f else 0.4f
            holder.labelTextView.alpha = if (icon.isEnabled) 1.0f else 0.4f
            holder.itemView.isEnabled = icon.isEnabled && !icon.isProtected
        } else {
            holder.iconImageView.setImageDrawable(null)
            holder.labelTextView.text = ""
            holder.itemView.isEnabled = false
        }

        holder.itemView.setOnClickListener {
            if (icon != null && icon.isEnabled && !icon.isProtected) {
                onItemClick(position)
            }
        }

        holder.itemView.setOnLongClickListener {
            if (icon != null && icon.isEnabled && !icon.isProtected) {
                // Only non-protected icons can be dragged
                println("DEBUG ADAPTER: Long click at position $position for icon '${icon.label}'")
                onItemLongClick(position)
                true
            } else {
                if (icon?.isProtected == true) {
                    println("DEBUG ADAPTER: Long click blocked - icon '${icon.label}' is protected")
                }
                false
            }
        }
    }
    
    private var internalList: MutableList<IconItem?> = mutableListOf()
    
    override fun submitList(list: List<IconItem?>?) {
        if (list != null) {
            internalList = list.toMutableList()
        }
        super.submitList(list)
    }
    
    override fun submitList(list: List<IconItem?>?, commitCallback: Runnable?) {
        if (list != null) {
            internalList = list.toMutableList()
        }
        super.submitList(list, commitCallback)
    }
    
    fun swapItemsImmediately(fromPosition: Int, toPosition: Int) {
        println("DEBUG ADAPTER: swapItemsImmediately called - from=$fromPosition to=$toPosition")
        
        if (fromPosition < internalList.size && toPosition < internalList.size) {
            val fromIcon = internalList[fromPosition]
            val toIcon = internalList[toPosition]
            
            // BLOCK if either position has a protected icon
            if (fromIcon?.isProtected == true) {
                println("DEBUG ADAPTER: 🔒 BLOCKED - Cannot swap from position $fromPosition - '${fromIcon.label}' is protected")
                return
            }
            if (toIcon?.isProtected == true) {
                println("DEBUG ADAPTER: 🔒 BLOCKED - Cannot swap to position $toPosition - '${toIcon.label}' is protected")
                return
            }
            
            println("DEBUG ADAPTER: Swapping '${fromIcon?.label}' at $fromPosition with '${toIcon?.label}' at $toPosition")
            
            // Swap in internal list
            internalList[fromPosition] = toIcon
            internalList[toPosition] = fromIcon
            
            println("DEBUG ADAPTER: After swap - [$fromPosition]=${internalList[fromPosition]?.label}, [$toPosition]=${internalList[toPosition]?.label}")
            
            // Submit the new list WITHOUT async DiffUtil (will update on next frame)
            val newList = internalList.toList()
            super.submitList(null) // Force clear
            super.submitList(newList) // Immediate update
            
            // Force immediate rebind of both positions
            notifyItemChanged(fromPosition, Unit)
            notifyItemChanged(toPosition, Unit)
            
            println("DEBUG ADAPTER: Immediate swap complete")
        } else {
            println("DEBUG ADAPTER: Invalid positions! fromPosition=$fromPosition, toPosition=$toPosition, size=${internalList.size}")
        }
    }
    
    fun getCurrentItem(position: Int): IconItem? {
        return internalList.getOrNull(position)
    }
    
    companion object {
        private val DIFF_CALLBACK = object : DiffUtil.ItemCallback<IconItem?>() {
            override fun areItemsTheSame(
                oldItem: IconItem,
                newItem: IconItem
            ): Boolean {
                // Both null = same
                // One null = different
                // Compare IDs
                val same = oldItem.id == newItem.id
                return same
            }

            override fun areContentsTheSame(
                oldItem: IconItem,
                newItem: IconItem
            ): Boolean {
                val same = oldItem == newItem
                println("DEBUG DIFF: areContentsTheSame('${oldItem.label}', '${newItem.label}') = $same")
                return same
            }
        }
    }
}
