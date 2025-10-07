package com.example.lapp.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.lapp.R
import com.example.lapp.model.IconItem

class MiddleTrayAdapter(
    private val onItemClick: (position: Int) -> Unit,
    private val onItemLongClick: (position: Int) -> Unit
) : RecyclerView.Adapter<MiddleTrayAdapter.ViewHolder>() {

    private var items: List<IconItem?> = emptyList()

    class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val iconImageView: ImageView = itemView.findViewById(R.id.iv_icon)
        val labelTextView: TextView = itemView.findViewById(R.id.tv_label)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_icon, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val icon = items[position]
        
        if (icon != null) {
            holder.iconImageView.setImageResource(icon.iconRes)
            holder.labelTextView.text = icon.label
            // Disabled icons (in side tray) show at 40% opacity and are not clickable
            holder.iconImageView.alpha = if (icon.isEnabled) 1.0f else 0.3f
            holder.labelTextView.alpha = if (icon.isEnabled) 1.0f else 0.3f
            holder.itemView.isEnabled = icon.isEnabled && !icon.isProtected
            
            // Visual indicator for disabled state
            if (!icon.isEnabled) {
                holder.itemView.alpha = 0.5f
            } else {
                holder.itemView.alpha = 1.0f
            }
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
                println("DEBUG MIDDLE TRAY: Long click at position $position for icon '${icon.label}' (enabled=${icon.isEnabled})")
                onItemLongClick(position)
                true
            } else {
                if (icon?.isEnabled == false) {
                    println("DEBUG MIDDLE TRAY: 🔒 Long click blocked - '${icon.label}' is disabled (exists in side tray)")
                } else if (icon?.isProtected == true) {
                    println("DEBUG MIDDLE TRAY: 🔒 Long click blocked - '${icon.label}' is protected")
                }
                false
            }
        }
    }

    override fun getItemCount(): Int = items.size

    fun submitList(newItems: List<IconItem?>) {
        items = newItems
        notifyDataSetChanged()
    }
    
    fun getCurrentList(): List<IconItem?> = items
}
