package com.androlua;

import android.content.Context;
import android.content.ContextWrapper;
import android.util.Log;

import com.kulipai.luahook.MyApplication;

import org.luaj.LuaValue;

import java.io.File;
import java.lang.reflect.Field;

import dx.proxy.Enhancer;
import dx.proxy.EnhancerInterface;
import dx.proxy.MethodFilter;
import dx.proxy.MethodInterceptor;

/**
 * Created by nirenr on 2018/12/19.
 * Fixed for Android 10+ SecurityException using ContextWrapper Hook by Gemini
 */

public final class LuaEnhancer {
    private static final String TAG = "LuaEnhancerDebug";
    private final Enhancer mEnhancer;

    // =======================================================
    // 🎭 Context 欺骗器：拦截外部存储请求，重定向到内部存储
    // =======================================================
    private static class ForceInternalContext extends ContextWrapper {
        public ForceInternalContext(Context base) {
            super(base);
        }

        @Override
        public File getExternalFilesDir(String type) {
            // 拦截！无论请求什么，都返回内部私有目录
            return super.getDir("dexfiles", MODE_PRIVATE);
        }

        @Override
        public File getExternalCacheDir() {
            // 拦截！
            return super.getDir("dexcache", MODE_PRIVATE);
        }

        // 有些老版本库可能直接调用这个
        @Override
        public File getFilesDir() {
            return super.getDir("files", MODE_PRIVATE);
        }
    }
    // =======================================================

    public LuaEnhancer(String cls) throws ClassNotFoundException {
        this(Class.forName(cls));
    }

    public LuaEnhancer(Class<?> cls) {
        this(MyApplication.getInstance(), cls);
    }

    public LuaEnhancer(Context context, String cls) throws ClassNotFoundException {
        this(context, Class.forName(cls));
    }

    public LuaEnhancer(Context context, Class<?> cls) {
        if (context == null) throw new NullPointerException("Context is null");
        if (cls == null) throw new NullPointerException("Class is null");

        // 1. 设置系统属性 (作为双重保险)
        try {
            File dexDir = context.getDir("dexfiles", Context.MODE_PRIVATE);
            System.setProperty("dexmaker.dexcache", dexDir.getAbsolutePath());
//            Log.d(TAG, "🛠️ [1/2] System Property 设置为: " + dexDir.getAbsolutePath());
        } catch (Exception e) {
            e.printStackTrace();
        }

        // 2. 创建一个“被欺骗”的 Context
        Context hookedContext = new ForceInternalContext(context);
//        Log.d(TAG, "🛠️ [2/2] 启用 Context 劫持，强制重定向 SD 卡写入请求");

        // 3. 将这个“假” Context 传给 Enhancer
        // Enhancer 以为它是真的 Activity/Context，实际上它的所有路径请求都被我们篡改了
        mEnhancer = new Enhancer(hookedContext);
        mEnhancer.setSuperclass(cls);
    }

    public void setInterceptor(EnhancerInterface obj, MethodInterceptor interceptor) {
        obj.setMethodInterceptor_Enhancer(interceptor);
    }

    public static void setInterceptor(Class<?> obj, MethodInterceptor interceptor) {
        try {
            Field field = obj.getDeclaredField("methodInterceptor");
            field.setAccessible(true);
            field.set(obj, interceptor);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public Class<?> create() {
        try {
            return mEnhancer.create();
        } catch (Exception e) {
//            Log.e(TAG, "create() Error", e);
        }
        return null;
    }

    public Class<?> create(MethodFilter filer) {
        try {
            mEnhancer.setMethodFilter(filer);
            return mEnhancer.create();
        } catch (Exception e) {
//            Log.e(TAG, "create(Filter) Error", e);
        }
        return null;
    }

    public Class<?> create(LuaValue arg) {
        MethodFilter filter = (method, name) -> !arg.get(name).isnil();
        try {
            mEnhancer.setMethodFilter(filter);
            // 此时调用 create，内部的 DexMaker 会调用 hookedContext.getExternalFilesDir()
            // 然后被我们重定向到内部存储，从而绕过 SecurityException
            Class<?> cls = mEnhancer.create();
            setInterceptor(cls, new LuaMethodInterceptor(arg));
            return cls;
        } catch (Exception e) {
//            Log.e(TAG, "❌ create(LuaValue) 崩溃", e);
            e.printStackTrace();
        }
        return null;
    }
}