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
                
                val icon = leftSideMenuAdapter.getCurrentItem(position)
                
                println("DEBUG LEFT: getMovementFlags for position $position, icon=${icon?.label}, isProtected=${icon?.isProtected}")
                
                // BLOCK protected icons - they cannot be dragged at all
                if (icon?.isProtected == true) {
                    println("DEBUG LEFT: 🔒 BLOCKED - ${icon.label} is protected")
                    return 0
                }
                
                // Allow dragging only non-protected, enabled icons
                return if (icon != null && icon.isEnabled) {
                    println("DEBUG LEFT: ✅ Allowing drag for ${icon.label}")
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
                
                val targetIcon = leftSideMenuAdapter.getCurrentItem(targetPosition)
                
                // BLOCK dropping on protected icons - they cannot be replaced
                if (targetIcon?.isProtected == true) {
                    println("DEBUG LEFT: 🔒 BLOCKED - Cannot drop on protected icon '${targetIcon.label}'")
                    return false
                }
                
                val canDrop = targetIcon == null || targetIcon.isEnabled
                println("DEBUG LEFT: canDropOver to position $targetPosition, targetIcon=${targetIcon?.label}, canDrop=$canDrop")
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
                
                // Check if either position has a protected icon
                val fromIcon = leftSideMenuAdapter.getCurrentItem(fromPosition)
                val toIcon = leftSideMenuAdapter.getCurrentItem(toPosition)
                
                if (fromIcon?.isProtected == true) {
                    println("DEBUG LEFT: 🔒 BLOCKED - Cannot move from position $fromPosition - '${fromIcon.label}' is protected")
                    return false
                }
                if (toIcon?.isProtected == true) {
                    println("DEBUG LEFT: 🔒 BLOCKED - Cannot move to position $toPosition - '${toIcon.label}' is protected")
                    return false
                }
                
                // Swap items in adapter immediately for smooth animation
                leftSideMenuAdapter.swapItemsImmediately(fromPosition, toPosition)
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
                
                val icon = rightSideMenuAdapter.getCurrentItem(position)
                
                println("DEBUG RIGHT: getMovementFlags for position $position, icon=${icon?.label}, isProtected=${icon?.isProtected}")
                
                // BLOCK protected icons - they cannot be dragged at all
                if (icon?.isProtected == true) {
                    println("DEBUG RIGHT: 🔒 BLOCKED - ${icon.label} is protected")
                    return 0
                }
                
                // Allow dragging only non-protected, enabled icons
                return if (icon != null && icon.isEnabled) {
                    println("DEBUG RIGHT: ✅ Allowing drag for ${icon.label}")
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
                
                val targetIcon = rightSideMenuAdapter.getCurrentItem(targetPosition)
                
                // BLOCK dropping on protected icons - they cannot be replaced
                if (targetIcon?.isProtected == true) {
                    println("DEBUG RIGHT: 🔒 BLOCKED - Cannot drop on protected icon '${targetIcon.label}'")
                    return false
                }
                
                val canDrop = targetIcon == null || targetIcon.isEnabled
                println("DEBUG RIGHT: canDropOver to position $targetPosition, targetIcon=${targetIcon?.label}, canDrop=$canDrop")
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
                
                // Check if either position has a protected icon
                val fromIcon = rightSideMenuAdapter.getCurrentItem(fromPosition)
                val toIcon = rightSideMenuAdapter.getCurrentItem(toPosition)
                
                if (fromIcon?.isProtected == true) {
                    println("DEBUG RIGHT: 🔒 BLOCKED - Cannot move from position $fromPosition - '${fromIcon.label}' is protected")
                    return false
                }
                if (toIcon?.isProtected == true) {
                    println("DEBUG RIGHT: 🔒 BLOCKED - Cannot move to position $toPosition - '${toIcon.label}' is protected")
                    return false
                }
                
                // Swap items in adapter immediately for smooth animation
                rightSideMenuAdapter.swapItemsImmediately(fromPosition, toPosition)
                
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
        println("DEBUG DRAG: ========== START DRAG FROM LEFT MENU ==========")
        println("DEBUG DRAG: Position = $position")
        
        // CRITICAL: Get icon from adapter's internal synchronous list
        // This ensures we get the immediately updated data after swaps
        val icon = leftSideMenuAdapter.getCurrentItem(position)
        println("DEBUG DRAG: Icon from Adapter (sync) at position $position = '${icon?.label}', enabled=${icon?.isEnabled}, protected=${icon?.isProtected}")
        
        // Block protected icons from being dragged
        if (icon != null && icon.isProtected) {
            println("DEBUG DRAG: 🔒 BLOCKED - Icon '${icon.label}' is protected and cannot be dragged")
            return
        }
        
        if (icon != null && icon.isEnabled) {
            println("DEBUG DRAG: ✅ Starting drag for '${icon.label}'")
            println("DEBUG DRAG: Icon data - id=${icon.id}, label=${icon.label}, iconRes=${icon.iconRes}")
            startDrag(icon, DragSource.LEFT_SIDE_MENU, position)
        } else {
            println("DEBUG DRAG: ❌ Cannot drag - icon is null or disabled")
        }
    }

    private fun startDragFromRightSideMenu(position: Int) {
        println("DEBUG DRAG: ========== START DRAG FROM RIGHT MENU ==========")
        println("DEBUG DRAG: Position = $position")
        
        // CRITICAL: Get icon from adapter's internal synchronous list
        // This ensures we get the immediately updated data after swaps
        val icon = rightSideMenuAdapter.getCurrentItem(position)
        println("DEBUG DRAG: Icon from Adapter (sync) at position $position = '${icon?.label}', enabled=${icon?.isEnabled}, protected=${icon?.isProtected}")
        
        // Block protected icons from being dragged
        if (icon != null && icon.isProtected) {
            println("DEBUG DRAG: 🔒 BLOCKED - Icon '${icon.label}' is protected and cannot be dragged")
            return
        }
        
        if (icon != null && icon.isEnabled) {
            println("DEBUG DRAG: ✅ Starting drag for '${icon.label}'")
            println("DEBUG DRAG: Icon data - id=${icon.id}, label=${icon.label}, iconRes=${icon.iconRes}")
            startDrag(icon, DragSource.RIGHT_SIDE_MENU, position)
        } else {
            println("DEBUG DRAG: ❌ Cannot drag - icon is null or disabled")
        }
    }

    private fun startDrag(icon: IconItem, source: DragSource, sourceIndex: Int) {
        println("DEBUG DRAG: startDrag for icon='${icon.label}' from $source[$sourceIndex]")
        draggedIcon = icon
        dragSource = source
        dragSourceIndex = sourceIndex
        
        // Create a custom drag shadow that directly shows the current icon
        // This avoids issues with stale view data after swaps
        val dragShadowBuilder = createDragShadowForIcon(icon)
        
        // Use the RecyclerView as the drag source
        val recyclerView = when (source) {
            DragSource.MIDDLE_TRAY -> middleTrayRecyclerView
            DragSource.LEFT_SIDE_MENU -> leftSideMenuRecyclerView
            DragSource.RIGHT_SIDE_MENU -> rightSideMenuRecyclerView
        }
        
        println("DEBUG DRAG: Starting drag with custom shadow for '${icon.label}'")
        recyclerView.startDrag(null, dragShadowBuilder, icon, 0)
    }
    
    private fun createDragShadowForIcon(icon: IconItem): View.DragShadowBuilder {
        // Create a temporary view to use as drag shadow
        val dragView = layoutInflater.inflate(R.layout.item_side_menu_icon, null)
        
        // Set up the view with the icon data
        val iconImageView = dragView.findViewById<android.widget.ImageView>(R.id.iv_icon)
        val labelTextView = dragView.findViewById<android.widget.TextView>(R.id.tv_label)
        
        iconImageView.setImageResource(icon.iconRes)
        labelTextView.text = icon.label
        iconImageView.alpha = if (icon.isEnabled) 1.0f else 0.4f
        labelTextView.alpha = if (icon.isEnabled) 1.0f else 0.4f
        
        // Measure and layout the view
        dragView.measure(
            View.MeasureSpec.makeMeasureSpec(200, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(80, View.MeasureSpec.EXACTLY)
        )
        dragView.layout(0, 0, dragView.measuredWidth, dragView.measuredHeight)
        
        println("DEBUG DRAG: Created custom drag shadow view for '${icon.label}'")
        
        return View.DragShadowBuilder(dragView)
    }

    private fun handleDrop(fromSource: DragSource, fromIndex: Int, toTarget: DragTarget, toIndex: Int) {
        println("DEBUG DROP: handleDrop - from $fromSource[$fromIndex] to $toTarget[$toIndex]")
        
        // Check if the dragged icon is protected (should never happen due to earlier blocks)
        val draggedIcon = this.draggedIcon
        if (draggedIcon != null && draggedIcon.isProtected) {
            println("DEBUG DROP: 🔒 BLOCKED - Dragged icon '${draggedIcon.label}' is protected (this should not happen!)")
            return
        }
        
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
            println("DEBUG DROP: Internal swap detected - performing immediate swap")
            
            // Step 1: Swap in adapter IMMEDIATELY for instant visual update
            when (fromSource) {
                DragSource.LEFT_SIDE_MENU -> {
                    leftSideMenuAdapter.swapItemsImmediately(fromIndex, toIndex)
                }
                DragSource.RIGHT_SIDE_MENU -> {
                    rightSideMenuAdapter.swapItemsImmediately(fromIndex, toIndex)
                }
                else -> {}
            }
            
            // Step 2: Update ViewModel state (will sync back to adapter via observer)
            viewModel.swapIcons(fromSource, fromIndex, toTarget, toIndex)
            
            println("DEBUG DROP: Internal swap complete - adapter and ViewModel updated")
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