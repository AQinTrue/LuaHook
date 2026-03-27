package com.kulipai.luahook.hook.api

import org.luaj.LuaTable
import org.luaj.LuaError
import org.luaj.LuaValue
import org.luaj.LuaUserdata
import org.luaj.Varargs
import org.luaj.lib.VarArgFunction
import org.luaj.lib.ZeroArgFunction
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

class NativeLib {
    private val isLoaded: Boolean = try {
        System.loadLibrary("luahook")
        true
    } catch (e: Throwable) {
        e.printStackTrace()
        false
    }

    // --- JNI interfaces ---
    external fun hook(addr: Long, returnType: Int, argTypes: IntArray, callbackId: Long): Long
    external fun unhook(handle: Long): Boolean
    external fun allocCStringUtf8(str: String): Long
    external fun alloc(size: Int): Long
    external fun free(ptr: Long)
    // Resolves the primary module base used by symbol lookup.
    external fun findModuleBase(name: String): Long
    // Resolves a /proc/self/maps entry by module expression and permission string.
    external fun findMapBase(moduleExpr: String, perms: String): Long
    external fun findSymbol(module: String, symbol: String): Long
    external fun readPointerChain(ptr: Long, offsets: LongArray): Long
    external fun readMemory(ptr: Long, size: Int): ByteArray?
    external fun writeMemory(ptr: Long, data: ByteArray): Boolean
    // Reads a JNI local ref that is only valid for the current thread envPtr.
    external fun readJStringUtf8(envPtr: Long, jstringRef: Long): String?
    // Reads a native-managed global ref created or retained through this API.
    external fun readManagedJStringUtf8(ref: Long): String?
    // Creates a native-managed global ref from a Java String on the current thread.
    external fun createManagedJStringUtf8(text: String): Long
    // Promotes an existing JNI ref into a native-managed global ref.
    external fun retainManagedRef(envPtr: Long, ref: Long): Long
    external fun releaseManagedRef(ref: Long)
    external fun callFunction(
        addr: Long,
        returnType: Int,
        argTypes: IntArray,
        argBits: LongArray
    ): Long

    data class HookConfig(
        val onEnter: LuaValue?,
        val onLeave: LuaValue?,
        val returnType: Int,
        val argTypes: IntArray
    )

    private data class HookCallbackState(
        val config: HookConfig,
        @Volatile var handle: Long = 0L,
        val inFlightCount: AtomicInteger = AtomicInteger(0),
        @Volatile var retired: Boolean = false,
        @Volatile var lastActivityNanos: Long = System.nanoTime()
    )

    companion object {
        const val TYPE_VOID = 0
        const val TYPE_I32 = 1
        const val TYPE_U32 = 2
        const val TYPE_I64 = 3
        const val TYPE_U64 = 4
        const val TYPE_PTR = 5
        const val TYPE_F32 = 6
        const val TYPE_F64 = 7

        private const val RETIRED_CALLBACK_GRACE_NANOS = 30L * 1_000_000_000L

        private val retiredHookCleanupExecutor = Executors.newSingleThreadScheduledExecutor { runnable ->
            Thread(runnable, "NativeLibRetiredHookCleanup").apply {
                isDaemon = true
            }
        }

        private val nextCallbackId = AtomicLong(1)
        private val hookCallbacks = ConcurrentHashMap<Long, HookCallbackState>()
        private val pendingHookCallbacks = ConcurrentHashMap<Long, HookCallbackState>()
        private val callbackStates = ConcurrentHashMap<Long, HookCallbackState>()
        private val callbackHandles = ConcurrentHashMap<Long, Long>()
        private val handleCallbacks = ConcurrentHashMap<Long, Long>()

        internal fun hookSignatureUsesStackArgs(argTypes: IntArray, is64BitProcess: Boolean): Boolean {
            if (argTypes.isEmpty()) return false

            if (!is64BitProcess) {
                return false
            }

            var gprIndex = 0
            var fprIndex = 0
            for (type in argTypes) {
                if (type == TYPE_F32 || type == TYPE_F64) {
                    if (fprIndex < 8) {
                        fprIndex++
                    } else {
                        return true
                    }
                } else {
                    if (gprIndex < 8) {
                        gprIndex++
                    } else {
                        return true
                    }
                }
            }
            return false
        }
    }

    // --- Callback entry ---
    fun onNativeEnter(callbackId: Long, argBits: LongArray): LongArray? {
        val state = retainHookCallback(callbackId) ?: return null
        val cfg = state.config
        val onEnter = cfg.onEnter ?: return null
        if (cfg.argTypes.size != argBits.size) return null

        val ctx = LuaTable()
        val argsTable = LuaTable()
        for (i in cfg.argTypes.indices) {
            argsTable[i + 1] = decodeNativeValue(cfg.argTypes[i], argBits[i])
        }
        ctx["args"] = argsTable
        ctx["callback_id"] = LuaPointer(callbackId, this)
        val handle = state.handle
        if (handle != 0L) {
            ctx["hook_handle"] = LuaPointer(handle, this)
        }

        try {
            onEnter.call(ctx)
        } catch (e: Exception) {
            e.printStackTrace()
        }

        val newBits = LongArray(argBits.size)
        var changed = false
        for (i in cfg.argTypes.indices) {
            val bits = packInvokeBits(cfg.argTypes[i], argsTable[i + 1])
            newBits[i] = bits
            if (bits != argBits[i]) {
                changed = true
            }
        }
        return if (changed) newBits else null
    }

    fun onNativeLeave(callbackId: Long, retval: Long): Long {
        val state = resolveHookCallbackState(callbackId) ?: return retval
        val cfg = state.config
        try {
            val onLeave = cfg.onLeave ?: return retval

            val arg = decodeNativeValue(cfg.returnType, retval)
            val result = onLeave.call(arg)
            if (!result.isnil() && cfg.returnType != TYPE_VOID) {
                return packInvokeBits(cfg.returnType, result)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            releaseHookCallback(callbackId, state)
        }
        return retval
    }
    // --- LuaPointer ---
    class LuaPointer(val address: Long, private val lib: NativeLib) : LuaUserdata(address) {

        // ?? get ?? "." ??

        override fun get(key: LuaValue): LuaValue {
            val name = key.tojstring()

            // ???? ptr.xxx ???????????????? address
            // ?? Lua ???????? self (??????)
            return when (name) {
                // ptr.read_s32([offset])
                "read_s32" -> object : VarArgFunction() {
                    override fun invoke(args: Varargs): LuaValue {
                        val offset = args.optint(1, 0) // arg1 ?? offset
                        val data = lib.readMemory(address + offset, 4) ?: return ZERO
                        val bb = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN)
                        return valueOf(bb.int.toDouble())
                    }
                }

                // ptr.read_u32([offset])
                "read_u32" -> object : VarArgFunction() {
                    override fun invoke(args: Varargs): LuaValue {
                        val offset = args.optint(1, 0)
                        val data = lib.readMemory(address + offset, 4) ?: return ZERO
                        val bb = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN)
                        return valueOf((bb.int.toLong() and 0xFFFFFFFFL).toDouble())
                    }
                }

                // ptr.read_s64([offset]) -> Pointer (avoid Lua number precision loss)
                "read_s64" -> object : VarArgFunction() {
                    override fun invoke(args: Varargs): LuaValue {
                        val offset = args.optint(1, 0)
                        val data = lib.readMemory(address + offset, 8) ?: return LuaPointer(0, lib)
                        val bb = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN)
                        return LuaPointer(bb.long, lib)
                    }
                }

                // ptr.read_u64([offset]) -> Pointer (avoid Lua number precision loss)
                "read_u64" -> object : VarArgFunction() {
                    override fun invoke(args: Varargs): LuaValue {
                        val offset = args.optint(1, 0)
                        val data = lib.readMemory(address + offset, 8) ?: return LuaPointer(0, lib)
                        val bb = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN)
                        return LuaPointer(bb.long, lib)
                    }
                }

                // ptr.read_ptr([offset]) -> Pointer
                "read_ptr" -> object : VarArgFunction() {
                    override fun invoke(args: Varargs): LuaValue {
                        val offset = args.optint(1, 0)
                        val size = lib.pointerSize()
                        val data = lib.readMemory(address + offset, size) ?: return LuaPointer(0, lib)
                        val bb = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN)
                        val v = if (size == 8) bb.long else (bb.int.toLong() and 0xFFFFFFFFL)
                        return LuaPointer(v, lib)
                    }
                }

                // ptr.read_cstring([max_len])
                "read_cstring" -> object : VarArgFunction() {
                    override fun invoke(args: Varargs): LuaValue {
                        val maxLen = args.optint(1, 512)
                        val data = lib.readMemory(address, maxLen) ?: return NIL
                        var len = 0
                        while (len < data.size && data[len] != 0.toByte()) len++
                        return valueOf(String(data, 0, len))
                    }
                }

                "read_cstring_utf8" -> object : VarArgFunction() {
                    override fun invoke(args: Varargs): LuaValue {
                        val maxLen = args.optint(1, 512)
                        val data = lib.readMemory(address, maxLen) ?: return NIL
                        var len = 0
                        while (len < data.size && data[len] != 0.toByte()) len++
                        return valueOf(String(data, 0, len, Charsets.UTF_8))
                    }
                }

                // ptr.read_u8([offset])
                "read_u8" -> object : VarArgFunction() {
                    override fun invoke(args: Varargs): LuaValue {
                        val offset = args.optint(1, 0)
                        val data = lib.readMemory(address + offset, 1) ?: return ZERO
                        return valueOf((data[0].toInt() and 0xFF).toDouble())
                    }
                }

                // ptr.read_s8([offset])
                "read_s8" -> object : VarArgFunction() {
                    override fun invoke(args: Varargs): LuaValue {
                        val offset = args.optint(1, 0)
                        val data = lib.readMemory(address + offset, 1) ?: return ZERO
                        return valueOf(data[0].toInt().toDouble())
                    }
                }

                // ptr.read_s16([offset])
                "read_s16" -> object : VarArgFunction() {
                    override fun invoke(args: Varargs): LuaValue {
                        val offset = args.optint(1, 0)
                        val data = lib.readMemory(address + offset, 2) ?: return ZERO
                        val bb = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN)
                        return valueOf(bb.short.toDouble())
                    }
                }

                // ptr.read_u16([offset])
                "read_u16" -> object : VarArgFunction() {
                    override fun invoke(args: Varargs): LuaValue {
                        val offset = args.optint(1, 0)
                        val data = lib.readMemory(address + offset, 2) ?: return ZERO
                        val bb = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN)
                        return valueOf((bb.short.toInt() and 0xFFFF).toDouble())
                    }
                }

                // ptr.read_f32([offset])
                "read_f32" -> object : VarArgFunction() {
                    override fun invoke(args: Varargs): LuaValue {
                        val offset = args.optint(1, 0)
                        val data = lib.readMemory(address + offset, 4) ?: return ZERO
                        val bb = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN)
                        return valueOf(bb.float.toDouble())
                    }
                }

                // ptr.read_f64([offset])
                "read_f64" -> object : VarArgFunction() {
                    override fun invoke(args: Varargs): LuaValue {
                        val offset = args.optint(1, 0)
                        val data = lib.readMemory(address + offset, 8) ?: return ZERO
                        val bb = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN)
                        return valueOf(bb.double)
                    }
                }

                // ptr.read_byte_array(size, [offset]) - ????????
                "read_byte_array" -> object : VarArgFunction() {
                    override fun invoke(args: Varargs): LuaValue {
                        val size = args.checkint(1)
                        val offset = args.optint(2, 0)
                        val data = lib.readMemory(address + offset, size) ?: return NIL
                        val table = LuaTable()
                        for (i in data.indices) {
                            table[i + 1] = valueOf((data[i].toInt() and 0xFF).toDouble())
                        }
                        return table
                    }
                }


                // ptr.write_u8(val, [offset])
                "write_u8" -> object : VarArgFunction() {
                    override fun invoke(args: Varargs): LuaValue {
                        val v = args.checkint(1)
                        val offset = args.optint(2, 0)
                        return valueOf(
                            lib.writeMemory(
                                address + offset,
                                byteArrayOf(v.toByte())
                            )
                        )
                    }
                }
                // ptr.write_s8(val, [offset])
                "write_s8" -> object : VarArgFunction() {
                    override fun invoke(args: Varargs): LuaValue {
                        val v = args.checkint(1)
                        val offset = args.optint(2, 0)
                        return valueOf(
                            lib.writeMemory(
                                address + offset,
                                byteArrayOf(v.toByte())
                            )
                        )
                    }
                }

                // ptr.write_s16(val, [offset])
                "write_s16" -> object : VarArgFunction() {
                    override fun invoke(args: Varargs): LuaValue {
                        val v = args.checkint(1)
                        val offset = args.optint(2, 0)
                        val bb = ByteBuffer.allocate(2).order(ByteOrder.LITTLE_ENDIAN)
                            .putShort(v.toShort())
                        return valueOf(lib.writeMemory(address + offset, bb.array()))
                    }
                }
                // ptr.write_u16(val, [offset])
                "write_u16" -> object : VarArgFunction() {
                    override fun invoke(args: Varargs): LuaValue {
                        val v = args.checkint(1) and 0xFFFF
                        val offset = args.optint(2, 0)
                        val bb = ByteBuffer.allocate(2).order(ByteOrder.LITTLE_ENDIAN)
                            .putShort(v.toShort())
                        return valueOf(lib.writeMemory(address + offset, bb.array()))
                    }
                }

                // ptr.write_s32(val, [offset])
                "write_s32" -> object : VarArgFunction() {
                    override fun invoke(args: Varargs): LuaValue {
                        val v = args.checkint(1)
                        val offset = args.optint(2, 0)
                        val bb = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(v)
                        return valueOf(lib.writeMemory(address + offset, bb.array()))
                    }
                }
                // ptr.write_u32(val, [offset])
                "write_u32" -> object : VarArgFunction() {
                    override fun invoke(args: Varargs): LuaValue {
                        val v = args.checklong(1)
                        val offset = args.optint(2, 0)
                        val bb = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN)
                            .putInt((v and 0xFFFFFFFFL).toInt())
                        return valueOf(lib.writeMemory(address + offset, bb.array()))
                    }
                }

                // ptr.write_s64(val, [offset])
                "write_s64" -> object : VarArgFunction() {
                    override fun invoke(args: Varargs): LuaValue {
                        val v = unwrap(args.arg(1))
                        val offset = args.optint(2, 0)
                        val bb = ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN).putLong(v)
                        return valueOf(lib.writeMemory(address + offset, bb.array()))
                    }
                }
                // ptr.write_u64(val, [offset])
                "write_u64" -> object : VarArgFunction() {
                    override fun invoke(args: Varargs): LuaValue {
                        val v = unwrap(args.arg(1))
                        val offset = args.optint(2, 0)
                        val bb = ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN).putLong(v)
                        return valueOf(lib.writeMemory(address + offset, bb.array()))
                    }
                }
                // ptr.write_ptr(val, [offset])
                "write_ptr" -> object : VarArgFunction() {
                    override fun invoke(args: Varargs): LuaValue {
                        val v = unwrap(args.arg(1))
                        val offset = args.optint(2, 0)
                        val size = lib.pointerSize()
                        val bb = ByteBuffer.allocate(size).order(ByteOrder.LITTLE_ENDIAN)
                        if (size == 8) bb.putLong(v) else bb.putInt((v and 0xFFFFFFFFL).toInt())
                        return valueOf(lib.writeMemory(address + offset, bb.array()))
                    }
                }

                // ptr.write_f32(val, [offset])
                "write_f32" -> object : VarArgFunction() {
                    override fun invoke(args: Varargs): LuaValue {
                        val v = args.checkdouble(1).toFloat()
                        val offset = args.optint(2, 0)
                        val bb = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putFloat(v)
                        return valueOf(lib.writeMemory(address + offset, bb.array()))
                    }
                }

                // ptr.write_f64(val, [offset])
                "write_f64" -> object : VarArgFunction() {
                    override fun invoke(args: Varargs): LuaValue {
                        val v = args.checkdouble(1)
                        val offset = args.optint(2, 0)
                        val bb = ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN).putDouble(v)
                        return valueOf(lib.writeMemory(address + offset, bb.array()))
                    }
                }

                // ptr.write_byte_array(table, [offset]) - ??????
                "write_byte_array" -> object : VarArgFunction() {
                    override fun invoke(args: Varargs): LuaValue {
                        val table = args.checktable(1)
                        val offset = args.optint(2, 0)
                        val len = table.length()
                        val bytes = ByteArray(len)
                        for (i in 1..len) {
                            bytes[i - 1] = table[i].checkint().toByte()
                        }
                        return valueOf(lib.writeMemory(address + offset, bytes))
                    }
                }

                // ptr.write_cstring(str)
                "write_cstring" -> object : VarArgFunction() {
                    override fun invoke(args: Varargs): LuaValue {
                        val s = args.checkjstring(1)
                        val bytes = s.toByteArray()
                        val withNull = bytes.copyOf(bytes.size + 1)
                        return valueOf(lib.writeMemory(address, withNull))
                    }
                }

                "write_cstring_utf8" -> object : VarArgFunction() {
                    override fun invoke(args: Varargs): LuaValue {
                        val s = args.checkjstring(1)
                        val bytes = s.toByteArray(Charsets.UTF_8)
                        val withNull = bytes.copyOf(bytes.size + 1)
                        return valueOf(lib.writeMemory(address, withNull))
                    }
                }

                // ptr.is_null() - ????????
                "is_null" -> object : ZeroArgFunction() {
                    override fun call(): LuaValue {
                        return valueOf(address == 0L)
                    }
                }

                // ptr.not_null() - ????????
                "not_null" -> object : ZeroArgFunction() {
                    override fun call(): LuaValue {
                        return valueOf(address != 0L)
                    }
                }

                // ptr.to_hex() - ??????
                "to_hex" -> object : ZeroArgFunction() {
                    override fun call(): LuaValue {
                        return valueOf(java.lang.Long.toHexString(address).uppercase())
                    }
                }

                // ptr.to_int() - ????????????
                "to_int" -> object : ZeroArgFunction() {
                    override fun call(): LuaValue {
                        return valueOf(address.toDouble())
                    }
                }

                // ptr.to_long() - ?????????? long (?? LuaPointer ?????)
                "to_long" -> object : ZeroArgFunction() {
                    override fun call(): LuaValue {
                        return LuaPointer(address, lib)
                    }
                }

                // ptr.hexdump([size])
                "hexdump" -> object : VarArgFunction() {
                    override fun invoke(args: Varargs): LuaValue {
                        val size = args.optint(1, 256)
                        val data = lib.readMemory(address, size)
                            ?: return valueOf(
                                "Cannot read memory at 0x${
                                    java.lang.Long.toHexString(
                                        address
                                    )
                                }"
                            )

                        val sb = StringBuilder()
                        val hexPart = StringBuilder()
                        // ?????????????? String
                        val lineBytes = java.io.ByteArrayOutputStream()

                        for (i in data.indices) {
                            val b = data[i].toInt() and 0xFF

                            // ?? Hex
                            hexPart.append(String.format("%02X ", b))

                            // ??????????
                            lineBytes.write(data[i].toInt())

                            // ? 16 ?????????????
                            if ((i + 1) % 16 == 0 || i == data.size - 1) {
                                // ?? Hex ????? (????????16??)
                                while (hexPart.length < 48) {
                                    hexPart.append("   ")
                                }

                                // ??? UTF-8 ?????
                                val rawString = String(lineBytes.toByteArray(), Charsets.UTF_8)
                                val safeString = StringBuilder()

                                // ???????????
                                for (char in rawString) {
                                    // ????????(??????)??????????
                                    if (!Character.isISOControl(char) || char == ' ') {
                                        safeString.append(char)
                                    } else {
                                        safeString.append('.')
                                    }
                                }

                                // ??
                                val offset = (i / 16) * 16
                                sb.append(
                                    String.format(
                                        "%04X  %s |%s|\n",
                                        offset,
                                        hexPart.toString(),
                                        safeString.toString()
                                    )
                                )

                                // ?????
                                hexPart.setLength(0)
                                lineBytes.reset()
                            }
                        }

                        return valueOf("\n" + sb.toString().trim())
                    }
                }

                // ptr.add(offset)
                "add" -> object : VarArgFunction() {
                    override fun invoke(args: Varargs): LuaValue {
                        val offset = args.checklong(1)
                        return LuaPointer(address + offset, lib)
                    }
                }

                // ptr.sub(offset)
                "sub" -> object : VarArgFunction() {
                    override fun invoke(args: Varargs): LuaValue {
                        val offset = args.checklong(1)
                        return LuaPointer(address - offset, lib)
                    }
                }

                // ptr.set(value) - ??????? (???? args ????)
                "set" -> object : VarArgFunction() {
                    override fun invoke(args: Varargs): LuaValue {
                        // ???? LuaPointer????????? args
                        val newVal = unwrap(args.arg(1))
                        return LuaPointer(newVal, lib)
                    }
                }

                // ptr.deref() - ????? (?????????)
                "deref" -> object : VarArgFunction() {
                    override fun invoke(args: Varargs): LuaValue {
                        val offset = args.optint(1, 0)
                        val size = lib.pointerSize()
                        val data = lib.readMemory(address + offset, size) ?: return LuaPointer(0, lib)
                        val bb = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN)
                        val v = if (size == 8) bb.long else (bb.int.toLong() and 0xFFFFFFFFL)
                        return LuaPointer(v, lib)
                    }
                }

                // ptr.and(mask) - ????
                "and" -> object : VarArgFunction() {
                    override fun invoke(args: Varargs): LuaValue {
                        val mask = unwrap(args.arg(1))
                        return LuaPointer(address and mask, lib)
                    }
                }

                // ptr.or(mask) - ????
                "or" -> object : VarArgFunction() {
                    override fun invoke(args: Varargs): LuaValue {
                        val mask = unwrap(args.arg(1))
                        return LuaPointer(address or mask, lib)
                    }
                }

                // ptr.xor(mask) - ?????
                "xor" -> object : VarArgFunction() {
                    override fun invoke(args: Varargs): LuaValue {
                        val mask = unwrap(args.arg(1))
                        return LuaPointer(address xor mask, lib)
                    }
                }

                // ptr.shl(bits) - ??
                "shl" -> object : VarArgFunction() {
                    override fun invoke(args: Varargs): LuaValue {
                        val bits = args.checkint(1)
                        return LuaPointer(address shl bits, lib)
                    }
                }

                // ptr.shr(bits) - ??
                "shr" -> object : VarArgFunction() {
                    override fun invoke(args: Varargs): LuaValue {
                        val bits = args.checkint(1)
                        return LuaPointer(address shr bits, lib)
                    }
                }

                // ptr.equals(other) - ??????
                "equals" -> object : VarArgFunction() {
                    override fun invoke(args: Varargs): LuaValue {
                        val other = unwrap(args.arg(1))
                        return valueOf(address == other)
                    }
                }

                "unhook" -> object : VarArgFunction() {
                    override fun invoke(args: Varargs): LuaValue {
                        return if (lib.isLoaded && address != 0L && lib.unhook(address)) {
                            TRUE
                        } else {
                            FALSE
                        }
                    }
                }

                else -> super.get(key)
            }
        }

        override fun tostring(): LuaValue {
            return valueOf("Ptr(0x${java.lang.Long.toHexString(address).uppercase()})")
        }

        companion object {
            fun unwrap(v: LuaValue): Long {
                return when {
                    v is LuaPointer -> v.address
                    v.isuserdata() -> {
                        val obj = v.touserdata()
                        obj as? Long ?: 0L
                    }

                    v.isnumber() -> v.tolong()
                    v.isstring() -> try {
                        java.lang.Long.decode(v.tojstring())
                    } catch (_: Exception) {
                        0L
                    }

                    else -> 0L
                }
            }
        }
    }

    // --- Lua API ?? ---
    fun toLuaTable(): LuaTable {
        val t = LuaTable()

        val memory = LuaTable()

        fun memProxy(name: String): VarArgFunction = object : VarArgFunction() {
            override fun invoke(args: Varargs): LuaValue {
                val addr = LuaPointer.unwrap(args.arg(1))
                if (addr == 0L) return if (name.startsWith("write")) FALSE else NIL
                if (name == "read_byte_array") {
                    val size = args.optint(2, 0)
                    if (size <= 0) return NIL
                }
                val fn = LuaPointer(addr, this@NativeLib).get(valueOf(name))
                val n = args.narg()
                val rest = Array(maxOf(0, n - 1)) { i -> args.arg(i + 2) }
                val vargs = LuaValue.varargsOf(rest)
                return fn.invoke(vargs).arg1()
            }
        }

        memory["read_byte_array"] = memProxy("read_byte_array")
        memory["read_u8"] = memProxy("read_u8")
        memory["read_s8"] = memProxy("read_s8")
        memory["read_u16"] = memProxy("read_u16")
        memory["read_s16"] = memProxy("read_s16")
        memory["read_u32"] = memProxy("read_u32")
        memory["read_s32"] = memProxy("read_s32")
        memory["read_u64"] = memProxy("read_u64")
        memory["read_s64"] = memProxy("read_s64")
        memory["read_f32"] = memProxy("read_f32")
        memory["read_f64"] = memProxy("read_f64")
        memory["read_ptr"] = memProxy("read_ptr")
        memory["read_cstring"] = memProxy("read_cstring")
        memory["read_cstring_utf8"] = memProxy("read_cstring_utf8")

        memory["read_len_prefixed_utf8"] = object : VarArgFunction() {
            override fun invoke(args: Varargs): LuaValue {
                val addr = LuaPointer.unwrap(args.arg(1))
                val maxLen = args.optint(2, 512)
                val lenData = readMemory(addr, 1) ?: return NIL
                val len = lenData[0].toInt() and 0xFF
                if (len == 0) return valueOf("")
                val readLen = if (len > maxLen) maxLen else len
                val data = readMemory(addr + 1, readLen) ?: return NIL
                return valueOf(String(data, 0, data.size, Charsets.UTF_8))
            }
        }

        memory["read_utf8_auto"] = object : VarArgFunction() {
            override fun invoke(args: Varargs): LuaValue {
                val addr = LuaPointer.unwrap(args.arg(1))
                val lenData = readMemory(addr, 1) ?: return memory["read_cstring_utf8"].invoke(args).arg1()
                val len = lenData[0].toInt() and 0xFF
                if (len in 1..0x7F) {
                    val data =
                        readMemory(addr + 1, len) ?: return memory["read_cstring_utf8"].invoke(args).arg1()
                    val s = String(data, 0, data.size, Charsets.UTF_8)
                    if (s.length == len) return valueOf(s)
                }
                return memory["read_cstring_utf8"].invoke(args).arg1()
            }
        }

        memory["write_byte_array"] = memProxy("write_byte_array")
        memory["write_u8"] = memProxy("write_u8")
        memory["write_s8"] = memProxy("write_s8")
        memory["write_u16"] = memProxy("write_u16")
        memory["write_s16"] = memProxy("write_s16")
        memory["write_u32"] = memProxy("write_u32")
        memory["write_s32"] = memProxy("write_s32")
        memory["write_u64"] = memProxy("write_u64")
        memory["write_s64"] = memProxy("write_s64")
        memory["write_ptr"] = memProxy("write_ptr")
        memory["write_f32"] = memProxy("write_f32")
        memory["write_f64"] = memProxy("write_f64")
        memory["write_cstring"] = memProxy("write_cstring")
        memory["write_cstring_utf8"] = memProxy("write_cstring_utf8")

        memory["alloc_cstring_utf8"] = object : VarArgFunction() {
            override fun invoke(args: Varargs): LuaValue {
                if (!isLoaded) return LuaPointer(0, this@NativeLib)
                val s = args.checkjstring(1)
                return LuaPointer(allocCStringUtf8(s), this@NativeLib)
            }
        }
        memory["alloc"] = object : VarArgFunction() {
            override fun invoke(args: Varargs): LuaValue {
                if (!isLoaded) return LuaPointer(0, this@NativeLib)
                val size = args.checkint(1)
                if (size <= 0) return LuaPointer(0, this@NativeLib)
                return LuaPointer(alloc(size), this@NativeLib)
            }
        }
        memory["free"] = object : VarArgFunction() {
            override fun invoke(args: Varargs): LuaValue {
                if (isLoaded) free(LuaPointer.unwrap(args.arg(1)))
                return NIL
            }
        }

        memory["write_len_prefixed_utf8"] = object : VarArgFunction() {
            override fun invoke(args: Varargs): LuaValue {
                val addr = LuaPointer.unwrap(args.arg(1))
                val s = args.checkjstring(2)
                val bytes = s.toByteArray(Charsets.UTF_8)
                val len = bytes.size
                val maxLen = args.optint(3, len)
                val writeLen = if (len > maxLen) maxLen else len
                val buf = ByteArray(writeLen + 1)
                buf[0] = (writeLen and 0xFF).toByte()
                System.arraycopy(bytes, 0, buf, 1, writeLen)
                return valueOf(writeMemory(addr, buf))
            }
        }

        t["memory"] = memory

        t["pointer"] = object : VarArgFunction() {
            override fun invoke(args: Varargs): LuaValue {
                val addr = LuaPointer.unwrap(args.arg(1))
                return LuaPointer(addr, this@NativeLib)
            }
        }

        val findModuleBaseLuaFn = object : VarArgFunction() {
            override fun invoke(args: Varargs): LuaValue {
                if (!isLoaded) return LuaPointer(0, this@NativeLib)
                val base = findModuleBase(args.checkjstring(1))
                return LuaPointer(base, this@NativeLib)
            }
        }

        val findMapBaseLuaFn = object : VarArgFunction() {
            override fun invoke(args: Varargs): LuaValue {
                if (!isLoaded) return LuaPointer(0, this@NativeLib)
                val moduleExpr = args.checkjstring(1)
                val perms = args.checkjstring(2)
                return LuaPointer(findMapBase(moduleExpr, perms), this@NativeLib)
            }
        }

        val findSymbolLuaFn = object : VarArgFunction() {
            override fun invoke(args: Varargs): LuaValue {
                if (!isLoaded) return LuaPointer(0, this@NativeLib)
                val module = args.checkjstring(1)
                val symbol = args.checkjstring(2)
                return LuaPointer(findSymbol(module, symbol), this@NativeLib)
            }
        }

        val readPointerChainLuaFn = object : VarArgFunction() {
            override fun invoke(args: Varargs): LuaValue {
                if (!isLoaded) return LuaPointer(0, this@NativeLib)
                val base = LuaPointer.unwrap(args.arg(1))
                val offsets = parseLongArray(args.checktable(2))
                return LuaPointer(readPointerChain(base, offsets), this@NativeLib)
            }
        }

        val callFunctionLuaFn = object : VarArgFunction() {
            override fun invoke(args: Varargs): LuaValue {
                val addr = LuaPointer.unwrap(args.arg(1))
                val returnType = parseCanonicalInvocationReturnType(args.arg(2), 2)
                val argTypes = parseCanonicalInvocationArgTypes(args.checktable(3), 3)
                val valuesTable = args.checktable(4)
                if (valuesTable.length() != argTypes.size) {
                    argerror(4, "arg_values length must match arg_types")
                }
                if (!isLoaded) return decodeInvokeResult(returnType, 0L)
                val argBits = packInvokeArgs(argTypes, valuesTable)
                val retBits = callFunction(addr, returnType, argTypes, argBits)
                return decodeInvokeResult(returnType, retBits)
            }
        }
        val bindFunctionLuaFn = object : VarArgFunction() {
            override fun invoke(args: Varargs): LuaValue {
                val addr = LuaPointer.unwrap(args.arg(1))
                val returnType = parseCanonicalInvocationReturnType(args.arg(2), 2)
                val argTypeSpec = args.arg(3)
                if (!argTypeSpec.isnil() && !argTypeSpec.istable()) {
                    argerror(3, "arg_types must be a table")
                }
                val argTypes = if (argTypeSpec.isnil()) {
                    IntArray(0)
                } else {
                    parseCanonicalInvocationArgTypes(argTypeSpec.checktable(), 3)
                }
                return object : VarArgFunction() {
                    override fun invoke(args: Varargs): LuaValue {
                        if (args.narg() != argTypes.size) {
                            return error("expected ${argTypes.size} args, got ${args.narg()}")
                        }
                        if (!isLoaded) return decodeInvokeResult(returnType, 0L)
                        val argBits = LongArray(argTypes.size) { index ->
                            packInvokeBits(argTypes[index], args.arg(index + 1))
                        }
                        val retBits = callFunction(addr, returnType, argTypes, argBits)
                        return decodeInvokeResult(returnType, retBits)
                    }
                }
            }
        }
        val hookLuaFn = object : VarArgFunction() {
            override fun invoke(args: Varargs): LuaValue {
                if (!isLoaded) return FALSE
                val addr = LuaPointer.unwrap(args.arg(1))
                if (addr == 0L) return FALSE
                val config = args.arg(2).checktable()
                val callbackId = nextCallbackId.getAndIncrement()
                val canonicalReturnType = config["return_type"]
                val returnType = if (!canonicalReturnType.isnil()) {
                    val parsed = parseCanonicalTypeSpec(canonicalReturnType)
                    if (parsed == null) {
                        argerror(2, "return_type must be a valid native type")
                    }
                    parsed!!
                } else {
                    parseHookReturnType(config)
                }
                val canonicalArgTypes = config["arg_types"]
                val argTypes = if (!canonicalArgTypes.isnil()) {
                    if (!canonicalArgTypes.istable()) {
                        argerror(2, "arg_types must be a table")
                    }
                    val parsed = parseTypeListStrict(canonicalArgTypes)
                    if (parsed == null) {
                        argerror(2, "arg_types must contain only valid native types")
                    }
                    parsed!!
                } else {
                    parseHookArgTypes(config)
                }
                if (hookSignatureUsesStackArgs(argTypes, isProcess64Bit())) {
                    argerror(2, "hook signatures with stack-passed args are not supported yet")
                }
                val hookState = HookCallbackState(
                    HookConfig(
                        firstPresent(config, "on_enter", "onEnter").optfunction(null),
                        firstPresent(config, "on_leave", "onLeave").optfunction(null),
                        returnType,
                        argTypes
                    )
                )
                pendingHookCallbacks[callbackId] = hookState

                val handle = hook(addr, returnType, argTypes, callbackId)
                if (handle == 0L) {
                    pendingHookCallbacks.remove(callbackId, hookState)
                    return FALSE
                }

                hookState.handle = handle
                hookCallbacks[handle] = hookState
                callbackStates[callbackId] = hookState
                callbackHandles[callbackId] = handle
                handleCallbacks[handle] = callbackId
                pendingHookCallbacks.remove(callbackId, hookState)
                return LuaPointer(handle, this@NativeLib)
            }
        }

        val unhookLuaFn = object : VarArgFunction() {
            override fun invoke(args: Varargs): LuaValue {
                if (!isLoaded) return FALSE
                val handle = LuaPointer.unwrap(args.arg(1))
                if (handle == 0L) return FALSE
                val removed = unhook(handle)
                if (removed) {
                    hookCallbacks.remove(handle)
                    val callbackId = handleCallbacks.remove(handle)
                    if (callbackId != null) {
                        callbackHandles.remove(callbackId)
                        retireHookCallback(callbackId)
                    }
                    return TRUE
                }
                return FALSE
            }
        }
        val readJStringUtf8LuaFn = object : VarArgFunction() {
            override fun invoke(args: Varargs): LuaValue {
                if (!isLoaded) return NIL
                val envPtr = LuaPointer.unwrap(args.arg(1))
                val jstringRef = LuaPointer.unwrap(args.arg(2))
                if (envPtr == 0L || jstringRef == 0L) return NIL
                val text = readJStringUtf8(envPtr, jstringRef) ?: return NIL
                return valueOf(text)
            }
        }
        val readManagedJStringUtf8LuaFn = object : VarArgFunction() {
            override fun invoke(args: Varargs): LuaValue {
                if (!isLoaded) return NIL
                val ref = LuaPointer.unwrap(args.arg(1))
                if (ref == 0L) return NIL
                val text = readManagedJStringUtf8(ref) ?: return NIL
                return valueOf(text)
            }
        }
        val createManagedJStringUtf8LuaFn = object : VarArgFunction() {
            override fun invoke(args: Varargs): LuaValue {
                if (!isLoaded) return LuaPointer(0, this@NativeLib)
                val text = args.checkjstring(1)
                return LuaPointer(createManagedJStringUtf8(text), this@NativeLib)
            }
        }
        val retainManagedRefLuaFn = object : VarArgFunction() {
            override fun invoke(args: Varargs): LuaValue {
                if (!isLoaded) return LuaPointer(0, this@NativeLib)
                val envPtr = LuaPointer.unwrap(args.arg(1))
                val ref = LuaPointer.unwrap(args.arg(2))
                if (envPtr == 0L || ref == 0L) return LuaPointer(0, this@NativeLib)
                return LuaPointer(retainManagedRef(envPtr, ref), this@NativeLib)
            }
        }
        val releaseManagedRefLuaFn = object : VarArgFunction() {
            override fun invoke(args: Varargs): LuaValue {
                if (!isLoaded) return NIL
                val ref = LuaPointer.unwrap(args.arg(1))
                if (ref != 0L) {
                    releaseManagedRef(ref)
                }
                return NIL
            }
        }

        t["find_module_base"] = findModuleBaseLuaFn
        t["find_map_base"] = findMapBaseLuaFn
        t["find_symbol"] = findSymbolLuaFn
        t["read_pointer_chain"] = readPointerChainLuaFn
        t["call_function"] = callFunctionLuaFn
        t["bind_function"] = bindFunctionLuaFn
        t["hook"] = hookLuaFn
        t["unhook"] = unhookLuaFn
        t["read_jstring_utf8"] = readJStringUtf8LuaFn
        t["read_managed_jstring_utf8"] = readManagedJStringUtf8LuaFn
        t["create_managed_jstring_utf8"] = createManagedJStringUtf8LuaFn
        t["retain_managed_ref"] = retainManagedRefLuaFn
        t["release_managed_ref"] = releaseManagedRefLuaFn
        return t
    }

    private fun resolveHookCallbackState(callbackId: Long): HookCallbackState? {
        callbackStates[callbackId]?.let { return it }
        return pendingHookCallbacks[callbackId]
    }

    private fun retainHookCallback(callbackId: Long): HookCallbackState? {
        val now = System.nanoTime()
        return callbackStates.computeIfPresent(callbackId) { _, state ->
            state.inFlightCount.incrementAndGet()
            state.lastActivityNanos = now
            state
        } ?: pendingHookCallbacks.computeIfPresent(callbackId) { _, state ->
            state.inFlightCount.incrementAndGet()
            state.lastActivityNanos = now
            state
        }
    }

    private fun releaseHookCallback(callbackId: Long, state: HookCallbackState) {
        val remaining = state.inFlightCount.decrementAndGet()
        if (remaining < 0) {
            state.inFlightCount.incrementAndGet()
            return
        }
        state.lastActivityNanos = System.nanoTime()
        if (state.retired && remaining == 0) {
            scheduleRetiredHookCleanup(callbackId, state, RETIRED_CALLBACK_GRACE_NANOS)
        }
    }

    private fun retireHookCallback(callbackId: Long) {
        val state = callbackStates[callbackId] ?: pendingHookCallbacks[callbackId] ?: return
        state.retired = true
        state.lastActivityNanos = System.nanoTime()
        scheduleRetiredHookCleanup(callbackId, state, RETIRED_CALLBACK_GRACE_NANOS)
    }

    private fun scheduleRetiredHookCleanup(callbackId: Long, state: HookCallbackState, delayNanos: Long) {
        retiredHookCleanupExecutor.schedule(
            { tryCleanupRetiredHookCallback(callbackId, state) },
            delayNanos.coerceAtLeast(0L),
            TimeUnit.NANOSECONDS
        )
    }

    private fun tryCleanupRetiredHookCallback(callbackId: Long, state: HookCallbackState) {
        if (!state.retired || state.inFlightCount.get() != 0) {
            return
        }
        val idleNanos = System.nanoTime() - state.lastActivityNanos
        if (idleNanos < RETIRED_CALLBACK_GRACE_NANOS) {
            scheduleRetiredHookCleanup(callbackId, state, RETIRED_CALLBACK_GRACE_NANOS - idleNanos)
            return
        }
        cleanupRetiredHookCallback(callbackId, state)
    }

    private fun cleanupRetiredHookCallback(callbackId: Long, state: HookCallbackState) {
        callbackStates.remove(callbackId, state)
        pendingHookCallbacks.remove(callbackId, state)
    }

    private fun firstPresent(config: LuaValue, vararg keys: String): LuaValue {
        for (key in keys) {
            val value = config[key]
            if (!value.isnil()) {
                return value
            }
        }
        return LuaValue.NIL
    }

    private fun luaArgError(argIndex: Int, message: String): Nothing {
        throw LuaError("bad argument #$argIndex ($message)")
    }

    private fun parseLongArray(value: LuaValue): LongArray {
        if (!value.istable()) {
            return LongArray(0)
        }
        val table = value.checktable()
        val count = table.length()
        return LongArray(count) { index ->
            table.get(index + 1).tolong()
        }
    }

    private fun parseHookReturnType(config: LuaValue): Int {
        val canonicalValue = firstPresent(config, "return_type")
        if (!canonicalValue.isnil()) {
            return parseTypeSpec(canonicalValue, TYPE_I32)
        }
        val legacyValue = firstPresent(config, "ret")
        return parseTypeSpec(legacyValue, TYPE_I32, legacyHookReturnType = true)
    }

    private fun parseCanonicalTypeSpec(value: LuaValue): Int? {
        return when {
            value.isnumber() -> value.toint().takeIf(::isValidNativeType)
            value.isstring() -> parseInvokeTypeOrNull(value.tojstring())
            else -> null
        }
    }

    private fun parseCanonicalArgTypeSpec(value: LuaValue): Int? {
        return parseCanonicalTypeSpec(value)?.takeUnless { it == TYPE_VOID }
    }

    private fun parseTypeListStrict(value: LuaValue): IntArray? {
        if (!value.istable()) {
            return null
        }
        val table = value.checktable()
        val count = table.length()
        val types = IntArray(count)
        for (index in 0 until count) {
            val parsed = parseCanonicalArgTypeSpec(table.get(index + 1)) ?: return null
            types[index] = parsed
        }
        return types
    }

    private fun parseCanonicalInvocationReturnType(value: LuaValue, argIndex: Int): Int {
        if (value.isnil()) {
            return TYPE_VOID
        }
        val parsed = parseCanonicalTypeSpec(value)
        if (parsed == null) {
            luaArgError(argIndex, "return type must be a valid native type")
        }
        return parsed!!
    }

    private fun parseCanonicalInvocationArgTypes(value: LuaValue, argIndex: Int): IntArray {
        val parsed = parseTypeListStrict(value)
        if (parsed == null) {
            luaArgError(argIndex, "arg_types must contain only valid native types")
        }
        return parsed!!
    }
    private fun parseTypeSpec(
        value: LuaValue,
        defaultType: Int,
        legacyHookReturnType: Boolean = false
    ): Int {
        return when {
            value.isnil() -> defaultType
            value.isnumber() -> if (legacyHookReturnType) {
                when (value.toint()) {
                    0 -> TYPE_I32
                    1 -> TYPE_F32
                    3 -> TYPE_F64
                    4 -> TYPE_VOID
                    else -> value.toint()
                }
            } else {
                value.toint()
            }
            value.isstring() -> parseInvokeType(value.tojstring())
            else -> defaultType
        }
    }

    private fun parseHookArgTypes(config: LuaValue): IntArray {
        val canonicalArgs = firstPresent(config, "arg_types")
        if (canonicalArgs.istable()) {
            return parseTypeList(canonicalArgs)
        }
        val legacyArgs = firstPresent(config, "args")
        if (legacyArgs.istable()) {
            return parseTypeList(legacyArgs)
        }
        val argc = if (config["argc"].isnumber()) config["argc"].toint().coerceAtLeast(0) else 0
        return IntArray(argc) { TYPE_PTR }
    }

    private fun parseTypeList(value: LuaValue, defaultType: Int = TYPE_I32): IntArray {
        if (!value.istable()) {
            return IntArray(0)
        }
        val table = value.checktable()
        val count = table.length()
        return IntArray(count) { index ->
            parseTypeSpec(table.get(index + 1), defaultType)
        }
    }

    private fun packInvokeArgs(argTypes: IntArray, valueTable: LuaValue): LongArray {
        if (!valueTable.istable()) {
            return LongArray(argTypes.size)
        }
        val table = valueTable.checktable()
        return LongArray(argTypes.size) { index ->
            packInvokeBits(argTypes[index], table.get(index + 1))
        }
    }

    private fun decodeNativeValue(type: Int, bits: Long): LuaValue {
        return when (type) {
            TYPE_VOID -> LuaValue.NIL
            TYPE_F32 -> LuaValue.valueOf(java.lang.Float.intBitsToFloat((bits and 0xFFFFFFFFL).toInt()).toDouble())
            TYPE_F64 -> LuaValue.valueOf(java.lang.Double.longBitsToDouble(bits))
            TYPE_I32 -> LuaValue.valueOf(bits.toInt().toDouble())
            TYPE_U32 -> LuaValue.valueOf((bits and 0xFFFFFFFFL).toDouble())
            else -> LuaPointer(bits, this)
        }
    }

    private fun parseInvokeType(typeName: String): Int {
        return parseInvokeTypeOrNull(typeName) ?: TYPE_I32
    }

    private fun parseInvokeTypeOrNull(typeName: String): Int? {
        typeName.toIntOrNull()?.let { return it.takeIf(::isValidNativeType) }
        return when (typeName.lowercase()) {
            "void" -> TYPE_VOID
            "int", "i32", "s32" -> TYPE_I32
            "uint", "u32" -> TYPE_U32
            "long", "i64", "s64" -> TYPE_I64
            "ulong", "u64" -> TYPE_U64
            "ptr", "pointer" -> TYPE_PTR
            "float", "f32" -> TYPE_F32
            "double", "f64" -> TYPE_F64
            else -> null
        }
    }

    private fun isValidNativeType(type: Int): Boolean {
        return type in TYPE_VOID..TYPE_F64
    }

    private fun packInvokeBits(type: Int, value: LuaValue): Long {
        return when (type) {
            TYPE_VOID -> 0L
            TYPE_F32 -> java.lang.Float.floatToRawIntBits(value.todouble().toFloat()).toLong() and 0xFFFFFFFFL
            TYPE_F64 -> java.lang.Double.doubleToRawLongBits(value.todouble())
            TYPE_I32, TYPE_U32 -> LuaPointer.unwrap(value) and 0xFFFFFFFFL
            TYPE_I64, TYPE_U64, TYPE_PTR -> LuaPointer.unwrap(value)
            else -> LuaPointer.unwrap(value)
        }
    }

    private fun decodeInvokeResult(returnType: Int, bits: Long): LuaValue {
        return decodeNativeValue(returnType, bits)
    }

    private fun isProcess64Bit(): Boolean {
        return try {
            android.os.Process.is64Bit()
        } catch (_: Throwable) {
            // Fallback: best-effort when is64Bit() is unavailable
            android.os.Build.SUPPORTED_ABIS.any { it.contains("64") }
        }
    }

    private fun pointerSize(): Int = if (isProcess64Bit()) 8 else 4
}


