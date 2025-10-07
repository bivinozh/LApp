package com.example.lapp.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.lapp.model.*
import com.example.lapp.repository.LauncherRepository
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class LauncherViewModel(
    private val repository: LauncherRepository
) : ViewModel() {
    
    val state: StateFlow<LauncherState> = repository.state
    
    init {
        repository.loadConfiguration()
    }
    
    fun initializeDefaultConfiguration() {
        repository.initializeDefaultConfiguration()
    }
    
    fun moveIcon(fromSource: DragSource, fromIndex: Int, toTarget: DragTarget, toIndex: Int) {
        repository.moveIcon(fromSource, fromIndex, toTarget, toIndex)
    }
    
    fun swapIcons(source: DragSource, sourceIndex: Int, target: DragTarget, targetIndex: Int) {
        repository.swapIcons(source, sourceIndex, target, targetIndex)
    }
    
    fun saveConfiguration() {
        viewModelScope.launch {
            repository.saveConfiguration()
        }
    }
    
    fun resetToDefault() {
        repository.resetToDefault()
    }
    
    fun startDrag(icon: IconItem, source: DragSource) {
        val currentState = repository.state.value
        val newDragState = DragDropState(
            isDragging = true,
            draggedIcon = icon,
            dragSource = source
        )
        // Update state through repository if needed
    }
    
    fun endDrag() {
        val currentState = repository.state.value
        val newDragState = DragDropState()
        // Update state through repository if needed
    }
    
    companion object {
        fun factory(repository: LauncherRepository): ViewModelProvider.Factory {
            return object : ViewModelProvider.Factory {
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    @Suppress("UNCHECKED_CAST")
                    return LauncherViewModel(repository) as T
                }
            }
        }
    }
}
