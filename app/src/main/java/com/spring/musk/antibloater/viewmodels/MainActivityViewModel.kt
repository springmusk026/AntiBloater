package com.spring.musk.antibloater.viewmodels

import android.app.Application
import android.os.Build
import androidx.core.content.edit
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import android.preference.PreferenceManager
import androidx.annotation.RequiresApi
import androidx.lifecycle.viewModelScope
import com.spring.musk.antibloater.addon.ADB
import com.spring.musk.antibloater.R

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File

@RequiresApi(Build.VERSION_CODES.O)
class MainActivityViewModel(application: Application) : AndroidViewModel(application) {
    private val _outputText = MutableLiveData<String>()
    val outputText: LiveData<String> = _outputText

    val isPairing = MutableLiveData<Boolean>()

    private val sharedPreferences = PreferenceManager
        .getDefaultSharedPreferences(application.applicationContext)

    val adb = ADB.getInstance(getApplication<Application>().applicationContext)

    init {
        startOutputThread()
    }


    fun startADBServer(callback: ((Boolean) -> (Unit))? = null) {
        viewModelScope.launch(Dispatchers.IO) {
            val success = adb.initServer()
            if (success)
                startShellDeathThread()
            callback?.invoke(success)
        }
    }

    private fun startOutputThread() {
        viewModelScope.launch(Dispatchers.IO) {
            while (isActive) {
                val out = readOutputFile(adb.outputBufferFile)
                val currentText = _outputText.value
                if (out != currentText)
                    _outputText.postValue(out)
                Thread.sleep(ADB.OUTPUT_BUFFER_DELAY_MS)
            }
        }
    }

    private fun startShellDeathThread() {
        viewModelScope.launch(Dispatchers.IO) {
            adb.waitForDeathAndReset()
        }
    }
    fun clearOutputText() {
        adb.outputBufferFile.writeText("")
    }

    fun needsToPair(): Boolean {
        val context = getApplication<Application>().applicationContext

        return !sharedPreferences.getBoolean(context.getString(R.string.paired_key), false) &&
                (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R)
    }

    fun setPairedBefore(value: Boolean) {
        val context = getApplication<Application>().applicationContext
        sharedPreferences.edit {
            putBoolean(context.getString(R.string.paired_key), value)
        }
    }
    private fun readOutputFile(file: File): String {
        val out = ByteArray(adb.getOutputBufferSize())

        synchronized(file) {
            if (!file.exists())
                return ""

            file.inputStream().use {
                val size = it.channel.size()

                if (size <= out.size)
                    return String(it.readBytes())

                val newPos = (it.channel.size() - out.size)
                it.channel.position(newPos)
                it.read(out)
            }
        }

        return String(out)
    }
}