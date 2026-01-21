package com.kulipai.luahook.core.utils

import android.util.Log


fun Any.dd() {
    Log.d("Debug", this.toString())
}

fun Any.ee() {
    Log.e("Debug", this.toString())
}