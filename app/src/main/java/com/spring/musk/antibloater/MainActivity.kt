package com.spring.musk.antibloater

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.KeyEvent
import android.view.MenuItem
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethod
import android.view.inputmethod.InputMethodManager
import android.widget.ListView
import android.widget.ScrollView
import androidx.activity.viewModels
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.edit
import androidx.lifecycle.lifecycleScope
import androidx.preference.PreferenceManager

import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.textfield.TextInputEditText
import com.spring.musk.antibloater.addon.InstalledAppsManager
import com.spring.musk.antibloater.adapter.AppListAdapter
import com.spring.musk.antibloater.databinding.ActivityMainBinding
import com.spring.musk.antibloater.viewmodels.MainActivityViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlin.system.exitProcess

@RequiresApi(Build.VERSION_CODES.O)
class MainActivity : AppCompatActivity() {
    private val viewModel: MainActivityViewModel by viewModels()
    private lateinit var binding: ActivityMainBinding

    private lateinit var pairDialog: MaterialAlertDialogBuilder

    private var lastCommand = ""
    private lateinit var listView: ListView

    private lateinit var installedAppsManager: InstalledAppsManager

    private fun setupUI() {
        pairDialog = MaterialAlertDialogBuilder(this)
            .setTitle(R.string.pair_title)
            .setCancelable(false)
            .setView(R.layout.dialog_pair)
            .setPositiveButton(R.string.pair, null)
            .setNegativeButton(R.string.help, null)
            .setNeutralButton(R.string.skip, null)

        binding.command.setOnKeyListener { _, keyCode, keyEvent ->
            if (keyCode == KeyEvent.KEYCODE_ENTER && keyEvent.action == KeyEvent.ACTION_DOWN) {
                sendCommandToADB()
                return@setOnKeyListener true
            } else {
                return@setOnKeyListener false
            }
        }

        binding.command.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEND) {
                sendCommandToADB()
                return@setOnEditorActionListener true
            } else {
                return@setOnEditorActionListener false
            }
        }
    }

    private fun sendCommandToADB() {
        val text = binding.command.text.toString()
        lastCommand = text
        binding.command.text = null
        lifecycleScope.launch(Dispatchers.IO) {
            viewModel.adb.sendToShellProcess(text)
        }
    }

    private fun setReadyForInput(ready: Boolean) {
        binding.command.isEnabled = ready
        binding.commandContainer.hint =
            if (ready) getString(R.string.command_hint) else getString(R.string.command_hint_waiting)
        binding.progress.visibility = if (ready) View.INVISIBLE else View.VISIBLE
    }

    private fun setupDataListeners() {
        viewModel.outputText.observe(this) { newText ->
            binding.output.text = newText
            binding.outputScrollview.post {
                binding.outputScrollview.fullScroll(ScrollView.FOCUS_DOWN)
                binding.command.requestFocus()
                val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
                imm.showSoftInput(binding.command, InputMethod.SHOW_EXPLICIT)
            }
        }

        viewModel.adb.closed.observe(this) { closed ->
            if (closed == true) {
                val intent = Intent(this, MainActivity::class.java)
                startActivity(intent)
                finishAffinity()
                exitProcess(0)
            }
        }
        viewModel.adb.started.observe(this) { started ->
            setReadyForInput(started == true)
        }
    }

    private fun pairAndStart() {
        if (viewModel.needsToPair()) {
            viewModel.adb.debug("Requesting pairing information")
            askToPair { thisPairSuccess ->
                if (thisPairSuccess) {
                    viewModel.setPairedBefore(true)
                    startADBServer()
                } else {
                    viewModel.adb.debug("Failed to pair! Trying again...")
                    runOnUiThread { pairAndStart() }
                }
            }
        } else {
            startADBServer()
        }
    }

    private fun startADBServer() {
        viewModel.startADBServer { success ->
            runOnUiThread {
                if(success){
                    listView = binding.listView

                    binding.firstPart.visibility = View.GONE
                    binding.secondPart.visibility = View.VISIBLE

                    installedAppsManager = InstalledAppsManager(this@MainActivity)
                    val installedApps = installedAppsManager.getAllInstalledApps()
                    val adapter = AppListAdapter(this@MainActivity, installedApps)
                    listView.adapter = adapter

                    val searchEditText = binding.searchEditText

                    searchEditText.setOnEditorActionListener { _, actionId, _ ->
                        if (actionId == EditorInfo.IME_ACTION_DONE || actionId == EditorInfo.IME_ACTION_SEARCH || actionId == EditorInfo.IME_ACTION_SEND) {
                            val searchQuery = searchEditText.text.toString()
                            (listView.adapter as AppListAdapter).filter.filter(searchQuery)
                            true
                        } else {
                            false
                        }
                    }

                    binding.selectButton.setOnClickListener {
                        val selectedApps = adapter.getSelectedApps()

                        lifecycleScope.launch(Dispatchers.IO) {
                            selectedApps.forEach { appInfo ->
                                val adbCommandString = "pm uninstall -k --user 0 ${appInfo.packageName}"
                                lastCommand = adbCommandString
                                viewModel.adb.sendToShellProcess(adbCommandString)
                            }
                        }
                        binding.firstPart.visibility = View.VISIBLE
                        binding.secondPart.visibility = View.GONE

                    }
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupUI()
        setupDataListeners()
        if (viewModel.isPairing.value != true)
            pairAndStart()

    }

    private fun askToPair(callback: ((Boolean) -> (Unit))? = null) {
        pairDialog
            .create()
            .apply {
                setOnShowListener {
                    getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                        val port = findViewById<TextInputEditText>(R.id.port)!!.text.toString()
                        val code = findViewById<TextInputEditText>(R.id.code)!!.text.toString()
                        dismiss()

                        lifecycleScope.launch(Dispatchers.IO) {
                            viewModel.adb.debug("Trying to pair...")
                            val success = viewModel.adb.pair(port, code)
                            callback?.invoke(success)
                        }
                    }

                    getButton(AlertDialog.BUTTON_NEGATIVE).setOnClickListener {
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(getString(R.string.tutorial_url)))
                        try {
                            startActivity(intent)
                        } catch (e: Exception) {
                            e.printStackTrace()
                            Snackbar.make(
                                binding.output,
                                getString(R.string.snackbar_intent_failed),
                                Snackbar.LENGTH_SHORT
                            )
                                .show()
                        }
                    }

                    getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener {
                        PreferenceManager.getDefaultSharedPreferences(context).edit(true) {
                            putBoolean(getString(R.string.auto_shell_key), false)
                        }
                        dismiss()
                        callback?.invoke(true)
                    }
                }
            }
            .show()
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            else -> super.onOptionsItemSelected(item)
        }
    }

}