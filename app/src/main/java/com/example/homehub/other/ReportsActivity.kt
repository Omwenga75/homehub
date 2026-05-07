package com.example.homehub.other

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity

class ReportsActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val intent = Intent(this, com.example.homehub.caretaker.ReportsActivity::class.java)
        intent.putExtras(getIntent())
        startActivity(intent)
        finish()
    }
}
