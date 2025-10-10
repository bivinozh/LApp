package com.example.lapp

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.AppCompatImageView
import com.example.lapp.model.IconItem
import com.example.lapp.repository.LauncherRepositoryImpl

class HomeActivity : AppCompatActivity() {

    private lateinit var repository: LauncherRepositoryImpl
    
    // Left tray icons
    private lateinit var leftIcon1: AppCompatImageView
    private lateinit var leftIcon2: AppCompatImageView
    private lateinit var leftIcon3: AppCompatImageView
    private lateinit var leftIcon4: AppCompatImageView
    
    // Right tray icons
    private lateinit var rightIcon1: AppCompatImageView
    private lateinit var rightIcon2: AppCompatImageView
    private lateinit var rightIcon3: AppCompatImageView
    private lateinit var rightIcon4: AppCompatImageView
    
    private lateinit var customizeButton: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_home)
        
        initializeRepository()
        initializeViews()
        setupIconClickListeners()
        setupCustomizeButton()
        loadIconsFromRepository()
    }
    
    override fun onResume() {
        super.onResume()
        // Reload icons when returning from MainActivity
        loadIconsFromRepository()
    }

    private fun initializeRepository() {
        // Get singleton instance
        repository = LauncherRepositoryImpl.getInstance(this)
        
        // Initialize default config only on first launch
        if (repository.state.value.configuration.middleTray.isEmpty()) {
            repository.initializeDefaultConfiguration()
        }
    }

    private fun initializeViews() {
        // Left tray icons
        leftIcon1 = findViewById(R.id.img_left_icon1)
        leftIcon2 = findViewById(R.id.img_left_icon2)
        leftIcon3 = findViewById(R.id.img_left_icon3)
        leftIcon4 = findViewById(R.id.img_left_icon4)
        
        // Right tray icons
        rightIcon1 = findViewById(R.id.img_right_icon1)
        rightIcon2 = findViewById(R.id.img_right_icon2)
        rightIcon3 = findViewById(R.id.img_right_icon3)
        rightIcon4 = findViewById(R.id.img_right_icon4)
        
        customizeButton = findViewById(R.id.btn_customize)
    }

    private fun setupIconClickListeners() {
        // Left tray icons
        leftIcon1.setOnClickListener {
            handleIconClick(repository.state.value.configuration.leftSideMenu.getOrNull(0))
        }
        
        leftIcon2.setOnClickListener {
            handleIconClick(repository.state.value.configuration.leftSideMenu.getOrNull(1))
        }
        
        leftIcon3.setOnClickListener {
            handleIconClick(repository.state.value.configuration.leftSideMenu.getOrNull(2))
        }
        
        leftIcon4.setOnClickListener {
            handleIconClick(repository.state.value.configuration.leftSideMenu.getOrNull(3))
        }
        
        // Right tray icons
        rightIcon1.setOnClickListener {
            handleIconClick(repository.state.value.configuration.rightSideMenu.getOrNull(0))
        }
        
        rightIcon2.setOnClickListener {
            handleIconClick(repository.state.value.configuration.rightSideMenu.getOrNull(1))
        }
        
        rightIcon3.setOnClickListener {
            handleIconClick(repository.state.value.configuration.rightSideMenu.getOrNull(2))
        }
        
        rightIcon4.setOnClickListener {
            handleIconClick(repository.state.value.configuration.rightSideMenu.getOrNull(3))
        }
    }
    
    private fun handleIconClick(iconItem: IconItem?) {
        if (iconItem != null && iconItem.isEnabled) {
            Log.d("HomeActivity", "Icon clicked - Label: ${iconItem.label}, ID: ${iconItem.id}")
            println("DEBUG HOME: Icon clicked - Label: ${iconItem.label}")
            Toast.makeText(this, "Launching ${iconItem.label}", Toast.LENGTH_SHORT).show()
            // TODO: Launch actual app
        } else if (iconItem != null && !iconItem.isEnabled) {
            Log.d("HomeActivity", "Disabled icon clicked - Label: ${iconItem.label}")
            println("DEBUG HOME: Disabled icon clicked - Label: ${iconItem.label}")
        } else {
            Log.d("HomeActivity", "Empty icon slot clicked")
            println("DEBUG HOME: Empty icon slot clicked")
        }
    }

    private fun setupCustomizeButton() {
        customizeButton.setOnClickListener {
            val intent = Intent(this, MainActivity::class.java)
            startActivity(intent)
        }
    }
    
    private fun loadIconsFromRepository() {
        val state = repository.state.value
        val leftMenu = state.configuration.leftSideMenu
        val rightMenu = state.configuration.rightSideMenu
        
        // Update left tray icons (4 icons)
        updateIcon(leftIcon1, leftMenu.getOrNull(0))
        updateIcon(leftIcon2, leftMenu.getOrNull(1))
        updateIcon(leftIcon3, leftMenu.getOrNull(2))
        updateIcon(leftIcon4, leftMenu.getOrNull(3))
        
        // Update right tray icons (4 icons)
        updateIcon(rightIcon1, rightMenu.getOrNull(0))
        updateIcon(rightIcon2, rightMenu.getOrNull(1))
        updateIcon(rightIcon3, rightMenu.getOrNull(2))
        updateIcon(rightIcon4, rightMenu.getOrNull(3))
    }
    
    private fun updateIcon(imageView: AppCompatImageView, iconItem: IconItem?) {
        if (iconItem != null) {
            imageView.setImageResource(iconItem.iconRes)
            imageView.visibility = View.VISIBLE
            imageView.alpha = if (iconItem.isEnabled) 1.0f else 0.4f
        } else {
            imageView.visibility = View.INVISIBLE
        }
    }
}

