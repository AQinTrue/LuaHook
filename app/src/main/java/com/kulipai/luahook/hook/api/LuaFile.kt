package com.kulipai.luahook.hook.api

import org.luaj.LuaTable
import org.luaj.LuaValue
import org.luaj.lib.OneArgFunction
import org.luaj.lib.TwoArgFunction
import java.io.File
import java.nio.file.Files
import java.nio.file.Paths
import java.nio.file.StandardCopyOption
import java.nio.file.StandardOpenOption

/**
 * file操作类，读写等各种文件封装
 */

object LuaFile{

    private val modName = "file"
    private var file = LuaTable()

    fun registerTo(env: LuaValue): LuaValue {

        file["isFile"]= object : OneArgFunction() {
            override fun call(path: LuaValue): LuaValue =
                valueOf(File(path.checkjstring()).isFile)
        }

        file["isDir"] =  object : OneArgFunction() {
            override fun call(path: LuaValue): LuaValue =
                valueOf(File(path.checkjstring()).isDirectory)
        }

        file["isExists"] =  object : OneArgFunction() {
            override fun call(path: LuaValue): LuaValue =
                valueOf(File(path.checkjstring()).exists())
        }

        file["read"] =  object : OneArgFunction() {
            override fun call(path: LuaValue): LuaValue = try {
                val content = String(Files.readAllBytes(Paths.get(path.checkjstring())))
                valueOf(content)
            } catch (_: Exception) {
                NIL
            }
        }

        file["readBytes"] =  object : OneArgFunction() {
            override fun call(path: LuaValue): LuaValue = try {
                val bytes = Files.readAllBytes(Paths.get(path.checkjstring()))
                valueOf(String(bytes)) // 可改为 base64 或返回 userdata
            } catch (_: Exception) {
                NIL
            }
        }

        file["write"] =  object : TwoArgFunction() {
            override fun call(path: LuaValue, content: LuaValue): LuaValue = try {
                Files.write(Paths.get(path.checkjstring()), content.checkjstring().toByteArray())
                TRUE
            } catch (_: Exception) {
                FALSE
            }
        }

        file["writeBytes"] =  object : TwoArgFunction() {
            override fun call(path: LuaValue, content: LuaValue): LuaValue = try {
                Files.write(Paths.get(path.checkjstring()), content.checkstring().c)
                TRUE
            } catch (_: Exception) {
                FALSE
            }
        }

        file["append"] =  object : TwoArgFunction() {
            override fun call(path: LuaValue, content: LuaValue): LuaValue = try {
                Files.write(
                    Paths.get(path.checkjstring()),
                    content.checkjstring().toByteArray(),
                    StandardOpenOption.CREATE,
                    StandardOpenOption.APPEND
                )
                TRUE
            } catch (_: Exception) {
                FALSE
            }
        }

        file["appendBytes"] =  object : TwoArgFunction() {
            override fun call(path: LuaValue, content: LuaValue): LuaValue = try {
                Files.write(
                    Paths.get(path.checkjstring()),
                    content.checkstring().c,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.APPEND
                )
                TRUE
            } catch (_: Exception) {
                FALSE
            }
        }

        file["copy"] =  object : TwoArgFunction() {
            override fun call(from: LuaValue, to: LuaValue): LuaValue = try {
                Files.copy(
                    Paths.get(from.checkjstring()),
                    Paths.get(to.checkjstring()),
                    StandardCopyOption.REPLACE_EXISTING
                )
                TRUE
            } catch (_: Exception) {
                FALSE
            }
        }

        file["move"] =  object : TwoArgFunction() {
            override fun call(from: LuaValue, to: LuaValue): LuaValue = try {
                Files.move(
                    Paths.get(from.checkjstring()),
                    Paths.get(to.checkjstring()),
                    StandardCopyOption.REPLACE_EXISTING
                )
                TRUE
            } catch (_: Exception) {
                FALSE
            }
        }

        file["rename"] =  object : TwoArgFunction() {
            override fun call(path: LuaValue, newName: LuaValue): LuaValue {
                val f = File(path.checkjstring())
                return valueOf(f.renameTo(File(f.parentFile, newName.checkjstring())))
            }
        }

        file["delete"] =  object : OneArgFunction() {
            override fun call(path: LuaValue): LuaValue =
                valueOf(File(path.checkjstring()).delete())
        }

        file["getName"] =  object : OneArgFunction() {
            override fun call(path: LuaValue): LuaValue =
                valueOf(File(path.checkjstring()).name)
        }

        file["getSize"] =  object : OneArgFunction() {
            override fun call(path: LuaValue): LuaValue =
                valueOf(File(path.checkjstring()).length().toInt())
        }

        env.set(modName, file)
        return file
    }

}
