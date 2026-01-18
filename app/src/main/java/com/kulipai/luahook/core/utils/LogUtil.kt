package com.kulipai.luahook.core.utils

import android.util.Log


fun Any.d() {
    Log.d("Debug", this.toString())
}

fun Any.e() {
    Log.e("Debug", this.toString())
}