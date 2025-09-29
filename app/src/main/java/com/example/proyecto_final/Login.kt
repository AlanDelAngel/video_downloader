package com.example.proyecto_final

import android.content.ContentValues.TAG
import android.content.Intent
import android.os.Bundle
import android.text.TextUtils
import android.util.Log
import android.widget.Button
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isGone
import androidx.core.view.isInvisible
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth


class Login : AppCompatActivity() {
    lateinit var email_input : EditText
    lateinit var password_input : EditText
    lateinit var login_btn : Button
    lateinit var progress_bar : ProgressBar
    lateinit var go2_register : TextView

    private lateinit var auth: FirebaseAuth

    public override fun onStart() {
        super.onStart()
        // Check if user is signed in (non-null) and update UI accordingly.
        val currentUser = auth.currentUser
        if (currentUser != null) {
            val intent = Intent(applicationContext, MainActivity::class.java)
            startActivity(intent)
            finish()
        }
    }

    public override fun onCreate(savedInstanceState: Bundle?) {
                super.onCreate(savedInstanceState)
                enableEdgeToEdge()
                setContentView(R.layout.activity_login)

                auth = Firebase.auth


                email_input = findViewById(R.id.email_input)
                password_input = findViewById(R.id.password_input)
                login_btn = findViewById(R.id.login_button)
                progress_bar = findViewById(R.id.progress_bar)
                go2_register = findViewById(R.id.register_page)

                go2_register.setOnClickListener {
                    val intent = Intent(applicationContext, Register::class.java)
                    startActivity(intent)
                    finish()
                }

                login_btn.setOnClickListener {
                    progress_bar.isInvisible = false
                    val email = email_input.text.toString()
                    val password = password_input.text.toString()

                    if (TextUtils.isEmpty(email)) {
                        Toast.makeText(this, "Enter email", Toast.LENGTH_SHORT).show()
                        return@setOnClickListener
                    }

                    if (TextUtils.isEmpty(password)) {
                        Toast.makeText(this, "Enter Password", Toast.LENGTH_SHORT).show()
                        return@setOnClickListener
                    }

                    auth.signInWithEmailAndPassword(email, password)
                        .addOnCompleteListener() { task ->
                            progress_bar.isGone=true
                            if (task.isSuccessful) {
                                // Sign in success, update UI with the signed-in user's information
                                Toast.makeText(this, "Account found", Toast.LENGTH_SHORT).show()
                                val intent = Intent(applicationContext, MainActivity::class.java)
                                startActivity(intent)
                                finish()
                            } else {
                                // If sign in fails, display a message to the user.
                                Toast.makeText(
                                    baseContext,
                                    "Authentication failed.",
                                    Toast.LENGTH_SHORT,
                                ).show()
                            }
                        }

                    }
        }
    }