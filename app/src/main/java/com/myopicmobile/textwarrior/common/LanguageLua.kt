/*
 * Copyright (c) 2013 Tah Wei Hoon.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Apache License Version 2.0,
 * with full text available at http://www.apache.org/licenses/LICENSE-2.0.html
 *
 * This software is provided "as is". Use at your own risk.
 */
package com.myopicmobile.textwarrior.common

/**
 * Singleton class containing the symbols and operators of the Javascript language
 */
class LanguageLua private constructor() : Language() {
    init {
        super.setOperators(LUA_OPERATORS)
        super.setKeywords(Companion.keywords)

        super.setNames(Companion.names)
        super.setNames(luaHookKey)
        super.addBasePackage(
            "io",
            package_io.split("\\|".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
        )
        super.addBasePackage(
            "string",
            package_string.split("\\|".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
        )
        super.addBasePackage(
            "luajava",
            package_luajava.split("\\|".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
        )
        super.addBasePackage(
            "os",
            package_os.split("\\|".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
        )
        super.addBasePackage(
            "table",
            package_table.split("\\|".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
        )
        super.addBasePackage(
            "math",
            package_math.split("\\|".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
        )
        super.addBasePackage(
            "utf8",
            package_utf8.split("\\|".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
        )
        //		super.addBasePackage("coroutine",package_coroutine.split("\\|"));
//		super.addBasePackage("package",package_package.split("\\|"));
        super.addBasePackage(
            "debug",
            package_debug.split("\\|".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
        )
        super.addBasePackage(
            "lpparam",
            package_lpparam.split("\\|".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
        )
        super.addBasePackage(
            "suparam",
            package_suparam.split("\\|".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
        )
        super.addBasePackage(
            "dexkitbridge",
            package_DexKitBridge.split("\\|".toRegex()).dropLastWhile { it.isEmpty() }
                .toTypedArray()
        )
        super.addBasePackage(
            "dexfinder",
            package_DexFinder.split("\\|".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
        )
        super.addBasePackage(
            "http",
            package_http.split("\\|".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
        )
        super.addBasePackage(
            "resources",
            package_res.split("\\|".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
        )
        super.addBasePackage(
            "file",
            package_file.split("\\|".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
        )
        super.addBasePackage(
            "json",
            package_json.split("\\|".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
        )

        super.addBasePackage(
            "sp",
            package_sp.split("\\|".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
        )

        super.addBasePackage(
            "xphelper",
            package_XpHelper.split("\\|".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
        )


        super.addBasePackage(
            "xsp",
            package_xsp.split("\\|".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
        )

        super.addBasePackage(
            "native",
            package_native.split("\\|".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
        )


    }

    /**
     * Whether the word after c is a token
     */
    fun isWordStart2(c: Char): Boolean {
        return (c == '.')
    }

    override fun isLineAStart(c: Char): Boolean {
        return false
    }

    /**
     * Whether c0c1L is a token, where L is a sequence of characters until the end of the line
     */
    override fun isLineStart(c0: Char, c1: Char): Boolean {
        return (c0 == '-' && c1 == '-')
    }

    /**
     * Whether c0c1 signifies the start of a multi-line token
     */
    override fun isMultilineStartDelimiter(c0: Char, c1: Char): Boolean {
        return (c0 == '[' && c1 == '[')
    }

    /**
     * Whether c0c1 signifies the end of a multi-line token
     */
    override fun isMultilineEndDelimiter(c0: Char, c1: Char): Boolean {
        return (c0 == ']' && c1 == ']')
    }

    companion object {
        private var _theOne: Language? = null

        //private final static String functionTarget   = "_ENV|_G|_VERSION|assert|collectgarbage|coroutine|create|isyieldable|resume|running|status|wrap|yield|debug|gethook|getinfo|getlocal|getmetatable|getregistry|getupvalue|getuservalue|sethook|setlocal|setmetatable|setupvalue|setuservalue|traceback|upvalueid|upvaluejoin|dofile|error|getfenv|getmetatable|io|close|flush|input|lines|open|output|popen|read|stderr|stdin|stdout|tmpfile|type|write|ipairs|load|loadfile|loadstring|luajava|bindClass|clear|coding|createArray|createProxy|instanceof|loadLib|loaded|luapath|new|newInstance|package|math|abs|acos|asin|atan|atan2|ceil|cos|cosh|deg|exp|floor|fmod|frexp|huge|ldexp|log|log10|max|maxinteger|min|mininteger|modf|pi|pow|rad|random|randomseed|sin|sinh|sqrt|tan|tanh|tointeger|type|ult|module|next|os|clock|date|difftime|execute|exit|getenv|remove|rename|setlocale|time|tmpname|package|config|cpath|loaded|loaders|loadlib|path|preload|searchers|searchpath|seeall|pairs|pcall|print|rawequal|rawget|rawlen|rawset|require|select|setfenv|setmetatable|string|byte|char|dump|find|format|gfind|gmatch|gsub|len|lower|match|pack|packsize|rep|reverse|sub|unpack|upper|table|concat|foreach|foreachi|insert|maxn|move|pack|remove|sort|unpack|tonumber|tostring|type|unpack|char|charpattern|utf8|codepoint|codes|len|offset|xpcall";
        //private final static String functionTarget1sx   = "_ENV|_G|_VERSION|assert|collectgarbage|coroutine.create|coroutine.isyieldable|coroutine.resume|coroutine.running|coroutine.status|coroutine.wrap|coroutine.yield|debug.debug|debug.gethook|debug.getinfo|debug.getlocal|debug.getmetatable|debug.getregistry|debug.getupvalue|debug.getuservalue|debug.sethook|debug.setlocal|debug.setmetatable|debug.setupvalue|debug.setuservalue|debug.traceback|debug.upvalueid|debug.upvaluejoin|dofile|error|getfenv|getmetatable|io.close|io.flush|io.input|io.lines|io.open|io.output|io.popen|io.read|io.stderr|io.stdin|io.stdout|io.tmpfile|io.type|io.write|ipairs|load|loadfile|loadstring|luajava.bindClass|luajava.clear|luajava.coding|luajava.createArray|luajava.createProxy|luajava.instanceof|luajava.loadLib|luajava.loaded|luajava.luapath|luajava.new|luajava.newInstance|luajava.package|math.abs|math.acos|math.asin|math.atan|math.atan2|math.ceil|math.cos|math.cosh|math.deg|math.exp|math.floor|math.fmod|math.frexp|math.huge|math.ldexp|math.log|math.log10|math.max|math.maxinteger|math.min|math.mininteger|math.modf|math.pi|math.pow|math.rad|math.random|math.randomseed|math.sin|math.sinh|math.sqrt|math.tan|math.tanh|math.tointeger|math.type|math.ult|module|next|os.clock|os.date|os.difftime|os.execute|os.exit|os.getenv|os.remove|os.rename|os.setlocale|os.time|os.tmpname|package.config|package.cpath|package.loaded|package.loaders|package.loadlib|package.path|package.preload|package.searchers|package.searchpath|package.seeall|pairs|pcall|print|rawequal|rawget|rawlen|rawset|require|select|setfenv|setmetatable|string.byte|string.char|string.dump|string.find|string.format|string.gfind|string.gmatch|string.gsub|string.len|string.lower|string.match|string.pack|string.packsize|string.rep|string.reverse|string.sub|string.unpack|string.upper|table.concat|table.foreach|table.foreachi|table.insert|table.maxn|table.move|table.pack|table.remove|table.sort|table.unpack|tonumber|tostring|type|unpack|utf8.char|utf8.charpattern|utf8.codepoint|utf8.codes|utf8.len|utf8.offset|xpcall";
        private const val keywordTarget =
            "and|break|case|catch|continue|default|defer|do|else|elseif|end|false|finally|for|function|goto|if|in|lambda|local|nil|not|or|repeat|return|switch|then|true|try|until|when|while|is"

        //	private final static String globalTarget="self|__add|__band|__bnot|__bor|__bxor|__call|__close|__concat|__div|__eq|__gc|__idiv|__index|__le|__len|__lt|__mod|__mul|__newindex|__pow|__shl|__shr|__sub|__tostring|__unm|_ENV|_G|assert|collectgarbage|dofile|error|getfenv|getmetatable|ipairs|load|loadfile|loadstring|module|next|pairs|pcall|print|rawequal|rawget|rawlen|rawset|require|select|self|setfenv|setmetatable|tointeger|tonumber|tostring|type|unpack|xpcall";
        private const val globalTarget =
            "_ENV|_G|assert|ipairs|load|next|pairs|pcall|print|tointeger|tonumber|tostring|type"

        //	private final static String packageName="coroutine|debug|io|luajava|math|os|package|string|table|utf8";
        private const val packageName = "debug|io|luajava|math|os|package|string|table|utf8"

        //	private final static String package_coroutine = "create|isyieldable|resume|running|status|wrap|yield";
        private const val package_debug =
            "debug|gethook|getinfo|getlocal|getmetatable|getregistry|getupvalue|getuservalue|sethook|setlocal|setmetatable|setupvalue|setuservalue|traceback|upvalueid|upvaluejoin"
        private const val package_io =
            "close|flush|info|input|isdir|lines|ls|mkdir|open|output|popen|read|readall|stderr|stdin|stdout|tmpfile|type|write"
        private const val package_luajava =
            "astable|bindClass|clear|coding|createArray|createProxy|getContext|instanceof|loadLib|loaded|luapath|new|newArray|newInstance|override|package|tostring"
        private const val package_math =
            "abs|acos|asin|atan|atan2|ceil|cos|cosh|deg|exp|floor|fmod|frexp|huge|ldexp|log|log10|max|maxinteger|min|mininteger|modf|pi|pow|rad|random|randomseed|sin|sinh|sqrt|tan|tanh|tointeger|type|ult"
        private const val package_os =
            "clock|date|difftime|execute|exit|getenv|remove|rename|setlocale|time|tmpname"

        //	private final static String package_package = "config|cpath|loaded|loaders|loadlib|path|preload|searchers|searchpath|seeall";
        private const val package_string =
            "byte|char|dump|find|format|gfind|gmatch|gsub|len|lower|match|rep|reverse|sub|upper"
        private const val package_table =
            "clear|clone|concat|const|find|foreach|foreachi|gfind|insert|maxn|move|pack|remove|size|sort|unpack"
        private const val package_utf8 =
            "byte|char|charpattern|charpos|codepoint|codes|escape|find|fold|gfind|gmatch|gsub|insert|len|lower|match|ncasecmp|next|offset|remove|reverse|sub|title|upper|width|widthindex"


        private const val package_lpparam =
            "packageName|classLoader|appInfo|isFirstApplication|processName"
        private const val package_suparam =
            "startsSystemServer|modulePath"
        private const val package_http = "get|post|upload|delete|put|postJson|head|download"
        private const val package_sp = "clear|getAll|remove|contains|get|set"
        private const val package_XpHelper =
            "initContext|initZygote|setConfigPath|setConfigPassword|injectResourcesToContext|moduleApkPath"
        private const val package_xsp = "getAll|contains|get|reload"
        private const val package_DexKitBridge =
            "close|initFullCache|setThreadNum|getDexNum|exportDexFile|batchFindClassUsingStrings|batchFindMethodUsingStrings|findClass|findMethod|findField|getClassData|getMethodData|getFieldData|getCallMethods|getInvokeMethods|create"
        private const val package_json = "encode|decode"
        private const val package_DexFinder =
            "setAutoCloseTime|create|getDexKitBridge|findMethod|findField|findClass|clearCache|resetTimer|close"
        private const val package_res = "getRConstants|getColor|getString|getResourceId|getDrawable"
        private const val package_file =
            "isFile|isDir|isExists|read|readBytes|write|writeBytes|append|appendBytes|copy|move|rename|delete|getName|getSize"

        private const val package_native =
            "write|read|get_module_base|module_base|sleep|resolve_symbol"


        //	private final static String extFunctionTarget="activity|call|compile|dump|each|enum|import|loadbitmap|loadlayout|loadmenu|service|set|task|thread|timer";
        private const val extFunctionTarget = "import"
        private val functionTarget: String =
            globalTarget + "|" + extFunctionTarget + "|" + packageName
        private val keywords: Array<String?> =
            keywordTarget.split("\\|".toRegex()).dropLastWhile { it.isEmpty() }
                .toTypedArray()

        val luaHookKey = arrayOf(
            "hook",
            "replace",
//            "Xposed",
            "tostring",
            "import",
            "log",
            "setField",
            "getField",
            "invoke",
            "file",
            "http",
            "sp",
            "xsp",
            "lpparam",
            "suparam",
            "json",
            "imports",
            "printStackTrace",
//            "new",
//            "newInstance",
//            "getConstructor",
            "clearDrawableCache",
            "loadDrawableFromFile",
            "loadDrawableAsync",
            "loadDrawableSync",
            "resources",
            "hookcotr",
            "hookctor",
            "injectActivity",
            "XposedBridge",
            "XposedHelpers",
//            "createProxy",
            "DexKitBridge",
            "findClass",
            "DexFinder",
            "hookAll",
            "XpHelper",
            "arrayOf",
            "Task",
            "callMethod",
            "getStaticField",
            "setStaticField",
            "native"
        )

        val names: Array<String?> =
            functionTarget.split("\\|".toRegex()).dropLastWhile { it.isEmpty() }
                .toTypedArray()

        private
        val LUA_OPERATORS = charArrayOf(
            '(', ')', '{', '}', ',', ';', '=', '+', '-',
            '/', '*', '&', '!', '|', ':', '[', ']', '<', '>',
            '?', '~', '%', '^'
        )

        @JvmStatic
        val instance: Language
            get() {
                if (_theOne == null) {
                    _theOne = LanguageLua()
                }
                return _theOne!!
            }
    }
}
