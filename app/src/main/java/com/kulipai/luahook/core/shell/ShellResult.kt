package com.kulipai.luahook.core.shell

sealed class ShellResult {

    data class Success(
        val stdout: String
    ) : ShellResult()

    data class Error(
        val stderr: String,
        val throwable: Throwable? = null
    ) : ShellResult()
}
