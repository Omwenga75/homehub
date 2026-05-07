package com.example.homehub.admin

import android.content.Context
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.viewpager2.adapter.FragmentStateAdapter
import com.example.homehub.R
import com.example.homehub.databinding.ActivityManageUsersBinding
import com.google.android.material.tabs.TabLayoutMediator
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FirebaseFirestore
import java.util.UUID

class ManageUsersActivity : AppCompatActivity() {

    private lateinit var binding: ActivityManageUsersBinding
    private val db = FirebaseFirestore.getInstance()
    private val tabTitles = arrayOf("Students", "Caretakers", "Suppliers")
    private val roles = arrayOf("student", "caretaker", "supplier")
    private val fragments = mutableListOf<UserListFragment>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityManageUsersBinding.inflate(layoutInflater)
        setContentView(binding.root)

        window.statusBarColor = ContextCompat.getColor(this, R.color.primary_dark)
        androidx.core.view.WindowCompat.getInsetsController(window, window.decorView)
            .isAppearanceLightStatusBars = false

        setupViewPager()
        setupListeners()
        
        // Handle initial tab selection (e.g., from AdminDashboard)
        val initialTabIndex = intent.getIntExtra("tab_index", 0)
        binding.viewPager.setCurrentItem(initialTabIndex, false)

        // Show add user dialog if requested
        if (intent.getBooleanExtra("show_add_dialog", false)) {
            showAddUserDialog()
        }
    }

    private fun setupViewPager() {
        for (role in roles) {
            fragments.add(UserListFragment.newInstance(role))
        }

        binding.viewPager.adapter = object : FragmentStateAdapter(this) {
            override fun getItemCount(): Int = fragments.size
            override fun createFragment(position: Int): Fragment = fragments[position]
        }

        TabLayoutMediator(binding.tabLayout, binding.viewPager) { tab, position ->
            tab.text = tabTitles[position]
        }.attach()
    }

    private fun setupListeners() {
        binding.toolbar.setNavigationOnClickListener { finish() }


        binding.btnAddUser.setOnClickListener {
            showAddUserDialog()
        }
    }

    private fun showAddUserDialog() {
        val bottomSheet = CreateUserBottomSheet()
        bottomSheet.onUserCreated = {
            // Refresh current tab
            fragments[binding.viewPager.currentItem].loadData()
        }
        bottomSheet.show(supportFragmentManager, CreateUserBottomSheet.TAG)
    }

}
