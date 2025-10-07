package com.example.lapp.model

data class LauncherConfiguration(
    val middleTray: List<IconItem?> = List(10) { null }, // 5x2 grid
    val leftSideMenu: List<IconItem?> = List(4) { null }, // 4 items
    val rightSideMenu: List<IconItem?> = List(4) { null }, // 4 items
    val availableIcons: List<IconItem> = emptyList()
)

data class DragDropState(
    val isDragging: Boolean = false,
    val draggedIcon: IconItem? = null,
    val dragSource: DragSource? = null,
    val dragTarget: DragTarget? = null
)

enum class DragSource {
    MIDDLE_TRAY,
    LEFT_SIDE_MENU,
    RIGHT_SIDE_MENU
}

enum class DragTarget {
    MIDDLE_TRAY,
    LEFT_SIDE_MENU,
    RIGHT_SIDE_MENU
}

data class LauncherState(
    val configuration: LauncherConfiguration,
    val dragDropState: DragDropState = DragDropState(),
    val isModified: Boolean = false
)
