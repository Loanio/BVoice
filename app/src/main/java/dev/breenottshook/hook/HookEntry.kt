package dev.breenottshook.hook

import com.highcapable.yukihookapi.hook.factory.encase
import com.highcapable.yukihookapi.hook.xposed.proxy.IYukiHookXposedInit
import dev.breenottshook.config.ConfigContract

class HookEntry : IYukiHookXposedInit {
    override fun onHook() = encase {
        loadApp(ConfigContract.BREENO_PACKAGE, BreenoHooker(), BreenoSettingsHook())
    }
}
