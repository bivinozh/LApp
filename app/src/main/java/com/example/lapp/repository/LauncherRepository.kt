package com.example.lapp.repository

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.lapp.model.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map

interface LauncherRepository {
    val state: StateFlow<LauncherState>
    fun initializeDefaultConfiguration()
    fun moveIcon(fromSource: DragSource, fromIndex: Int, toTarget: DragTarget, toIndex: Int)
    fun swapIcons(source: DragSource, sourceIndex: Int, target: DragTarget, targetIndex: Int)
    fun saveConfiguration()
    fun loadConfiguration()
    fun resetToDefault()
}

private val Context.dataStore by preferencesDataStore(name = "launcher_configuration")

class LauncherRepositoryImpl(private val context: Context) : LauncherRepository {
    
    private val _state = MutableStateFlow(LauncherState(LauncherConfiguration()))
    override val state: StateFlow<LauncherState> = _state
    
    override fun initializeDefaultConfiguration() {
        val defaultConfig = createDefaultConfiguration()
        _state.value = _state.value.copy(
            configuration = defaultConfig,
            isModified = false
        )
    }
    
    override fun moveIcon(fromSource: DragSource, fromIndex: Int, toTarget: DragTarget, toIndex: Int) {
        val currentState = _state.value
        val config = currentState.configuration
        
        val icon = when (fromSource) {
            DragSource.MIDDLE_TRAY -> config.middleTray[fromIndex]
            DragSource.LEFT_SIDE_MENU -> config.leftSideMenu[fromIndex]
            DragSource.RIGHT_SIDE_MENU -> config.rightSideMenu[fromIndex]
        }
        
        if (icon == null || icon.isProtected) return
        
        // Handle special case: moving from side launcher to middle tray
        if ((fromSource == DragSource.LEFT_SIDE_MENU || fromSource == DragSource.RIGHT_SIDE_MENU) && toTarget == DragTarget.MIDDLE_TRAY) {
            handleSideLauncherToMiddleTrayMove(config, fromSource, fromIndex, toIndex, icon)
            return
        }
        
        // Handle special case: moving from middle tray to side launcher
        if (fromSource == DragSource.MIDDLE_TRAY && (toTarget == DragTarget.LEFT_SIDE_MENU || toTarget == DragTarget.RIGHT_SIDE_MENU)) {
            handleMiddleTrayToSideLauncherMove(config, fromSource, fromIndex, toTarget, toIndex, icon)
            return
        }
        
        // Handle other moves (middle tray to side launcher, etc.)
        val newConfig = when (toTarget) {
            DragTarget.MIDDLE_TRAY -> {
                val newMiddleTray = config.middleTray.toMutableList()
                val replacedIcon = newMiddleTray[toIndex]
                newMiddleTray[toIndex] = icon.copy(isEnabled = true)
                
                config.copy(middleTray = newMiddleTray)
            }
            DragTarget.LEFT_SIDE_MENU -> {
                val newLeftMenu = config.leftSideMenu.toMutableList()
                val replacedIcon = newLeftMenu[toIndex]
                newLeftMenu[toIndex] = icon.copy(isEnabled = true)
                
                config.copy(leftSideMenu = newLeftMenu)
            }
            DragTarget.RIGHT_SIDE_MENU -> {
                val newRightMenu = config.rightSideMenu.toMutableList()
                val replacedIcon = newRightMenu[toIndex]
                newRightMenu[toIndex] = icon.copy(isEnabled = true)
                
                config.copy(rightSideMenu = newRightMenu)
            }
        }
        
        // Clear the source position
        val finalConfig = when (fromSource) {
            DragSource.MIDDLE_TRAY -> {
                val clearedMiddleTray = newConfig.middleTray.toMutableList()
                clearedMiddleTray[fromIndex] = null
                newConfig.copy(middleTray = clearedMiddleTray)
            }
            DragSource.LEFT_SIDE_MENU -> {
                val clearedLeftMenu = newConfig.leftSideMenu.toMutableList()
                clearedLeftMenu[fromIndex] = null
                newConfig.copy(leftSideMenu = clearedLeftMenu)
            }
            DragSource.RIGHT_SIDE_MENU -> {
                val clearedRightMenu = newConfig.rightSideMenu.toMutableList()
                clearedRightMenu[fromIndex] = null
                newConfig.copy(rightSideMenu = clearedRightMenu)
            }
        }
        
        _state.value = currentState.copy(
            configuration = finalConfig,
            isModified = true
        )
    }
    
    private fun handleSideLauncherToMiddleTrayMove(
        config: LauncherConfiguration,
        fromSource: DragSource,
        fromIndex: Int,
        toIndex: Int,
        draggedIcon: IconItem
    ) {
        val currentState = _state.value
        
        // Get the icon being replaced in middle tray
        val replacedIcon = config.middleTray[toIndex]
        
        // Update middle tray with dragged icon (enabled)
        val newMiddleTray = config.middleTray.toMutableList()
        newMiddleTray[toIndex] = draggedIcon.copy(isEnabled = true)
        
        // Update source side launcher
        val updatedConfig = when (fromSource) {
            DragSource.LEFT_SIDE_MENU -> {
                val newLeftMenu = config.leftSideMenu.toMutableList()
                // Always remove the dragged icon from side launcher (set to null)
                newLeftMenu[fromIndex] = null
                config.copy(
                    middleTray = newMiddleTray,
                    leftSideMenu = newLeftMenu
                )
            }
            DragSource.RIGHT_SIDE_MENU -> {
                val newRightMenu = config.rightSideMenu.toMutableList()
                // Always remove the dragged icon from side launcher (set to null)
                newRightMenu[fromIndex] = null
                config.copy(
                    middleTray = newMiddleTray,
                    rightSideMenu = newRightMenu
                )
            }
            else -> config
        }
        
        _state.value = currentState.copy(
            configuration = updatedConfig,
            isModified = true
        )
    }
    
    private fun handleMiddleTrayToSideLauncherMove(
        config: LauncherConfiguration,
        fromSource: DragSource,
        fromIndex: Int,
        toTarget: DragTarget,
        toIndex: Int,
        draggedIcon: IconItem
    ) {
        val currentState = _state.value
        
        // Get the icon being replaced in side launcher
        val replacedIcon = when (toTarget) {
            DragTarget.LEFT_SIDE_MENU -> config.leftSideMenu[toIndex]
            DragTarget.RIGHT_SIDE_MENU -> config.rightSideMenu[toIndex]
            else -> null
        }
        
        // Update side launcher with dragged icon (enabled)
        val updatedConfig = when (toTarget) {
            DragTarget.LEFT_SIDE_MENU -> {
                val newLeftMenu = config.leftSideMenu.toMutableList()
                newLeftMenu[toIndex] = draggedIcon.copy(isEnabled = true)
                config.copy(leftSideMenu = newLeftMenu)
            }
            DragTarget.RIGHT_SIDE_MENU -> {
                val newRightMenu = config.rightSideMenu.toMutableList()
                newRightMenu[toIndex] = draggedIcon.copy(isEnabled = true)
                config.copy(rightSideMenu = newRightMenu)
            }
            else -> config
        }
        
        // Disable the middle tray position (not remove)
        val finalConfig = updatedConfig.copy(
            middleTray = updatedConfig.middleTray.toMutableList().apply { 
                set(fromIndex, draggedIcon.copy(isEnabled = false)) 
            }
        )
        
        _state.value = currentState.copy(
            configuration = finalConfig,
            isModified = true
        )
    }
    
    override fun swapIcons(source: DragSource, sourceIndex: Int, target: DragTarget, targetIndex: Int) {
        println("DEBUG REPO: swapIcons called - source=$source[$sourceIndex] -> target=$target[$targetIndex]")
        
        val currentState = _state.value
        val config = currentState.configuration
        
        val sourceIcon = when (source) {
            DragSource.MIDDLE_TRAY -> config.middleTray[sourceIndex]
            DragSource.LEFT_SIDE_MENU -> config.leftSideMenu[sourceIndex]
            DragSource.RIGHT_SIDE_MENU -> config.rightSideMenu[sourceIndex]
        }
        
        val targetIcon = when (target) {
            DragTarget.MIDDLE_TRAY -> config.middleTray[targetIndex]
            DragTarget.LEFT_SIDE_MENU -> config.leftSideMenu[targetIndex]
            DragTarget.RIGHT_SIDE_MENU -> config.rightSideMenu[targetIndex]
        }
        
        println("DEBUG REPO: sourceIcon='${sourceIcon?.label}' (protected=${sourceIcon?.isProtected})")
        println("DEBUG REPO: targetIcon='${targetIcon?.label}' (protected=${targetIcon?.isProtected})")
        
        if (sourceIcon == null) {
            println("DEBUG REPO: Blocked - sourceIcon is null")
            return
        }
        
        // BLOCK protected icons - they cannot be moved or swapped at all
        if (sourceIcon.isProtected) {
            println("DEBUG REPO: 🔒 BLOCKED - sourceIcon '${sourceIcon.label}' is protected")
            return
        }
        
        // BLOCK dropping on protected icons - they cannot be replaced
        if (targetIcon?.isProtected == true) {
            println("DEBUG REPO: 🔒 BLOCKED - targetIcon '${targetIcon.label}' is protected")
            return
        }
        
        val newConfig = when {
            source == DragSource.MIDDLE_TRAY && target == DragTarget.MIDDLE_TRAY -> {
                println("DEBUG REPO: Swapping within MIDDLE_TRAY")
                val newMiddleTray = config.middleTray.toMutableList()
                newMiddleTray[sourceIndex] = targetIcon
                newMiddleTray[targetIndex] = sourceIcon
                config.copy(middleTray = newMiddleTray)
            }
            source == DragSource.LEFT_SIDE_MENU && target == DragTarget.LEFT_SIDE_MENU -> {
                println("DEBUG REPO: Swapping within LEFT_SIDE_MENU")
                val newLeftMenu = config.leftSideMenu.toMutableList()
                newLeftMenu[sourceIndex] = targetIcon
                newLeftMenu[targetIndex] = sourceIcon
                println("DEBUG REPO: New left menu - [$sourceIndex]=${newLeftMenu[sourceIndex]?.label}, [$targetIndex]=${newLeftMenu[targetIndex]?.label}")
                config.copy(leftSideMenu = newLeftMenu)
            }
            source == DragSource.RIGHT_SIDE_MENU && target == DragTarget.RIGHT_SIDE_MENU -> {
                println("DEBUG REPO: Swapping within RIGHT_SIDE_MENU")
                val newRightMenu = config.rightSideMenu.toMutableList()
                newRightMenu[sourceIndex] = targetIcon
                newRightMenu[targetIndex] = sourceIcon
                println("DEBUG REPO: New right menu - [$sourceIndex]=${newRightMenu[sourceIndex]?.label}, [$targetIndex]=${newRightMenu[targetIndex]?.label}")
                config.copy(rightSideMenu = newRightMenu)
            }
            else -> {
                println("DEBUG REPO: Cross-container swap not supported")
                config
            }
        }
        
        println("DEBUG REPO: Updating state with isModified=true")
        val newState = currentState.copy(
            configuration = newConfig,
            isModified = true
        )
        _state.value = newState
        
        // Verify the update happened
        val verifyState = _state.value
        println("DEBUG REPO: State updated successfully")
        println("DEBUG REPO: Verification - state after update:")
        when {
            source == DragSource.LEFT_SIDE_MENU && target == DragTarget.LEFT_SIDE_MENU -> {
                println("DEBUG REPO: Left menu [$sourceIndex]='${verifyState.configuration.leftSideMenu[sourceIndex]?.label}', [$targetIndex]='${verifyState.configuration.leftSideMenu[targetIndex]?.label}'")
            }
            source == DragSource.RIGHT_SIDE_MENU && target == DragTarget.RIGHT_SIDE_MENU -> {
                println("DEBUG REPO: Right menu [$sourceIndex]='${verifyState.configuration.rightSideMenu[sourceIndex]?.label}', [$targetIndex]='${verifyState.configuration.rightSideMenu[targetIndex]?.label}'")
            }
        }
    }
    
    override fun saveConfiguration() {
        // Implementation would save to DataStore
        // For now, just mark as not modified
        _state.value = _state.value.copy(isModified = false)
    }
    
    override fun loadConfiguration() {
        // Implementation would load from DataStore
        // For now, initialize default
        initializeDefaultConfiguration()
    }
    
    override fun resetToDefault() {
        initializeDefaultConfiguration()
    }
    
    private fun createDefaultConfiguration(): LauncherConfiguration {
        val protectedIcons = listOf(
            IconItem.createProtected("settings", "Settings", android.R.drawable.ic_menu_preferences),
            IconItem.createProtected("link", "LINK", android.R.drawable.ic_menu_share),
            IconItem.createProtected("phone", "Phone", android.R.drawable.ic_menu_call),
            IconItem.createProtected("apps", "APPS", android.R.drawable.ic_menu_manage),
            IconItem.createProtected("all_menu", "ALL MENU", android.R.drawable.ic_menu_sort_by_size)
        )
        
        val customizableIcons = listOf(
            IconItem.createCustomizable("app1", "App 1", android.R.drawable.ic_menu_add),
            IconItem.createCustomizable("app2", "App 2", android.R.drawable.ic_menu_edit),
            IconItem.createCustomizable("app3", "App 3", android.R.drawable.ic_menu_delete),
            IconItem.createCustomizable("app4", "App 4", android.R.drawable.ic_menu_send),
            IconItem.createCustomizable("app5", "App 5", android.R.drawable.ic_menu_search),
            IconItem.createCustomizable("app6", "App 6", android.R.drawable.ic_menu_view),
            IconItem.createCustomizable("app7", "App 7", android.R.drawable.ic_menu_info_details),
            IconItem.createCustomizable("app8", "App 8", android.R.drawable.ic_menu_help),
            IconItem.createCustomizable("app9", "App 9", android.R.drawable.ic_menu_close_clear_cancel),
            IconItem.createCustomizable("app10", "App 10", android.R.drawable.ic_menu_gallery)
        )
        
        // Place some icons in default positions
        val middleTray = mutableListOf<IconItem?>()
        middleTray.addAll(List(10) { null }) // 5x2 grid = 10 slots
        // Add all customizable icons to middle tray
        customizableIcons.forEachIndexed { index, icon ->
            middleTray[index] = icon // Fill all 10 slots
        }
        
        val leftSideMenu = mutableListOf<IconItem?>()
        leftSideMenu.addAll(List(4) { null }) // 4 items
        // Add protected icons and customizable icons to left side menu
        leftSideMenu[0] = protectedIcons[0] // Settings
        leftSideMenu[1] = protectedIcons[1] // LINK
        leftSideMenu[2] = customizableIcons[0] // App 1 (same as center tray)
        leftSideMenu[3] = customizableIcons[1] // App 2 (same as center tray)
        
        val rightSideMenu = mutableListOf<IconItem?>()
        rightSideMenu.addAll(List(4) { null }) // 4 items
        // Add protected icons and customizable icons to right side menu
        rightSideMenu[0] = protectedIcons[2] // Phone
        rightSideMenu[1] = protectedIcons[3] // APPS
        rightSideMenu[2] = protectedIcons[4] // ALL MENU
        rightSideMenu[3] = customizableIcons[2] // App 3 (same as center tray)
        
        return LauncherConfiguration(
            middleTray = middleTray,
            leftSideMenu = leftSideMenu,
            rightSideMenu = rightSideMenu,
            availableIcons = emptyList() // All icons are now placed
        )
    }
}