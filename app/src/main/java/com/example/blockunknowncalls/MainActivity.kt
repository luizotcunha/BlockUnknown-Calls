package com.example.blockunknowncalls

import android.Manifest
import android.app.role.RoleManager
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.os.Bundle
import android.telephony.PhoneNumberUtils
import android.text.format.DateUtils
import android.view.LayoutInflater
import android.view.View
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.ColorRes
import androidx.annotation.DrawableRes
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import com.google.android.material.button.MaterialButton
import com.google.android.material.button.MaterialButtonToggleGroup
import com.google.android.material.card.MaterialCardView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.materialswitch.MaterialSwitch
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.textfield.TextInputEditText

class MainActivity : AppCompatActivity() {

    private lateinit var prefs: SharedPreferences

    private lateinit var root: View
    private lateinit var cardStatus: MaterialCardView
    private lateinit var iconStatus: ImageView
    private lateinit var txtStatusTitle: TextView
    private lateinit var txtStatusSubtitle: TextView
    private lateinit var switchEnable: MaterialSwitch
    private lateinit var cardSetup: MaterialCardView
    private lateinit var txtSetup: TextView
    private lateinit var toggleMode: MaterialButtonToggleGroup
    private lateinit var txtModeHint: TextView
    private lateinit var txtHistoryCount: TextView
    private lateinit var btnClearHistory: MaterialButton
    private lateinit var layoutHistory: LinearLayout
    private lateinit var layoutWhitelist: LinearLayout

    private val permissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
            if (!hasScreeningRole()) requestScreeningRole() else renderSetup()
        }

    private val roleLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { renderSetup() }

    // Atualiza a tela em tempo real quando o serviço bloqueia uma chamada com o app aberto.
    private val prefsListener =
        SharedPreferences.OnSharedPreferenceChangeListener { _, _ -> runOnUiThread { renderLists() } }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        prefs = BlockerStore.prefs(this)

        bindViews()
        applyInsets()
        setupControls()

        if (savedInstanceState == null) startSetup()
    }

    override fun onStart() {
        super.onStart()
        prefs.registerOnSharedPreferenceChangeListener(prefsListener)
    }

    override fun onStop() {
        prefs.unregisterOnSharedPreferenceChangeListener(prefsListener)
        super.onStop()
    }

    override fun onResume() {
        super.onResume()
        renderStatus()
        renderSetup()
        renderLists()
    }

    private fun bindViews() {
        root = findViewById(R.id.root)
        cardStatus = findViewById(R.id.cardStatus)
        iconStatus = findViewById(R.id.iconStatus)
        txtStatusTitle = findViewById(R.id.txtStatusTitle)
        txtStatusSubtitle = findViewById(R.id.txtStatusSubtitle)
        switchEnable = findViewById(R.id.switchEnable)
        cardSetup = findViewById(R.id.cardSetup)
        txtSetup = findViewById(R.id.txtSetup)
        toggleMode = findViewById(R.id.toggleMode)
        txtModeHint = findViewById(R.id.txtModeHint)
        txtHistoryCount = findViewById(R.id.txtHistoryCount)
        btnClearHistory = findViewById(R.id.btnClearHistory)
        layoutHistory = findViewById(R.id.layoutHistory)
        layoutWhitelist = findViewById(R.id.layoutWhitelist)
    }

    private fun applyInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(root) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }
    }

    private fun setupControls() {
        switchEnable.isChecked = prefs.getBoolean(BlockerStore.KEY_ENABLED, true)
        switchEnable.setOnCheckedChangeListener { _, checked ->
            prefs.edit().putBoolean(BlockerStore.KEY_ENABLED, checked).apply()
            renderStatus()
        }
        cardStatus.setOnClickListener { switchEnable.toggle() }

        val reject = prefs.getBoolean(BlockerStore.KEY_AUTO_BLOCK, true)
        toggleMode.check(if (reject) R.id.btnModeReject else R.id.btnModeSilence)
        toggleMode.addOnButtonCheckedListener { _, id, checked ->
            if (!checked) return@addOnButtonCheckedListener
            prefs.edit().putBoolean(BlockerStore.KEY_AUTO_BLOCK, id == R.id.btnModeReject).apply()
            renderStatus()
        }

        findViewById<MaterialButton>(R.id.btnSetup).setOnClickListener { startSetup() }
        findViewById<MaterialButton>(R.id.btnAddWhitelist).setOnClickListener { showAddDialog() }
        btnClearHistory.setOnClickListener {
            MaterialAlertDialogBuilder(this)
                .setTitle(R.string.clear_history_title)
                .setMessage(R.string.clear_history_msg)
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.clear) { _, _ -> BlockerStore.clearHistory(prefs) }
                .show()
        }
    }

    // ---------- Renderização ----------

    private fun renderStatus() {
        val on = switchEnable.isChecked
        val reject = toggleMode.checkedButtonId == R.id.btnModeReject

        cardStatus.setCardBackgroundColor(color(if (on) R.color.accent_container else R.color.neutral_container))
        iconStatus.setImageResource(if (on) R.drawable.ic_shield_check else R.drawable.ic_shield_outline)
        iconStatus.imageTintList = ColorStateList.valueOf(color(if (on) R.color.accent else R.color.text_muted))

        txtStatusTitle.setText(if (on) R.string.status_on else R.string.status_off)
        txtStatusTitle.setTextColor(color(if (on) R.color.on_accent_container else R.color.text))
        txtStatusSubtitle.setText(
            when {
                !on -> R.string.status_off_desc
                reject -> R.string.status_on_reject
                else -> R.string.status_on_silence
            }
        )
        txtStatusSubtitle.setTextColor(color(if (on) R.color.on_accent_container else R.color.text_muted))
        txtStatusSubtitle.alpha = if (on) 0.8f else 1f

        txtModeHint.setText(if (reject) R.string.mode_reject_hint else R.string.mode_silence_hint)
        toggleMode.alpha = if (on) 1f else 0.5f
    }

    private fun renderSetup() {
        val hasContacts = hasPermission(Manifest.permission.READ_CONTACTS)
        val hasRole = hasScreeningRole()
        cardSetup.isVisible = !(hasContacts && hasRole)
        txtSetup.setText(if (!hasRole) R.string.setup_role else R.string.setup_contacts)
    }

    private fun renderLists() {
        renderHistory()
        renderWhitelist()
    }

    private fun renderHistory() {
        layoutHistory.removeAllViews()

        val grouped = BlockerStore.history(prefs)
            .groupBy { it.number }
            .map { (number, entries) -> Triple(number, entries.size, entries.maxOf { it.time }) }
            .sortedByDescending { it.third }

        txtHistoryCount.text = if (grouped.isEmpty()) "" else grouped.size.toString()
        btnClearHistory.isVisible = grouped.isNotEmpty()

        if (grouped.isEmpty()) {
            layoutHistory.addView(emptyView(R.string.history_empty))
            return
        }

        val now = System.currentTimeMillis()
        grouped.forEachIndexed { index, (number, count, last) ->
            val calls = resources.getQuantityString(R.plurals.calls_count, count, count)
            val whenText = if (last > 0) {
                DateUtils.getRelativeTimeSpanString(last, now, DateUtils.MINUTE_IN_MILLIS).toString()
            } else getString(R.string.old_entry)

            val row = numberRow(
                number = number,
                meta = "$calls · $whenText",
                icon = R.drawable.ic_block,
                iconTint = R.color.danger,
                iconBg = R.color.danger_container
            )
            row.findViewById<MaterialButton>(R.id.btnAction).apply {
                isVisible = number.isNotBlank()
                setText(R.string.allow)
                setOnClickListener {
                    BlockerStore.addToWhitelist(prefs, number)
                    BlockerStore.removeFromHistory(prefs, number)
                    snack(getString(R.string.number_allowed, displayNumber(number)))
                }
            }
            if (index > 0) layoutHistory.addView(divider())
            layoutHistory.addView(row)
        }
    }

    private fun renderWhitelist() {
        layoutWhitelist.removeAllViews()
        val list = BlockerStore.whitelist(prefs).sorted()

        if (list.isEmpty()) {
            layoutWhitelist.addView(emptyView(R.string.whitelist_empty))
            return
        }

        list.forEachIndexed { index, number ->
            val row = numberRow(
                number = number,
                meta = getString(R.string.whitelist_meta),
                icon = R.drawable.ic_check,
                iconTint = R.color.accent,
                iconBg = R.color.accent_container
            )
            row.findViewById<ImageButton>(R.id.btnRemove).apply {
                isVisible = true
                setOnClickListener {
                    BlockerStore.removeFromWhitelist(prefs, number)
                    Snackbar.make(root, R.string.number_removed, Snackbar.LENGTH_LONG)
                        .setAction(R.string.undo) { BlockerStore.addToWhitelist(prefs, number) }
                        .show()
                }
            }
            if (index > 0) layoutWhitelist.addView(divider())
            layoutWhitelist.addView(row)
        }
    }

    private fun numberRow(
        number: String,
        meta: String,
        @DrawableRes icon: Int,
        @ColorRes iconTint: Int,
        @ColorRes iconBg: Int
    ): View {
        val row = LayoutInflater.from(this).inflate(R.layout.item_number, layoutHistory, false)
        row.findViewById<ImageView>(R.id.icon).apply {
            setImageResource(icon)
            imageTintList = ColorStateList.valueOf(color(iconTint))
            backgroundTintList = ColorStateList.valueOf(color(iconBg))
        }
        row.findViewById<TextView>(R.id.txtNumber).text = displayNumber(number)
        row.findViewById<TextView>(R.id.txtMeta).text = meta
        return row
    }

    private fun emptyView(textRes: Int) = TextView(this).apply {
        setText(textRes)
        setTextColor(color(R.color.text_muted))
        textSize = 14f
        val pad = dp(20)
        setPadding(pad, pad, pad, pad)
    }

    private fun divider() = View(this).apply {
        setBackgroundColor(color(R.color.outline))
        layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(1)).apply {
            marginStart = dp(66)
        }
    }

    // ---------- Ações ----------

    private fun showAddDialog() {
        val view = LayoutInflater.from(this).inflate(R.layout.dialog_add_number, null)
        val input = view.findViewById<TextInputEditText>(R.id.inputNumber)
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.add_title)
            .setView(view)
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(R.string.add) { _, _ ->
                val number = input.text?.toString()?.trim().orEmpty()
                if (number.any(Char::isDigit)) {
                    BlockerStore.addToWhitelist(prefs, number)
                    snack(getString(R.string.number_allowed, displayNumber(number)))
                }
            }
            .show()
        input.requestFocus()
    }

    private fun startSetup() {
        val missing = buildList {
            add(Manifest.permission.READ_CONTACTS)
            add(Manifest.permission.READ_PHONE_STATE)
            add(Manifest.permission.POST_NOTIFICATIONS)
        }.filterNot(::hasPermission)

        when {
            missing.isNotEmpty() -> permissionLauncher.launch(missing.toTypedArray())
            !hasScreeningRole() -> requestScreeningRole()
        }
    }

    private fun requestScreeningRole() {
        val roleManager = getSystemService(RoleManager::class.java)
        roleLauncher.launch(roleManager.createRequestRoleIntent(RoleManager.ROLE_CALL_SCREENING))
    }

    // ---------- Utilitários ----------

    private fun hasScreeningRole() =
        getSystemService(RoleManager::class.java).isRoleHeld(RoleManager.ROLE_CALL_SCREENING)

    private fun hasPermission(permission: String) =
        ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED

    private fun displayNumber(number: String): String {
        if (number.isBlank()) return getString(R.string.private_number)
        return PhoneNumberUtils.formatNumber(number, "BR") ?: number
    }

    private fun snack(text: String) = Snackbar.make(root, text, Snackbar.LENGTH_SHORT).show()

    private fun color(@ColorRes res: Int) = ContextCompat.getColor(this, res)

    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
}
