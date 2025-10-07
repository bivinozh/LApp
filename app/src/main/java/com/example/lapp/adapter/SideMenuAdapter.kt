package com.example.lapp.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.lapp.R
import com.example.lapp.model.IconItem

class SideMenuAdapter(
    private val onItemClick: (position: Int) -> Unit,
    private val onItemLongClick: (position: Int) -> Unit
) : RecyclerView.Adapter<SideMenuAdapter.ViewHolder>() {

    private var items: List<IconItem?> = emptyList()

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
        val icon = items[position]
        
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
                println("DEBUG ADAPTER: Long click at position $position for icon '${icon.label}'")
                onItemLongClick(position)
                true
            } else {
                false
            }
        }
    }

    override fun getItemCount(): Int = items.size

    fun submitList(newItems: List<IconItem?>) {
        println("DEBUG ADAPTER: submitList called with ${newItems.size} items")
        items = newItems
        notifyDataSetChanged()
    }
    
    fun swapItems(fromPosition: Int, toPosition: Int) {
        println("DEBUG ADAPTER: swapItems called - from=$fromPosition to=$toPosition")
        
        if (fromPosition < items.size && toPosition < items.size) {
            val mutableItems = items.toMutableList()
            val temp = mutableItems[fromPosition]
            println("DEBUG ADAPTER: Swapping '${temp?.label}' at $fromPosition with '${mutableItems[toPosition]?.label}' at $toPosition")
            
            mutableItems[fromPosition] = mutableItems[toPosition]
            mutableItems[toPosition] = temp
            
            items = mutableItems
            
            println("DEBUG ADAPTER: After swap - [$fromPosition]=${items[fromPosition]?.label}, [$toPosition]=${items[toPosition]?.label}")
            notifyItemChanged(fromPosition)
            notifyItemChanged(toPosition)
            println("DEBUG ADAPTER: Notified item changes")
        } else {
            println("DEBUG ADAPTER: Invalid positions! fromPosition=$fromPosition, toPosition=$toPosition, size=${items.size}")
        }
    }
    
    fun getCurrentList(): List<IconItem?> = items
}
