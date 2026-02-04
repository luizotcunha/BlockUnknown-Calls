package com.example.blockunknowncalls

import android.Manifest
import android.app.role.RoleManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.Gravity
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

class MainActivity : AppCompatActivity() {

    private val REQUEST_CODE_PERMISSIONS = 101

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        checkAndRequestPermissions()
        setupUI()
    }

    private fun setupUI() {
        val sharedPref = getSharedPreferences("Settings", Context.MODE_PRIVATE)
        val switchEnable = findViewById<SwitchCompat>(R.id.switchEnable)
        val switchAutoBlock = findViewById<SwitchCompat>(R.id.switchAutoBlock)
        val layoutHistory = findViewById<LinearLayout>(R.id.layoutHistory)
        val txtWhitelist = findViewById<TextView>(R.id.txtWhitelist)
        val btnClearWhitelist = findViewById<Button>(R.id.btnClearWhitelist)

        // Carregar estados iniciais
        switchEnable.isChecked = sharedPref.getBoolean("enabled", true)
        switchAutoBlock.isChecked = sharedPref.getBoolean("auto_block", true)

        fun refreshListUI() {
            // Whitelist
            val whitelist = sharedPref.getStringSet("whitelist", setOf()) ?: setOf()
            txtWhitelist.text = if (whitelist.isEmpty()) "Nenhum número" else whitelist.joinToString("\n")

            // Histórico
            layoutHistory.removeAllViews()
            val history = sharedPref.getStringSet("history", setOf()) ?: setOf()

            if (history.isEmpty()) {
                val emptyMsg = TextView(this)
                emptyMsg.text = "Sem registros recentes"
                layoutHistory.addView(emptyMsg)
            } else {
                history.forEach { number ->
                    val row = LinearLayout(this)
                    row.orientation = LinearLayout.HORIZONTAL
                    row.gravity = Gravity.CENTER_VERTICAL
                    row.setPadding(0, 10, 0, 10)

                    val tv = TextView(this)
                    tv.text = number
                    tv.layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)

                    val btnAdd = Button(this)
                    btnAdd.text = "+ Whitelist"
                    btnAdd.setOnClickListener {
                        // Salva na Whitelist
                        val currentWhite = sharedPref.getStringSet("whitelist", mutableSetOf())?.toMutableSet() ?: mutableSetOf()
                        currentWhite.add(number)
                        sharedPref.edit().putStringSet("whitelist", currentWhite).apply()

                        // Remove do histórico
                        val currentHist = sharedPref.getStringSet("history", mutableSetOf())?.toMutableSet() ?: mutableSetOf()
                        currentHist.remove(number)
                        sharedPref.edit().putStringSet("history", currentHist).apply()

                        refreshListUI()
                        Toast.makeText(this, "Número liberado!", Toast.LENGTH_SHORT).show()
                    }

                    row.addView(tv)
                    row.addView(btnAdd)
                    layoutHistory.addView(row)
                }
            }
        }

        refreshListUI()

        switchEnable.setOnCheckedChangeListener { _, isChecked -> sharedPref.edit().putBoolean("enabled", isChecked).apply() }
        switchAutoBlock.setOnCheckedChangeListener { _, isChecked -> sharedPref.edit().putBoolean("auto_block", isChecked).apply() }

        btnClearWhitelist.setOnClickListener {
            sharedPref.edit().putStringSet("whitelist", setOf()).apply()
            refreshListUI()
        }
    }

    private fun checkAndRequestPermissions() {
        val permissions = mutableListOf(Manifest.permission.READ_CONTACTS, Manifest.permission.READ_PHONE_STATE)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.POST_NOTIFICATIONS)
        }

        val missing = permissions.filter { ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED }

        if (missing.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, missing.toTypedArray(), REQUEST_CODE_PERMISSIONS)
        } else {
            requestCallScreeningRole()
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        requestCallScreeningRole()
    }

    private fun requestCallScreeningRole() {
        val roleManager = getSystemService(ROLE_SERVICE) as RoleManager
        if (!roleManager.isRoleHeld(RoleManager.ROLE_CALL_SCREENING)) {
            val intent = roleManager.createRequestRoleIntent(RoleManager.ROLE_CALL_SCREENING)
            startActivityForResult(intent, 123)
        }
    }

    override fun onResume() {
        super.onResume()
        setupUI() // Atualiza a lista caso tenha havido bloqueios enquanto o app estava fechado
    }
}