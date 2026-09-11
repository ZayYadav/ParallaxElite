//#import "include/shadowhook.h"
#include "SystemPropertiesHook.h"
#include "IO.h"
#include "BoxCore.h"
#import "JniHook/JniHook.h"
#include "Log.h"

static std::map<std::string, std::string> prop_map;

HOOK_JNI(jstring, native_get, JNIEnv *env, jobject obj, jstring key, jstring def) {
    if (env == nullptr || key == nullptr || def == nullptr) {
        return orig_native_get(env, obj, key, def);
    }

    const char *key_str = env->GetStringUTFChars(key, nullptr);
    if (key_str == nullptr) {
        if (env->ExceptionCheck()) {
            env->ExceptionClear();
        }
        return orig_native_get(env, obj, key, def);
    }

    const char *def_str = env->GetStringUTFChars(def, nullptr);
    if (def_str == nullptr) {
        if (env->ExceptionCheck()) {
            env->ExceptionClear();
        }
        env->ReleaseStringUTFChars(key, key_str);
        return orig_native_get(env, obj, key, def);
    }

    auto ret = prop_map.find(key_str);
    if (ret != prop_map.end()) {
        const std::string value = ret->second;
        env->ReleaseStringUTFChars(def, def_str);
        env->ReleaseStringUTFChars(key, key_str);
        return env->NewStringUTF(value.c_str());
    }

    env->ReleaseStringUTFChars(def, def_str);
    env->ReleaseStringUTFChars(key, key_str);
    return orig_native_get(env, obj, key, def);
}

HOOK_JNI(int, __system_property_get, const char *name, char *value) {
    if (name == nullptr || value == nullptr) {
        return orig___system_property_get(name, value);
    }

    ALOGD("__system_property_get: %s", name);
    auto ret = prop_map.find(name);
    if (ret != prop_map.end()) {
        const char *ret_value = ret->second.c_str();
        // Android system property values are bounded by PROP_VALUE_MAX. All values
        // installed below are comfortably inside that limit; copy the terminating NUL.
        const size_t length = ret->second.size();
        memcpy(value, ret_value, length + 1);
        return static_cast<int>(length);
    }
    return orig___system_property_get(name, value);
}

void SystemPropertiesHook::init(JNIEnv *env) {
    if (env == nullptr) {
        return;
    }

    // emplace is both namespace-correct on modern NDK libc++ and idempotent when
    // initialization is retried.
    prop_map.emplace("ro.product.board", "umi");
    prop_map.emplace("ro.product.brand", "Xiaomi");
    prop_map.emplace("ro.product.device", "umi");
    prop_map.emplace("ro.build.display.id", "QKQ1.191117.002 test-keys");
    prop_map.emplace("ro.build.host", "c5-miui-ota-bd074.bj");
    prop_map.emplace("ro.build.id", "QKQ1.191117.002");
    prop_map.emplace("ro.product.manufacturer", "Xiaomi");
    prop_map.emplace("ro.product.model", "Mi 10");
    prop_map.emplace("ro.product.name", "umi");
    prop_map.emplace("ro.build.tags", "release-keys");
    prop_map.emplace("ro.build.type", "user");
    prop_map.emplace("ro.build.user", "builder");

    JniHook::HookJniFun(
            env,
            "android/os/SystemProperties",
            "native_get",
            "(Ljava/lang/String;Ljava/lang/String;)Ljava/lang/String;",
            (void *) new_native_get,
            (void **) (&orig_native_get),
            true);
    // shadowhook_hook_sym_name("libc.so", "__system_property_get",
    //                          (void *)new___system_property_get,
    //                          (void **) &orig___system_property_get);
}
