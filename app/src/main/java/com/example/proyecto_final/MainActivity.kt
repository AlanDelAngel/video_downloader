package com.example.proyecto_final

import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.EditText
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

class MainActivity : AppCompatActivity() {
    lateinit var username_input : EditText
    lateinit var password_input : EditText
    lateinit var login_btn : Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)

        username_input = findViewById(R.id.username_input)
        password_input = findViewById(R.id.password_input)
        login_btn = findViewById(R.id.login_button)

        login_btn.setOnClickListener {
            val username = username_input.text.toString()
            val password = username_input.text.toString()
            Log.i("Test Credentials", "Username : $username and Password : $password")
        }
    }

}
