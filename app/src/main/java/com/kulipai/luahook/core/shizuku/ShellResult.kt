package com.kulipai.luahook.core.shizuku

import android.os.Parcel
import android.os.Parcelable

/**
 * shizuku有关
 */


data class ShellResult(val output: String, val success: Boolean) : Parcelable {
    constructor(parcel: Parcel) : this(
        parcel.readString() ?: "",
        parcel.readByte() != 0.toByte()
    )

    override fun writeToParcel(parcel: Parcel, flags: Int) {
        parcel.writeString(output)
        parcel.writeByte(if (success) 1 else 0)
    }

    override fun describeContents(): Int = 0

    companion object CREATOR : Parcelable.Creator<ShellResult> {
        override fun createFromParcel(parcel: Parcel): ShellResult = ShellResult(parcel)
        override fun newArray(size: Int): Array<ShellResult?> = arrayOfNulls(size)
    }
}