package com.example.lapp.model

data class IconItem(
    val id: String,
    val label: String,
    val iconRes: Int,
    val isProtected: Boolean = false,
    val isEnabled: Boolean = true,
    val isDragging: Boolean = false
) {
    companion object {
        fun createProtected(id: String, label: String, iconRes: Int): IconItem {
            return IconItem(
                id = id,
                label = label,
                iconRes = iconRes,
                isProtected = true,
                isEnabled = true
            )
        }
        
        fun createCustomizable(id: String, label: String, iconRes: Int): IconItem {
            return IconItem(
                id = id,
                label = label,
                iconRes = iconRes,
                isProtected = false,
                isEnabled = true
            )
        }
    }
}
