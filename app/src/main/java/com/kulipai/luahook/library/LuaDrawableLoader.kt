package com.kulipai.luahook.library
import android.content.res.Resources
import android.graphics.BitmapFactory
import android.graphics.drawable.Drawable
import android.os.Handler
import android.os.Looper
import androidx.core.graphics.drawable.toDrawable
import com.kulipai.luahook.util.d
import okhttp3.Call
import okhttp3.Callback
import okhttp3.ConnectionPool
import okhttp3.Dispatcher
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.luaj.Globals
import org.luaj.LuaError
import org.luaj.LuaValue
import org.luaj.lib.ThreeArgFunction
import org.luaj.lib.TwoArgFunction
import org.luaj.lib.jse.CoerceJavaToLua
import java.io.ByteArrayInputStream
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit


class LuaDrawableLoader(val handler: Handler = Handler(Looper.getMainLooper())) {

    // 同步网络图像
    val loadDrawableSync = object : TwoArgFunction() {
        override fun call(urlValue: LuaValue, cacheValue: LuaValue): LuaValue {
            val url = urlValue.checkjstring()
            val cache = cacheValue.optboolean(true)
//                "开始同步加载: $url, 缓存: $cache".d()
            val drawable = DrawableHelper.loadDrawableSync(url, cache)
            return if (drawable != null) CoerceJavaToLua.coerce(drawable) else NIL
        }
    }

    // 异步网络图像
    val loadDrawableAsync = object : ThreeArgFunction() {
        override fun call(urlValue: LuaValue, cacheValue: LuaValue, callback: LuaValue): LuaValue {
            val url = urlValue.checkjstring()
            val cache = cacheValue.optboolean(true)
//                "开始异步加载: $url, 缓存: $cache".d()
            DrawableHelper.loadDrawableAsync(url, cache) { drawable ->
                handler.post {
                    try {
                        if (!callback.isnil()) {
                            if (drawable != null) {
                                callback.call(CoerceJavaToLua.coerce(drawable))
                            } else {
                                callback.call(NIL)
                            }
                        }
                    } catch (e: Exception) {
                        "异步回调处理异常: ${e.javaClass.name}".d()
                        throw LuaError(e)
                    }
                }
            }
            return NIL

        }
    }

    // 本地文件图像
    val loadDrawableFromFile = object : TwoArgFunction() {
        override fun call(pathValue: LuaValue, cacheValue: LuaValue): LuaValue {
            try {
                val path = pathValue.checkjstring()
                val cache = cacheValue.optboolean(true)
//                "开始加载本地文件: $path, 缓存: $cache".d()
                val drawable = DrawableHelper.loadDrawableFromFile(path, cache)
                return if (drawable != null) CoerceJavaToLua.coerce(drawable) else NIL
            } catch (e: Exception) {
                "loadDrawableFromFile Lua函数异常: ${e.javaClass.name}".d()
                throw LuaError(e)
            }
        }
    }

    // 清除缓存
    val clearDrawableCache = object : TwoArgFunction() {
        override fun call(keyValue: LuaValue, allValue: LuaValue): LuaValue {
            try {
                val key = if (!keyValue.isnil()) keyValue.checkjstring() else null
                val clearAll = allValue.optboolean(false)
                return valueOf(DrawableHelper.clearCache(key, clearAll))
            } catch (e: Exception) {
                "clearDrawableCache Lua函数异常: ${e.javaClass.name}: ${e.message}".d()
                throw LuaError(e)
            }
        }
    }

    fun registerTo(globals: Globals) {
        globals.set("loadDrawableSync", loadDrawableSync)
        globals.set("loadDrawableAsync", loadDrawableAsync)
        globals.set("loadDrawableFromFile", loadDrawableFromFile)
        globals.set("clearDrawableCache", clearDrawableCache)
    }
}

object DrawableHelper {
    // 内存缓存
    private val memoryCache = mutableMapOf<String, Drawable>()

    // 共享的OkHttpClient实例，适当配置超时和连接池
    private val okHttpClient by lazy {
        try {
//            "初始化OkHttpClient".d()
            OkHttpClient.Builder()
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(15, TimeUnit.SECONDS)
                .writeTimeout(15, TimeUnit.SECONDS)
                .connectionPool(ConnectionPool(5, 5, TimeUnit.MINUTES))
                .dispatcher(Dispatcher().apply {
                    maxRequestsPerHost = 10
                    maxRequests = 20
                })
                .build()
        } catch (e: Exception) {
            "OkHttpClient初始化失败: ${e.javaClass.name}: ${e.message}".d()
//            throw LuaError(e)
            // 创建一个基本的OkHttpClient作为后备
            OkHttpClient()
        }
    }

    /**
     * 同步加载网络图片
     */
    fun loadDrawableSync(url: String, cache: Boolean = true): Drawable? {
//        "loadDrawableSync开始执行: $url".d()

        try {
            // 检查URL格式
            if (!url.startsWith("http://") && !url.startsWith("https://")) {
                "URL格式无效: $url".d()
                return null
            }

            // 检查缓存
            if (cache && memoryCache.containsKey(url)) {
//                "使用缓存同步加载: $url".d()
                return memoryCache[url]
            }

//            "同步请求开始: $url".d()

            // 创建请求
            val request = try {
                Request.Builder().url(url).get().build()
            } catch (e: Exception) {
                "创建请求对象失败: ${e.javaClass.name}: ${e.message}".d()
                e.printStackTrace()
                return null
            }

            // 执行请求
            val response = try {
                okHttpClient.newCall(request).execute()
            } catch (e: IOException) {
                "执行同步请求IO异常: ${e.javaClass.name}: ${e.message}".d()
                e.printStackTrace()
                return null
            } catch (e: Exception) {
                "执行同步请求异常: ${e.javaClass.name}: ${e.message}".d()
                e.printStackTrace()
                return null
            }

//            "同步请求响应: code=${response.code}, message=${response.message}".d()

            if (!response.isSuccessful) {
                "同步请求失败, 响应码: ${response.code}".d()
                return null
            }

            // 处理响应体
            val responseBody = response.body

            // 读取字节数据
            val bytes = try {
                responseBody.bytes()
            } catch (e: Exception) {
                "读取响应数据异常: ${e.javaClass.name}: ${e.message}".d()
                e.printStackTrace()
                return null
            }

//            "成功获取图片数据: ${bytes.size} 字节".d()

            if (bytes.isEmpty()) {
                "同步加载失败: 图片数据为空".d()
                return null
            }

            // 解码图片
            val bitmap = try {
                val options = BitmapFactory.Options().apply {
                    inPreferredConfig = android.graphics.Bitmap.Config.ARGB_8888
                }
                BitmapFactory.decodeStream(ByteArrayInputStream(bytes), null, options)
            } catch (e: Exception) {
                "解码Bitmap异常: ${e.javaClass.name}: ${e.message}".d()
                e.printStackTrace()
                return null
            }

            if (bitmap == null) {
                "同步加载失败: 解码Bitmap失败".d()
                return null
            }

//            "Bitmap解码成功: ${bitmap.width}x${bitmap.height}".d()

            // 创建Drawable
            val drawable = try {
                bitmap.toDrawable(Resources.getSystem())
            } catch (e: Exception) {
                "创建BitmapDrawable异常: ${e.javaClass.name}: ${e.message}".d()
                e.printStackTrace()
                bitmap.recycle()
                return null
            }

            // 缓存Drawable
            if (cache) {
                try {
                    memoryCache[url] = drawable
//                    "已缓存Drawable: $url".d()
                } catch (e: Exception) {
                    "缓存Drawable异常: ${e.javaClass.name}: ${e.message}".d()
                    e.printStackTrace()
                    // 缓存失败不影响返回结果
                }
            }

//            "同步加载成功: $url".d()
            return drawable
        } catch (e: Throwable) {
            "同步加载未捕获异常: ${e.javaClass.name}".d()
            throw LuaError(e)
        }
    }

    /**
     * 异步加载网络图片
     */
    fun loadDrawableAsync(url: String, cache: Boolean = true, callback: (Drawable?) -> Unit) {
        try {
            // 检查URL格式
            if (!url.startsWith("http://") && !url.startsWith("https://")) {
                "URL格式无效: $url".d()
                callback(null)
                return
            }

            // 检查缓存
            if (cache && memoryCache.containsKey(url)) {
//                "使用缓存异步加载: $url".d()
                callback(memoryCache[url])
                return
            }

//            "异步请求开始: $url".d()

            // 创建请求
            val request = try {
                Request.Builder().url(url).get().build()
            } catch (e: Exception) {
                "创建异步请求对象失败: ${e.javaClass.name}: ${e.message}".d()
                e.printStackTrace()
                callback(null)
                return
            }

            // 执行异步请求
            okHttpClient.newCall(request).enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    "异步请求失败: ${e.javaClass.name}: ${e.message}".d()
                    e.printStackTrace()
                    callback(null)
                }

                override fun onResponse(call: Call, response: Response) {
                    try {
//                        "异步请求响应: code=${response.code}, message=${response.message}".d()

                        if (!response.isSuccessful) {
                            "异步请求响应码异常: ${response.code}".d()
                            callback(null)
                            return
                        }

                        // 处理响应体
                        val responseBody = response.body

                        // 读取字节数据
                        val bytes = try {
                            responseBody.bytes()
                        } catch (e: Exception) {
                            "读取异步响应数据异常: ${e.javaClass.name}: ${e.message}".d()
                            e.printStackTrace()
                            callback(null)
                            return
                        }

//                        "成功获取异步图片数据: ${bytes.size} 字节".d()

                        if (bytes.isEmpty()) {
                            "异步加载失败: 图片数据为空".d()
                            callback(null)
                            return
                        }

                        // 解码图片
                        val bitmap = try {
                            val options = BitmapFactory.Options().apply {
                                inPreferredConfig = android.graphics.Bitmap.Config.ARGB_8888
                            }
                            BitmapFactory.decodeStream(ByteArrayInputStream(bytes), null, options)
                        } catch (e: Exception) {
                            "解码异步Bitmap异常: ${e.javaClass.name}: ${e.message}".d()
                            e.printStackTrace()
                            callback(null)
                            return
                        }

                        if (bitmap == null) {
                            "异步加载失败: 解码Bitmap失败".d()
                            callback(null)
                            return
                        }

//                        "异步Bitmap解码成功: ${bitmap.width}x${bitmap.height}".d()

                        // 创建Drawable
                        val drawable = try {
                            bitmap.toDrawable(Resources.getSystem())
                        } catch (e: Exception) {
                            "创建异步BitmapDrawable异常: ${e.javaClass.name}: ${e.message}".d()
                            e.printStackTrace()
                            bitmap.recycle()
                            callback(null)
                            return
                        }

                        // 缓存Drawable
                        if (cache) {
                            try {
                                memoryCache[url] = drawable
//                                "已缓存异步Drawable: $url".d()
                            } catch (e: Exception) {
                                "缓存异步Drawable异常: ${e.javaClass.name}: ${e.message}".d()
                                e.printStackTrace()
                                // 缓存失败不影响返回结果
                            }
                        }

//                        "异步加载成功: $url".d()
                        callback(drawable)
                    } catch (e: Throwable) {
                        "异步加载响应处理异常: ${e.javaClass.name}: ${e.message}".d()
                        e.printStackTrace()
                        callback(null)
                    }
                }
            })
        } catch (e: Throwable) {
            "异步加载未捕获异常: ${e.javaClass.name}".d()
            throw LuaError(e)
        }
    }

    /**
     * 从本地文件加载图片
     */
    fun loadDrawableFromFile(path: String, cache: Boolean = true): Drawable? {
        try {
            // 检查缓存
            if (cache && memoryCache.containsKey(path)) {
//                "使用缓存加载本地图片: $path".d()
                return memoryCache[path]
            }

            val file = File(path)
            if (!file.exists()) {
                "文件不存在: $path".d()
                return null
            }

//            "开始解码本地文件: $path, 文件大小: ${file.length()} 字节".d()

            // 解码文件
            val bitmap = try {
                val options = BitmapFactory.Options().apply {
                    inPreferredConfig = android.graphics.Bitmap.Config.ARGB_8888
                }
                BitmapFactory.decodeFile(path, options)
            } catch (e: Exception) {
                "解码本地文件异常: ${e.javaClass.name}: ${e.message}".d()
                e.printStackTrace()
                return null
            }

            if (bitmap == null) {
                "本地图片解码失败: $path".d()
                return null
            }

//            "本地Bitmap解码成功: ${bitmap.width}x${bitmap.height}".d()

            // 创建Drawable
            val drawable = try {
                bitmap.toDrawable(Resources.getSystem())
            } catch (e: Exception) {
                "创建本地BitmapDrawable异常: ${e.javaClass.name}: ${e.message}".d()
                e.printStackTrace()
                bitmap.recycle()
                return null
            }

            // 缓存Drawable
            if (cache) {
                try {
                    memoryCache[path] = drawable
//                    "已缓存本地Drawable: $path".d()
                } catch (e: Exception) {
                    "缓存本地Drawable异常: ${e.javaClass.name}: ${e.message}".d()
                    e.printStackTrace()
                    // 缓存失败不影响返回结果
                }
            }

//            "本地图片加载成功: $path".d()
            return drawable
        } catch (e: Throwable) {
            "本地加载未捕获异常: ${e.javaClass.name}".d()
            throw LuaError(e)
        }
    }

    /**
     * 清除缓存
     * @param key 特定的缓存键，如果为null且clearAll为true则清除所有缓存
     * @param clearAll 是否清除所有缓存
     * @return 返回是否成功清除缓存
     */
    fun clearCache(key: String? = null, clearAll: Boolean = false): Boolean {
        return try {
            if (clearAll || key == null) {
                val size = memoryCache.size
                memoryCache.clear()
                "清除所有缓存, 共 $size 项".d()
                true
            } else if (memoryCache.containsKey(key)) {
                memoryCache.remove(key)
                "清除缓存: $key".d()
                true
            } else {
                "缓存不存在: $key".d()
                false
            }
        } catch (e: Exception) {
            "清除缓存失败: ${e.javaClass.name}".d()
            throw LuaError(e)
        }
    }
}