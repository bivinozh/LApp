package com.example.lapp.repository

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.lapp.model.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject

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
    
    private var savedConfiguration: LauncherConfiguration? = null
    private val scope = CoroutineScope(Dispatchers.IO)
    
    companion object {
        private val KEY_LEFT_MENU = stringPreferencesKey("left_side_menu")
        private val KEY_RIGHT_MENU = stringPreferencesKey("right_side_menu")
        private val KEY_MIDDLE_TRAY = stringPreferencesKey("middle_tray")
    }
    
    override fun initializeDefaultConfiguration() {
        val defaultConfig = createDefaultConfiguration()
        
        // Save as the initial saved configuration if none exists
        if (savedConfiguration == null) {
            savedConfiguration = defaultConfig
            println("DEBUG INIT: Set default configuration as initial saved state")
        }
        
        _state.value = _state.value.copy(
            configuration = defaultConfig,
            isModified = false
        )
    }
    
    override fun moveIcon(fromSource: DragSource, fromIndex: Int, toTarget: DragTarget, toIndex: Int) {
        println("DEBUG REPO: moveIcon called - from $fromSource[$fromIndex] to $toTarget[$toIndex]")
        
        val currentState = _state.value
        val config = currentState.configuration
        
        val icon = when (fromSource) {
            DragSource.MIDDLE_TRAY -> config.middleTray[fromIndex]
            DragSource.LEFT_SIDE_MENU -> config.leftSideMenu[fromIndex]
            DragSource.RIGHT_SIDE_MENU -> config.rightSideMenu[fromIndex]
        }
        
        // Get the target icon to check if it's protected
        val targetIcon = when (toTarget) {
            DragTarget.MIDDLE_TRAY -> config.middleTray[toIndex]
            DragTarget.LEFT_SIDE_MENU -> config.leftSideMenu[toIndex]
            DragTarget.RIGHT_SIDE_MENU -> config.rightSideMenu[toIndex]
        }
        
        println("DEBUG REPO: Source icon='${icon?.label}' (protected=${icon?.isProtected})")
        println("DEBUG REPO: Target icon='${targetIcon?.label}' (protected=${targetIcon?.isProtected})")
        
        if (icon == null || icon.isProtected) {
            println("DEBUG REPO: 🔒 BLOCKED - Source icon is null or protected")
            return
        }
        
        // BLOCK if target has a protected icon
        if (targetIcon?.isProtected == true) {
            println("DEBUG REPO: 🔒 BLOCKED - Cannot replace protected icon '${targetIcon.label}' at target position")
            return
        }
        
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
        println("DEBUG REPO: handleSideLauncherToMiddleTrayMove - enabling icon '${draggedIcon.label}' in middle tray")
        
        val currentState = _state.value
        
        // Find the same icon in middle tray by ID and enable it
        val newMiddleTray = config.middleTray.toMutableList()
        val middleTrayIndex = newMiddleTray.indexOfFirst { it?.id == draggedIcon.id }
        
        if (middleTrayIndex != -1) {
            println("DEBUG REPO: Found '${draggedIcon.label}' at middle tray position $middleTrayIndex - enabling it")
            newMiddleTray[middleTrayIndex] = draggedIcon.copy(isEnabled = true)
        } else {
            println("DEBUG REPO: Icon '${draggedIcon.label}' not found in middle tray - cannot enable")
            return
        }
        
        // Remove from source side launcher
        val updatedConfig = when (fromSource) {
            DragSource.LEFT_SIDE_MENU -> {
                val newLeftMenu = config.leftSideMenu.toMutableList()
                newLeftMenu[fromIndex] = null
                println("DEBUG REPO: Removed from left menu position $fromIndex")
                config.copy(
                    middleTray = newMiddleTray,
                    leftSideMenu = newLeftMenu
                )
            }
            DragSource.RIGHT_SIDE_MENU -> {
                val newRightMenu = config.rightSideMenu.toMutableList()
                newRightMenu[fromIndex] = null
                println("DEBUG REPO: Removed from right menu position $fromIndex")
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
        println("DEBUG REPO: handleMiddleTrayToSideLauncherMove - moving '${draggedIcon.label}' to side menu")
        
        val currentState = _state.value
        
        // Get the icon being replaced in side launcher
        val replacedIcon = when (toTarget) {
            DragTarget.LEFT_SIDE_MENU -> config.leftSideMenu[toIndex]
            DragTarget.RIGHT_SIDE_MENU -> config.rightSideMenu[toIndex]
            else -> null
        }
        
        println("DEBUG REPO: Target icon in side menu = '${replacedIcon?.label}' (protected=${replacedIcon?.isProtected})")
        
        // BLOCK if target position has a protected icon
        if (replacedIcon?.isProtected == true) {
            println("DEBUG REPO: 🔒 BLOCKED - Cannot replace protected icon '${replacedIcon.label}' in side menu")
            return
        }
        
        // Disable the middle tray icon (keep it there but make it non-draggable)
        val newMiddleTray = config.middleTray.toMutableList()
        newMiddleTray[fromIndex] = draggedIcon.copy(isEnabled = false)
        println("DEBUG REPO: Disabled '${draggedIcon.label}' at middle tray position $fromIndex")
        
        // If there was an icon being replaced, re-enable it in middle tray
        if (replacedIcon != null && !replacedIcon.isProtected) {
            val replacedIconMiddleIndex = newMiddleTray.indexOfFirst { it?.id == replacedIcon.id }
            if (replacedIconMiddleIndex != -1) {
                newMiddleTray[replacedIconMiddleIndex] = replacedIcon.copy(isEnabled = true)
                println("DEBUG REPO: Re-enabled '${replacedIcon.label}' at middle tray position $replacedIconMiddleIndex")
            }
        }
        
        // Update side launcher with dragged icon (enabled)
        val updatedConfig = when (toTarget) {
            DragTarget.LEFT_SIDE_MENU -> {
                val newLeftMenu = config.leftSideMenu.toMutableList()
                newLeftMenu[toIndex] = draggedIcon.copy(isEnabled = true)
                println("DEBUG REPO: Placed '${draggedIcon.label}' in left menu position $toIndex")
                config.copy(
                    middleTray = newMiddleTray,
                    leftSideMenu = newLeftMenu
                )
            }
            DragTarget.RIGHT_SIDE_MENU -> {
                val newRightMenu = config.rightSideMenu.toMutableList()
                newRightMenu[toIndex] = draggedIcon.copy(isEnabled = true)
                println("DEBUG REPO: Placed '${draggedIcon.label}' in right menu position $toIndex")
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
        println("DEBUG SAVE: Saving configuration to DataStore...")
        val currentConfig = _state.value.configuration
        
        // Save the current configuration as the last saved state
        savedConfiguration = currentConfig
        
        scope.launch {
            try {
                context.dataStore.edit { preferences ->
                    // Serialize left menu
                    preferences[KEY_LEFT_MENU] = serializeIconList(currentConfig.leftSideMenu)
                    // Serialize right menu
                    preferences[KEY_RIGHT_MENU] = serializeIconList(currentConfig.rightSideMenu)
                    // Serialize middle tray
                    preferences[KEY_MIDDLE_TRAY] = serializeIconList(currentConfig.middleTray)
                    
                    println("DEBUG SAVE: Configuration saved successfully")
                    println("DEBUG SAVE: Left menu order: ${currentConfig.leftSideMenu.map { it?.label }}")
                    println("DEBUG SAVE: Right menu order: ${currentConfig.rightSideMenu.map { it?.label }}")
                }
                
                // Mark as not modified
                _state.value = _state.value.copy(isModified = false)
            } catch (e: Exception) {
                println("DEBUG SAVE: Error saving configuration - ${e.message}")
            }
        }
    }
    
    override fun loadConfiguration() {
        println("DEBUG LOAD: Loading configuration from DataStore...")
        
        runBlocking {
            try {
                val preferences = context.dataStore.data.first()
                val leftMenuJson = preferences[KEY_LEFT_MENU]
                val rightMenuJson = preferences[KEY_RIGHT_MENU]
                val middleTrayJson = preferences[KEY_MIDDLE_TRAY]
                
                if (leftMenuJson != null && rightMenuJson != null && middleTrayJson != null) {
                    val leftMenu = deserializeIconList(leftMenuJson)
                    val rightMenu = deserializeIconList(rightMenuJson)
                    val middleTray = deserializeIconList(middleTrayJson)
                    
                    val loadedConfig = LauncherConfiguration(
                        middleTray = middleTray,
                        leftSideMenu = leftMenu,
                        rightSideMenu = rightMenu,
                        availableIcons = emptyList()
                    )
                    
                    savedConfiguration = loadedConfig
                    _state.value = _state.value.copy(
                        configuration = loadedConfig,
                        isModified = false
                    )
                    
                    println("DEBUG LOAD: Configuration loaded successfully")
                    println("DEBUG LOAD: Left menu order: ${leftMenu.map { it?.label }}")
                    println("DEBUG LOAD: Right menu order: ${rightMenu.map { it?.label }}")
                } else {
                    println("DEBUG LOAD: No saved configuration found - using default")
                    initializeDefaultConfiguration()
                }
            } catch (e: Exception) {
                println("DEBUG LOAD: Error loading configuration - ${e.message}")
                initializeDefaultConfiguration()
            }
        }
    }
    
    override fun resetToDefault() {
        println("DEBUG RESET: Resetting to last saved configuration or default")
        
        if (savedConfiguration != null) {
            println("DEBUG RESET: Restoring saved configuration")
            _state.value = _state.value.copy(
                configuration = savedConfiguration!!,
                isModified = false
            )
            println("DEBUG RESET: Left menu order: ${savedConfiguration!!.leftSideMenu.map { it?.label }}")
            println("DEBUG RESET: Right menu order: ${savedConfiguration!!.rightSideMenu.map { it?.label }}")
        } else {
            println("DEBUG RESET: No saved configuration - using default")
            initializeDefaultConfiguration()
        }
    }
    
    private fun disableMiddleTrayIconsInSideMenus(config: LauncherConfiguration): LauncherConfiguration {
        // Collect all non-protected icon IDs that exist in side menus
        val sideMenuIconIds = mutableSetOf<String>()
        config.leftSideMenu.forEach { icon -> 
            icon?.let { if (!it.isProtected) sideMenuIconIds.add(it.id) } 
        }
        config.rightSideMenu.forEach { icon -> 
            icon?.let { if (!it.isProtected) sideMenuIconIds.add(it.id) } 
        }
        
        // Disable those icons in middle tray
        val updatedMiddleTray = config.middleTray.map { icon ->
            if (icon != null && sideMenuIconIds.contains(icon.id)) {
                icon.copy(isEnabled = false)
            } else {
                icon
            }
        }
        
        return config.copy(middleTray = updatedMiddleTray)
    }
    
    private fun serializeIconList(icons: List<IconItem?>): String {
        val jsonArray = JSONArray()
        icons.forEach { icon ->
            if (icon != null) {
                val jsonObject = JSONObject().apply {
                    put("id", icon.id)
                    put("label", icon.label)
                    put("iconRes", icon.iconRes)
                    put("isProtected", icon.isProtected)
                    put("isEnabled", icon.isEnabled)
                }
                jsonArray.put(jsonObject)
            } else {
                jsonArray.put(JSONObject.NULL)
            }
        }
        return jsonArray.toString()
    }
    
    private fun deserializeIconList(json: String): List<IconItem?> {
        val jsonArray = JSONArray(json)
        val icons = mutableListOf<IconItem?>()
        
        for (i in 0 until jsonArray.length()) {
            if (jsonArray.isNull(i)) {
                icons.add(null)
            } else {
                val jsonObject = jsonArray.getJSONObject(i)
                val icon = IconItem(
                    id = jsonObject.getString("id"),
                    label = jsonObject.getString("label"),
                    iconRes = jsonObject.getInt("iconRes"),
                    isProtected = jsonObject.getBoolean("isProtected"),
                    isEnabled = jsonObject.getBoolean("isEnabled")
                )
                icons.add(icon)
            }
        }
        
        return icons
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
        
        // Place all customizable icons in middle tray (all enabled initially)
        val middleTray = mutableListOf<IconItem?>()
        customizableIcons.forEachIndexed { index, icon ->
            middleTray.add(icon.copy(isEnabled = true))
        }
        
        val leftSideMenu = mutableListOf<IconItem?>()
        leftSideMenu.addAll(List(4) { null }) // 4 items
        // Add protected icons and customizable icons to left side menu
        leftSideMenu[0] = protectedIcons[0] // Settings
        leftSideMenu[1] = protectedIcons[1] // LINK
        leftSideMenu[2] = customizableIcons[0] // App 1
        leftSideMenu[3] = customizableIcons[1] // App 2
        
        val rightSideMenu = mutableListOf<IconItem?>()
        rightSideMenu.addAll(List(4) { null }) // 4 items
        // Add protected icons and customizable icons to right side menu
        rightSideMenu[0] = protectedIcons[2] // Phone
        rightSideMenu[1] = protectedIcons[3] // APPS
        rightSideMenu[2] = protectedIcons[4] // ALL MENU
        rightSideMenu[3] = customizableIcons[2] // App 3
        
        // Collect all icon IDs that exist in side menus
        val sideMenuIconIds = mutableSetOf<String>()
        leftSideMenu.forEach { icon -> icon?.let { if (!it.isProtected) sideMenuIconIds.add(it.id) } }
        rightSideMenu.forEach { icon -> icon?.let { if (!it.isProtected) sideMenuIconIds.add(it.id) } }
        
        println("DEBUG REPO: Icons in side menus: $sideMenuIconIds")
        
        // Disable those icons in middle tray
        val finalMiddleTray = middleTray.map { icon ->
            if (icon != null && sideMenuIconIds.contains(icon.id)) {
                println("DEBUG REPO: Disabling '${icon.label}' in middle tray (exists in side menu)")
                icon.copy(isEnabled = false)
            } else {
                icon
            }
        }
        
        println("DEBUG REPO: Middle tray final state:")
        finalMiddleTray.forEachIndexed { idx, icon ->
            println("DEBUG REPO:   [$idx] '${icon?.label}' enabled=${icon?.isEnabled}")
        }
        
        return LauncherConfiguration(
            middleTray = finalMiddleTray,
            leftSideMenu = leftSideMenu,
            rightSideMenu = rightSideMenu,
            availableIcons = emptyList()
        )
    }
}