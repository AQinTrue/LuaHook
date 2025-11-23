package com.kulipai.luahook.util

import android.content.Context
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData

/**
 * DataRepository搭配ViewModel使用
 */

object DataRepository {
    private val _shellMode = MutableLiveData<ShellManager.Mode>()
    val ShellMode: LiveData<ShellManager.Mode> get() = _shellMode

    fun shellInit(context: Context) {
        ShellManager.init(context) {
            _shellMode.postValue(ShellManager.getMode())
        }
    }
}
