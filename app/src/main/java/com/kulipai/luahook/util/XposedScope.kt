package com.kulipai.luahook.util

import android.content.Context
import io.github.libxposed.service.XposedService
import io.github.libxposed.service.XposedServiceHelper
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import top.sacz.xphelper.util.ActivityTools.runOnUiThread

/**
 * api100有的模块作用域管理
 */

object XposedScope {
    private var _service: XposedService? = null
    val service get() = _service

    inline fun withService(onService: (XposedService) -> Unit) {
        service?.let { onService(it) }
    }

    fun requestScope(context: Context, pkg: String) {
        withService {
            if (!it.scope.contains(pkg)) {
                it.requestScope(pkg, object : XposedService.OnScopeEventListener {})
            }
        }
    }


    @OptIn(DelicateCoroutinesApi::class)
    fun requestManyScope(context: Context, pkgList: MutableList<String>, index: Int) {
        var pkg = ""
        withService {
            pkgList -= it.scope
            if (index<pkgList.size){
                pkg = pkgList[index]

                it.requestScope(pkg,
                    object : XposedService.OnScopeEventListener {
//                        override fun onScopeRequestPrompted(packageName: String) {
//                            runOnUiThread {
//
//                            }
//                        }

                        override fun onScopeRequestApproved(packageName: String) {
                            runOnUiThread {
                                GlobalScope.launch {
                                    delay(200)
                                    requestManyScope(context,pkgList,index+1)
                                }
                            }
                        }

//                        override fun onScopeRequestDenied(packageName: String) {
//                            runOnUiThread {
//                            }
//                        }
//
//                        override fun onScopeRequestTimeout(packageName: String) {
//                            runOnUiThread {
//                            }
//                        }
//
//                        override fun onScopeRequestFailed(packageName: String, message: String) {
//                            runOnUiThread {
//
//                            }
//                        }
                    }

                )

            }
        }
    }


    fun removeScope(context: Context, pkg: String) {
        withService {
            if (it.scope.contains(pkg)) {
                it.removeScope(pkg)
            }
        }
    }


    fun init() {
        XposedServiceHelper.registerListener(object : XposedServiceHelper.OnServiceListener {
            override fun onServiceBind(service: XposedService) {
                _service = service
            }

            override fun onServiceDied(service: XposedService) {
                _service = null
            }
        })
    }

}