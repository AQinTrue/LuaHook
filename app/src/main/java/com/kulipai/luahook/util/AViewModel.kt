package com.kulipai.luahook.util

import androidx.lifecycle.LiveData
import androidx.lifecycle.ViewModel

/**
 * 一个ViewModel
 */

class AViewModel : ViewModel() {
    val data: LiveData<ShellManager.Mode> = DataRepository.ShellMode
}
