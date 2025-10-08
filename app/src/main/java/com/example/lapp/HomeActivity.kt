package com.example.lapp

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.lapp.adapter.MiddleTrayAdapter
import com.example.lapp.adapter.SideMenuAdapter
import com.example.lapp.repository.LauncherRepositoryImpl
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class HomeActivity : AppCompatActivity() {

    private lateinit var repository: LauncherRepositoryImpl
    
    private lateinit var middleTrayRecyclerView: RecyclerView
    private lateinit var leftSideMenuRecyclerView: RecyclerView
    private lateinit var rightSideMenuRecyclerView: RecyclerView
    private lateinit var customizeButton: Button
    
    private lateinit var middleTrayAdapter: MiddleTrayAdapter
    private lateinit var leftSideMenuAdapter: SideMenuAdapter
    private lateinit var rightSideMenuAdapter: SideMenuAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_home)
        
        initializeRepository()
        initializeViews()
        setupRecyclerViews()
        setupCustomizeButton()
        observeRepository()
    }

    private fun initializeRepository() {
        // Get singleton instance
        repository = LauncherRepositoryImpl.getInstance(this)
        println("DEBUG HOME: Repository singleton obtained")
        
        // Initialize default config only on first launch
        if (repository.state.value.configuration.middleTray.isEmpty()) {
            println("DEBUG HOME: Initializing default configuration")
            repository.initializeDefaultConfiguration()
        } else {
            println("DEBUG HOME: Using existing configuration from repository")
        }
    }

    private fun initializeViews() {
        middleTrayRecyclerView = findViewById(R.id.rv_middle_tray)
        leftSideMenuRecyclerView = findViewById(R.id.rv_left_side_menu)
        rightSideMenuRecyclerView = findViewById(R.id.rv_right_side_menu)
        customizeButton = findViewById(R.id.btn_customize)
    }

    private fun setupRecyclerViews() {
        // Middle Tray (5x2 Grid) - Display only, no interaction
        middleTrayAdapter = MiddleTrayAdapter(
            onItemClick = { position -> handleMiddleTrayClick(position) },
            onItemLongClick = { position -> /* No long click on home screen */ }
        )
        middleTrayRecyclerView.layoutManager = GridLayoutManager(this, 5)
        middleTrayRecyclerView.adapter = middleTrayAdapter

        // Left Side Menu - Display only
        leftSideMenuAdapter = SideMenuAdapter(
            onItemClick = { position -> handleLeftSideMenuClick(position) },
            onItemLongClick = { position -> /* No long click on home screen */ }
        )
        leftSideMenuRecyclerView.layoutManager = LinearLayoutManager(this)
        leftSideMenuRecyclerView.adapter = leftSideMenuAdapter

        // Right Side Menu - Display only
        rightSideMenuAdapter = SideMenuAdapter(
            onItemClick = { position -> handleRightSideMenuClick(position) },
            onItemLongClick = { position -> /* No long click on home screen */ }
        )
        rightSideMenuRecyclerView.layoutManager = LinearLayoutManager(this)
        rightSideMenuRecyclerView.adapter = rightSideMenuAdapter
    }

    private fun setupCustomizeButton() {
        customizeButton.setOnClickListener {
            println("DEBUG HOME: Customize button clicked - navigating to MainActivity")
            val intent = Intent(this, MainActivity::class.java)
            startActivity(intent)
        }
    }

    private fun observeRepository() {
        lifecycleScope.launch {
            repository.state.collectLatest { state ->
                println("DEBUG HOME: Repository state updated")
                println("DEBUG HOME: Left menu: ${state.configuration.leftSideMenu.map { it?.label }}")
                println("DEBUG HOME: Right menu: ${state.configuration.rightSideMenu.map { it?.label }}")
                
                // Update adapters to show current configuration
                middleTrayAdapter.submitList(state.configuration.middleTray)
                leftSideMenuAdapter.submitList(state.configuration.leftSideMenu)
                rightSideMenuAdapter.submitList(state.configuration.rightSideMenu)
            }
        }
    }
    
    private fun handleMiddleTrayClick(position: Int) {
        val state = repository.state.value
        val icon = state.configuration.middleTray[position]
        if (icon != null && icon.isEnabled) {
            // Launch the app
            println("DEBUG HOME: Clicked on '${icon.label}' in middle tray")
            android.widget.Toast.makeText(this, "Launching ${icon.label}", android.widget.Toast.LENGTH_SHORT).show()
            // TODO: Launch actual app
        }
    }
    
    private fun handleLeftSideMenuClick(position: Int) {
        val state = repository.state.value
        val icon = state.configuration.leftSideMenu[position]
        if (icon != null && icon.isEnabled) {
            // Launch the app
            println("DEBUG HOME: Clicked on '${icon.label}' in left menu")
            android.widget.Toast.makeText(this, "Launching ${icon.label}", android.widget.Toast.LENGTH_SHORT).show()
            // TODO: Launch actual app
        }
    }
    
    private fun handleRightSideMenuClick(position: Int) {
        val state = repository.state.value
        val icon = state.configuration.rightSideMenu[position]
        if (icon != null && icon.isEnabled) {
            // Launch the app
            println("DEBUG HOME: Clicked on '${icon.label}' in right menu")
            android.widget.Toast.makeText(this, "Launching ${icon.label}", android.widget.Toast.LENGTH_SHORT).show()
            // TODO: Launch actual app
        }
    }
    
    override fun onResume() {
        super.onResume()
        println("DEBUG HOME: onResume - checking for configuration updates")
        // The state observer will automatically update the UI if configuration changed
    }
}

