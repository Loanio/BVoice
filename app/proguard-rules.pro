# Loaded by YukiHook/Xposed from META-INF/yukihookapi_init and assets/xposed_init.
-keep class dev.breenottshook.hook.HookEntry { *; }

# YukiHook contains generated and reflectively-resolved bridge classes. Keeping
# this boundary is intentionally conservative; application and Compose code can
# still be fully optimized and removed when unused.
-keep class com.highcapable.yukihookapi.** { *; }

# Preserve metadata used by Kotlin serialization and reflective bridge code.
-keepattributes RuntimeVisibleAnnotations,RuntimeInvisibleAnnotations,AnnotationDefault,Signature,InnerClasses,EnclosingMethod
