package com.example.lapp

import android.graphics.Point
import android.os.Bundle
import android.view.DragEvent
import android.view.View
import android.widget.Button
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.lapp.adapter.MiddleTrayAdapter
import com.example.lapp.adapter.SideMenuAdapter
import com.example.lapp.model.*
import com.example.lapp.repository.LauncherRepositoryImpl
import com.example.lapp.viewmodel.LauncherViewModel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private lateinit var repository: LauncherRepositoryImpl
    private lateinit var viewModel: LauncherViewModel

    private lateinit var middleTrayRecyclerView: RecyclerView
    private lateinit var leftSideMenuRecyclerView: RecyclerView
    private lateinit var rightSideMenuRecyclerView: RecyclerView

    private lateinit var middleTrayAdapter: MiddleTrayAdapter
    private lateinit var leftSideMenuAdapter: SideMenuAdapter
    private lateinit var rightSideMenuAdapter: SideMenuAdapter

    private lateinit var dragOverlay: TextView
    private lateinit var okButton: Button
    private lateinit var cancelButton: Button

    private var draggedIcon: IconItem? = null
    private var dragSource: DragSource? = null
    private var dragSourceIndex: Int = -1

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)
        
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        initializeRepository()
        initializeViews()
        setupRecyclerViews()
        setupDragAndDrop()
        setupButtons()
        observeViewModel()
        
        viewModel.initializeDefaultConfiguration()
    }

    private fun initializeRepository() {
        repository = LauncherRepositoryImpl(this)
        viewModel = LauncherViewModel(repository)
    }

    private fun initializeViews() {
        middleTrayRecyclerView = findViewById(R.id.rv_middle_tray)
        leftSideMenuRecyclerView = findViewById(R.id.rv_left_side_menu)
        rightSideMenuRecyclerView = findViewById(R.id.rv_right_side_menu)
        dragOverlay = findViewById(R.id.tv_drag_overlay)
        okButton = findViewById(R.id.btn_ok)
        cancelButton = findViewById(R.id.btn_cancel)
    }

    private fun setupRecyclerViews() {
        // Middle Tray (5x2 Grid)
        middleTrayAdapter = MiddleTrayAdapter(
            onItemClick = { position -> handleMiddleTrayClick(position) },
            onItemLongClick = { position -> startDragFromMiddleTray(position) }
        )
        middleTrayRecyclerView.layoutManager = GridLayoutManager(this, 5)
        middleTrayRecyclerView.adapter = middleTrayAdapter

        // Left Side Menu
        leftSideMenuAdapter = SideMenuAdapter(
            onItemClick = { position -> handleLeftSideMenuClick(position) },
            onItemLongClick = { position -> startDragFromLeftSideMenu(position) }
        )
        leftSideMenuRecyclerView.layoutManager = LinearLayoutManager(this)
        leftSideMenuRecyclerView.adapter = leftSideMenuAdapter

        // Right Side Menu
        rightSideMenuAdapter = SideMenuAdapter(
            onItemClick = { position -> handleRightSideMenuClick(position) },
            onItemLongClick = { position -> startDragFromRightSideMenu(position) }
        )
        rightSideMenuRecyclerView.layoutManager = LinearLayoutManager(this)
        rightSideMenuRecyclerView.adapter = rightSideMenuAdapter
        
        // Set up ItemTouchHelper for internal drag and drop within side menus
        setupItemTouchHelper()
    }

    private fun setupItemTouchHelper() {
        println("DEBUG SETUP: Setting up ItemTouchHelpers")
        
        // ItemTouchHelper for left side menu internal drag and drop
        val leftItemTouchHelper = ItemTouchHelper(object : ItemTouchHelper.SimpleCallback(
            ItemTouchHelper.UP or ItemTouchHelper.DOWN, 0
        ) {
            override fun onSelectedChanged(viewHolder: RecyclerView.ViewHolder?, actionState: Int) {
                super.onSelectedChanged(viewHolder, actionState)
                when (actionState) {
                    ItemTouchHelper.ACTION_STATE_DRAG -> {
                        println("DEBUG LEFT: Drag started at position ${viewHolder?.bindingAdapterPosition}")
                    }
                    ItemTouchHelper.ACTION_STATE_IDLE -> {
                        println("DEBUG LEFT: Drag ended")
                    }
                }
            }
            override fun getMovementFlags(
                recyclerView: RecyclerView,
                viewHolder: RecyclerView.ViewHolder
            ): Int {
                val position = viewHolder.bindingAdapterPosition
                if (position == RecyclerView.NO_POSITION) return 0
                
                val icon = leftSideMenuAdapter.getCurrentList().getOrNull(position)
                
                println("DEBUG LEFT: getMovementFlags for position $position, icon=${icon?.label}, isProtected=${icon?.isProtected}")
                
                // Don't allow dragging protected or null icons
                return if (icon != null && !icon.isProtected && icon.isEnabled) {
                    println("DEBUG LEFT: Allowing drag for ${icon.label}")
                    makeMovementFlags(ItemTouchHelper.UP or ItemTouchHelper.DOWN, 0)
                } else {
                    0
                }
            }
            
            override fun canDropOver(
                recyclerView: RecyclerView,
                current: RecyclerView.ViewHolder,
                target: RecyclerView.ViewHolder
            ): Boolean {
                val targetPosition = target.bindingAdapterPosition
                if (targetPosition == RecyclerView.NO_POSITION) return false
                
                val targetIcon = leftSideMenuAdapter.getCurrentList().getOrNull(targetPosition)
                
                val canDrop = targetIcon == null || (!targetIcon.isProtected && targetIcon.isEnabled)
                println("DEBUG LEFT: canDropOver to position $targetPosition, targetIcon=${targetIcon?.label}, canDrop=$canDrop")
                
                // Don't allow dropping on protected icons
                return canDrop
            }
            
            override fun onMove(
                recyclerView: RecyclerView,
                viewHolder: RecyclerView.ViewHolder,
                target: RecyclerView.ViewHolder
            ): Boolean {
                val fromPosition = viewHolder.bindingAdapterPosition
                val toPosition = target.bindingAdapterPosition
                
                println("DEBUG LEFT: onMove from $fromPosition to $toPosition")
                
                if (fromPosition == RecyclerView.NO_POSITION || toPosition == RecyclerView.NO_POSITION) {
                    println("DEBUG LEFT: Invalid positions")
                    return false
                }
                
                // Swap items in adapter immediately for smooth animation
                leftSideMenuAdapter.swapItems(fromPosition, toPosition)
                println("DEBUG LEFT: Swapped in adapter")
                
                // Update ViewModel state
                viewModel.swapIcons(DragSource.LEFT_SIDE_MENU, fromPosition, DragTarget.LEFT_SIDE_MENU, toPosition)
                println("DEBUG LEFT: Updated ViewModel")
                return true
            }

            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
                // Not used for drag and drop
            }

            override fun isLongPressDragEnabled(): Boolean {
                println("DEBUG LEFT: isLongPressDragEnabled called - returning false (using View drag system)")
                return false  // Disable ItemTouchHelper long press, use View drag system instead
            }
        })
        leftItemTouchHelper.attachToRecyclerView(leftSideMenuRecyclerView)
        println("DEBUG SETUP: Left ItemTouchHelper attached to RecyclerView")

        // ItemTouchHelper for right side menu internal drag and drop
        val rightItemTouchHelper = ItemTouchHelper(object : ItemTouchHelper.SimpleCallback(
            ItemTouchHelper.UP or ItemTouchHelper.DOWN, 0
        ) {
            override fun onSelectedChanged(viewHolder: RecyclerView.ViewHolder?, actionState: Int) {
                super.onSelectedChanged(viewHolder, actionState)
                when (actionState) {
                    ItemTouchHelper.ACTION_STATE_DRAG -> {
                        println("DEBUG RIGHT: Drag started at position ${viewHolder?.bindingAdapterPosition}")
                    }
                    ItemTouchHelper.ACTION_STATE_IDLE -> {
                        println("DEBUG RIGHT: Drag ended")
                    }
                }
            }
            override fun getMovementFlags(
                recyclerView: RecyclerView,
                viewHolder: RecyclerView.ViewHolder
            ): Int {
                val position = viewHolder.bindingAdapterPosition
                if (position == RecyclerView.NO_POSITION) return 0
                
                val icon = rightSideMenuAdapter.getCurrentList().getOrNull(position)
                
                println("DEBUG RIGHT: getMovementFlags for position $position, icon=${icon?.label}, isProtected=${icon?.isProtected}")
                
                // Don't allow dragging protected or null icons
                return if (icon != null && !icon.isProtected && icon.isEnabled) {
                    println("DEBUG RIGHT: Allowing drag for ${icon.label}")
                    makeMovementFlags(ItemTouchHelper.UP or ItemTouchHelper.DOWN, 0)
                } else {
                    0
                }
            }
            
            override fun canDropOver(
                recyclerView: RecyclerView,
                current: RecyclerView.ViewHolder,
                target: RecyclerView.ViewHolder
            ): Boolean {
                val targetPosition = target.bindingAdapterPosition
                if (targetPosition == RecyclerView.NO_POSITION) return false
                
                val targetIcon = rightSideMenuAdapter.getCurrentList().getOrNull(targetPosition)
                
                val canDrop = targetIcon == null || (!targetIcon.isProtected && targetIcon.isEnabled)
                println("DEBUG RIGHT: canDropOver to position $targetPosition, targetIcon=${targetIcon?.label}, canDrop=$canDrop")
                
                // Don't allow dropping on protected icons
                return canDrop
            }
            
            override fun onMove(
                recyclerView: RecyclerView,
                viewHolder: RecyclerView.ViewHolder,
                target: RecyclerView.ViewHolder
            ): Boolean {
                val fromPosition = viewHolder.bindingAdapterPosition
                val toPosition = target.bindingAdapterPosition
                
                if (fromPosition == RecyclerView.NO_POSITION || toPosition == RecyclerView.NO_POSITION) {
                    return false
                }
                
                // Swap items in adapter immediately for smooth animation
                rightSideMenuAdapter.swapItems(fromPosition, toPosition)
                
                // Update ViewModel state
                viewModel.swapIcons(DragSource.RIGHT_SIDE_MENU, fromPosition, DragTarget.RIGHT_SIDE_MENU, toPosition)
                return true
            }

            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
                // Not used for drag and drop
            }

            override fun isLongPressDragEnabled(): Boolean {
                println("DEBUG RIGHT: isLongPressDragEnabled called - returning false (using View drag system)")
                return false  // Disable ItemTouchHelper long press, use View drag system instead
            }
        })
        rightItemTouchHelper.attachToRecyclerView(rightSideMenuRecyclerView)
        println("DEBUG SETUP: Right ItemTouchHelper attached to RecyclerView")
        println("DEBUG SETUP: All ItemTouchHelpers configured successfully")
    }

    private fun setupDragAndDrop() {
        // Set up drag listeners for all RecyclerViews
        setupDragListener(middleTrayRecyclerView, DragTarget.MIDDLE_TRAY)
        setupDragListener(leftSideMenuRecyclerView, DragTarget.LEFT_SIDE_MENU)
        setupDragListener(rightSideMenuRecyclerView, DragTarget.RIGHT_SIDE_MENU)
    }

    private fun setupDragListener(recyclerView: RecyclerView, target: DragTarget) {
        recyclerView.setOnDragListener { view, dragEvent ->
            when (dragEvent.action) {
                DragEvent.ACTION_DRAG_STARTED -> {
                    dragOverlay.visibility = View.VISIBLE
                    true
                }
                DragEvent.ACTION_DRAG_ENTERED -> {
                    view.alpha = 0.8f
                    true
                }
                DragEvent.ACTION_DRAG_EXITED -> {
                    view.alpha = 1.0f
                    true
                }
                DragEvent.ACTION_DROP -> {
                    view.alpha = 1.0f
                    dragOverlay.visibility = View.GONE
                    
                    val position = getDropPosition(recyclerView, dragEvent)
                    if (position != -1 && draggedIcon != null && dragSource != null) {
                        handleDrop(dragSource!!, dragSourceIndex, target, position)
                    }
                    true
                }
                DragEvent.ACTION_DRAG_ENDED -> {
                    view.alpha = 1.0f
                    dragOverlay.visibility = View.GONE
                    draggedIcon = null
                    dragSource = null
                    dragSourceIndex = -1
                    true
                }
                else -> false
            }
        }
    }

    private fun getDropPosition(recyclerView: RecyclerView, dragEvent: DragEvent): Int {
        val x = dragEvent.x.toInt()
        val y = dragEvent.y.toInt()
        
        val child = recyclerView.findChildViewUnder(x.toFloat(), y.toFloat())
        return if (child != null) {
            recyclerView.getChildAdapterPosition(child)
        } else {
            -1
        }
    }

    private fun startDragFromMiddleTray(position: Int) {
        val state = viewModel.state.value
        val icon = state.configuration.middleTray[position]
        if (icon != null && icon.isEnabled && !icon.isProtected) {
            startDrag(icon, DragSource.MIDDLE_TRAY, position)
        }
    }

    private fun startDragFromLeftSideMenu(position: Int) {
        println("DEBUG DRAG: startDragFromLeftSideMenu at position $position")
        val state = viewModel.state.value
        val icon = state.configuration.leftSideMenu[position]
        println("DEBUG DRAG: Icon at position $position = '${icon?.label}', enabled=${icon?.isEnabled}, protected=${icon?.isProtected}")
        if (icon != null && icon.isEnabled && !icon.isProtected) {
            println("DEBUG DRAG: Starting drag for '${icon.label}'")
            startDrag(icon, DragSource.LEFT_SIDE_MENU, position)
        } else {
            println("DEBUG DRAG: Cannot drag - icon is null, disabled, or protected")
        }
    }

    private fun startDragFromRightSideMenu(position: Int) {
        println("DEBUG DRAG: startDragFromRightSideMenu at position $position")
        val state = viewModel.state.value
        val icon = state.configuration.rightSideMenu[position]
        println("DEBUG DRAG: Icon at position $position = '${icon?.label}', enabled=${icon?.isEnabled}, protected=${icon?.isProtected}")
        if (icon != null && icon.isEnabled && !icon.isProtected) {
            println("DEBUG DRAG: Starting drag for '${icon.label}'")
            startDrag(icon, DragSource.RIGHT_SIDE_MENU, position)
        } else {
            println("DEBUG DRAG: Cannot drag - icon is null, disabled, or protected")
        }
    }

    private fun startDrag(icon: IconItem, source: DragSource, sourceIndex: Int) {
        draggedIcon = icon
        dragSource = source
        dragSourceIndex = sourceIndex
        
        // Get the view holder for the dragged item
        val viewHolder = when (source) {
            DragSource.MIDDLE_TRAY -> middleTrayRecyclerView.findViewHolderForAdapterPosition(sourceIndex)
            DragSource.LEFT_SIDE_MENU -> leftSideMenuRecyclerView.findViewHolderForAdapterPosition(sourceIndex)
            DragSource.RIGHT_SIDE_MENU -> rightSideMenuRecyclerView.findViewHolderForAdapterPosition(sourceIndex)
        }
        
        val view = viewHolder?.itemView
        if (view != null && view.width > 0 && view.height > 0) {
            val dragShadowBuilder = View.DragShadowBuilder(view)
            view.startDrag(null, dragShadowBuilder, icon, 0)
        } else {
            // Fallback: create a simple drag shadow with default dimensions
            val dragShadowBuilder = object : View.DragShadowBuilder() {
                override fun onProvideShadowMetrics(outShadowSize: Point?, outShadowTouchPoint: Point?) {
                    outShadowSize?.set(100, 100) // Default size
                    outShadowTouchPoint?.set(50, 50) // Center point
                }
            }
            
            // Use the RecyclerView as the drag source
            val recyclerView = when (source) {
                DragSource.MIDDLE_TRAY -> middleTrayRecyclerView
                DragSource.LEFT_SIDE_MENU -> leftSideMenuRecyclerView
                DragSource.RIGHT_SIDE_MENU -> rightSideMenuRecyclerView
            }
            recyclerView.startDrag(null, dragShadowBuilder, icon, 0)
        }
    }

    private fun handleDrop(fromSource: DragSource, fromIndex: Int, toTarget: DragTarget, toIndex: Int) {
        println("DEBUG DROP: handleDrop - from $fromSource[$fromIndex] to $toTarget[$toIndex]")
        
        // Check if it's the same container and same position
        val isSamePosition = when {
            fromSource == DragSource.MIDDLE_TRAY && toTarget == DragTarget.MIDDLE_TRAY && fromIndex == toIndex -> true
            fromSource == DragSource.LEFT_SIDE_MENU && toTarget == DragTarget.LEFT_SIDE_MENU && fromIndex == toIndex -> true
            fromSource == DragSource.RIGHT_SIDE_MENU && toTarget == DragTarget.RIGHT_SIDE_MENU && fromIndex == toIndex -> true
            else -> false
        }
        
        if (isSamePosition) {
            println("DEBUG DROP: Same position - ignoring")
            return
        }
        
        // Check if it's an internal swap (same container, different position)
        val isInternalSwap = when {
            fromSource == DragSource.LEFT_SIDE_MENU && toTarget == DragTarget.LEFT_SIDE_MENU -> true
            fromSource == DragSource.RIGHT_SIDE_MENU && toTarget == DragTarget.RIGHT_SIDE_MENU -> true
            else -> false
        }
        
        if (isInternalSwap) {
            println("DEBUG DROP: Internal swap detected - calling swapIcons")
            // Update adapter immediately for visual feedback
            when (fromSource) {
                DragSource.LEFT_SIDE_MENU -> leftSideMenuAdapter.swapItems(fromIndex, toIndex)
                DragSource.RIGHT_SIDE_MENU -> rightSideMenuAdapter.swapItems(fromIndex, toIndex)
                else -> {}
            }
            // Update ViewModel state
            viewModel.swapIcons(fromSource, fromIndex, toTarget, toIndex)
        } else {
            println("DEBUG DROP: Cross-container move - calling moveIcon")
            viewModel.moveIcon(fromSource, fromIndex, toTarget, toIndex)
        }
    }

    private fun handleMiddleTrayClick(position: Int) {
        // Move icon back to available icons or first empty side menu slot
        val state = viewModel.state.value
        val icon = state.configuration.middleTray[position]
        if (icon != null && icon.isEnabled && !icon.isProtected) {
            // Find first empty slot in left side menu, then right
            val leftEmptyIndex = state.configuration.leftSideMenu.indexOfFirst { it == null }
            val rightEmptyIndex = state.configuration.rightSideMenu.indexOfFirst { it == null }
            
            when {
                leftEmptyIndex != -1 -> viewModel.moveIcon(DragSource.MIDDLE_TRAY, position, DragTarget.LEFT_SIDE_MENU, leftEmptyIndex)
                rightEmptyIndex != -1 -> viewModel.moveIcon(DragSource.MIDDLE_TRAY, position, DragTarget.RIGHT_SIDE_MENU, rightEmptyIndex)
            }
        }
    }

    private fun handleLeftSideMenuClick(position: Int) {
        val state = viewModel.state.value
        val icon = state.configuration.leftSideMenu[position]
        if (icon != null && icon.isEnabled && !icon.isProtected) {
            // Find first empty slot in middle tray
            val middleEmptyIndex = state.configuration.middleTray.indexOfFirst { it == null }
            if (middleEmptyIndex != -1) {
                viewModel.moveIcon(DragSource.LEFT_SIDE_MENU, position, DragTarget.MIDDLE_TRAY, middleEmptyIndex)
            }
        }
    }

    private fun handleRightSideMenuClick(position: Int) {
        val state = viewModel.state.value
        val icon = state.configuration.rightSideMenu[position]
        if (icon != null && icon.isEnabled && !icon.isProtected) {
            // Find first empty slot in middle tray
            val middleEmptyIndex = state.configuration.middleTray.indexOfFirst { it == null }
            if (middleEmptyIndex != -1) {
                viewModel.moveIcon(DragSource.RIGHT_SIDE_MENU, position, DragTarget.MIDDLE_TRAY, middleEmptyIndex)
            }
        }
    }

    private fun setupButtons() {
        okButton.setOnClickListener {
            viewModel.saveConfiguration()
            finish()
        }
        
        cancelButton.setOnClickListener {
            viewModel.resetToDefault()
            finish()
        }
    }

    private fun observeViewModel() {
        lifecycleScope.launch {
            viewModel.state.collectLatest { state ->
                println("DEBUG OBSERVE: ===== State Update Received =====")
                println("DEBUG OBSERVE: Middle tray size=${state.configuration.middleTray.size}")
                println("DEBUG OBSERVE: Left menu: ${state.configuration.leftSideMenu.mapIndexed { i, icon -> "[$i]=${icon?.label}" }}")
                println("DEBUG OBSERVE: Right menu: ${state.configuration.rightSideMenu.mapIndexed { i, icon -> "[$i]=${icon?.label}" }}")
                println("DEBUG OBSERVE: isModified=${state.isModified}")
                
                println("DEBUG OBSERVE: Submitting to adapters...")
                middleTrayAdapter.submitList(state.configuration.middleTray)
                leftSideMenuAdapter.submitList(state.configuration.leftSideMenu)
                rightSideMenuAdapter.submitList(state.configuration.rightSideMenu)
                println("DEBUG OBSERVE: Adapters updated")
                
                // Update button states based on modification status
                okButton.isEnabled = state.isModified
            }
        }
    }
}