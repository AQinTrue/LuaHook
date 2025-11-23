# LuaHook

**A module for writing Xposed scripts via Lua**

**一个使用 Lua 编写 Xposed 模块的框架**

This is a module under active development that allows you to write powerful Xposed scripts using the lightweight and flexible Lua language.

这是一个正在积极开发中的模块，旨在让你能够使用轻巧灵活的 Lua 语言来编写强大的 Xposed 脚本。

**Key Features (Under Development)**

* **Lua Script Support:** Hook Android applications using your familiar Lua syntax.
* **Xposed Integration:** Seamlessly integrates with the Xposed framework to achieve various hooking functionalities.
* **Dynamic Loading:** Load and apply Lua scripts without recompiling and reinstalling the APK.
* **[More features coming soon...]**

**主要特性 (开发中)**

* **Lua 脚本支持:** 使用你熟悉的 Lua 语法来hook Android 应用。
* **Xposed 集成:** 无缝对接 Xposed 框架，实现各种 Hook 功能。
* **动态加载:** 无需重新编译安装 APK，即可加载和应用 Lua 脚本。
* **[更多特性敬请期待...]**

**Important Notice**

This module is still in the early stages of development and may contain various issues and shortcomings. I sincerely apologize if you encounter any errors during use.

**重要提示**

本模块尚处于早期开发阶段，可能存在各种问题和不足。如果你在使用过程中遇到任何错误，我深感抱歉。

**Contact**

Welcome to join our Telegram channel for communication and feedback: [Telegram Channel](https://t.me/LuaXposed)[QQ](https://qm.qq.com/q/Qt3yKDzCeG)

Alternatively, you can directly submit issues and suggestions on the project's Issue page. I will actively respond and do my best to resolve them.

**联系方式**

欢迎加入我们的 Telegram 频道进行交流和反馈：[Telegram频道](https://t.me/LuaXposed),[QQ](https://qm.qq.com/q/Qt3yKDzCeG)

或者，你也可以直接在本项目的 Issue 页面提交问题和建议，我会积极回复并尽力解决。

**Thank you for your attention and support!**

**感谢你的关注和支持！**

**致谢/Thanks**

[DexKit](https://github.com/LuckyPray/DexKit)

[XpHelper](https://github.com/suzhelan/XPHelper)

[NeLuaJ](https://github.com/znzsofficial/NeLuaJ)

[sora-editor](https://github.com/Rosemoe/sora-editor)

[sora-editor-with-androlua](https://github.com/dingyi222666/sora-editor-with-androlua)

**支持/Support**

微信赞赏方式

使用LuaHook勾选微信增加脚本

```lua
imports "top.sacz.xphelper.dexkit.FieldFinder"
imports "java.lang.reflect.Modifier"
imports "top.sacz.xphelper.dexkit.bean.MethodInfo"

hook {
  class="android.app.Application",
  method="attach",
  params={"android.content.Context"},
  after=function(it)
    XpHelper.initContext(it.thisObject)
    XpHelper.injectResourcesToContext(it.thisObject)
    local loader = invoke(it.thisObject, "getClassLoader")
    local dexFinder = DexFinder.INSTANCE
    dexFinder.create(lpparam.appInfo.sourceDir)
    local method = MethodInfo() {
      UsedString = { "MicroMsg.QRCodeHandler", "qbar_string_scan_source" },
      ParamCount = 2,
    }.generate().firstOrNull()
    hook {
      method=method,
      before=function(it)
        it.args[1].putString("result_code_name", "WX_CODE")
      end
    }
  end
}
```

打开微信扫一扫，扫描下面二维码进行赞赏:)

![wechat](app/src/main/res/drawable/wechat_qr.png)

其他赞赏方式/Other Ways

Ton
```text
UQCT4SxRvop52iLADb8_TcuoGFlr8UqC4QNTlIraRcljm-Us
```

USDT(TRC20)
```text
TLhumaxCuCJYddWwfAyS9ZyVWeFbwUfydm
```

TRX(TRC20)
```text
TGGvqp4zx9VNT6HaijAQxQT8uFibs1etxt
```



