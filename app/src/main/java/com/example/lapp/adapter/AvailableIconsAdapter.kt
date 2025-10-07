package com.example.lapp.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.lapp.R
import com.example.lapp.model.IconItem

class AvailableIconsAdapter(
    private val onItemClick: (icon: IconItem) -> Unit
) : RecyclerView.Adapter<AvailableIconsAdapter.ViewHolder>() {

    private var items: List<IconItem> = emptyList()

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
        
        holder.iconImageView.setImageResource(icon.iconRes)
        holder.labelTextView.text = icon.label
        holder.iconImageView.alpha = if (icon.isEnabled) 1.0f else 0.4f
        holder.labelTextView.alpha = if (icon.isEnabled) 1.0f else 0.4f
        holder.itemView.isEnabled = icon.isEnabled && !icon.isProtected

        holder.itemView.setOnClickListener {
            if (icon.isEnabled && !icon.isProtected) {
                onItemClick(icon)
            }
        }
    }

    override fun getItemCount(): Int = items.size

    fun submitList(newItems: List<IconItem>) {
        items = newItems
        notifyDataSetChanged()
    }
}
