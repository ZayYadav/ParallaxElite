#include "BoxCore.h"
#include "Log.h"
#include "IO.h"
#include <jni.h>
#include <mutex>
#include "JniHook/JniHook.h"
#include "Hook/VMClassLoaderHook.h"
#include "Hook/UnixFileSystemHook.h"
#include "Hook/SystemPropertiesHook.h"
#include <Hook/BinderHook.h>
#include <Hook/DexFileHook.h>
#include <Hook/RuntimeHook.h>
#include <Hook/LinuxHook.h>
#include "SandHook/oxorany.h"

struct {
    JavaVM *vm;
    jclass NativeCoreClass;
    jmethodID getCallingUidId;
    jmethodID redirectPathString;
    jmethodID redirectPathFile;
    int api_level;
} VMEnv;

static std::mutex gInitMutex;
static std::mutex gHookMutex;
static bool gNativeInitialized = false;
static bool gHooksEnabled = false;

JNIEnv *getEnv() {
    if (VMEnv.vm == nullptr) {
        return nullptr;
    }
    JNIEnv *env = nullptr;
    jint result = VMEnv.vm->GetEnv(reinterpret_cast<void **>(&env), JNI_VERSION_1_6);
    return result == JNI_OK ? env : nullptr;
}

JNIEnv *ensureEnvCreated() {
    JNIEnv *env = getEnv();
    if (env != nullptr) {
        return env;
    }
    if (VMEnv.vm == nullptr) {
        return nullptr;
    }
    if (VMEnv.vm->AttachCurrentThread(&env, nullptr) != JNI_OK) {
        return nullptr;
    }
    return env;
}

int BoxCore::getCallingUid(JNIEnv *env, int orig) {
    env = ensureEnvCreated();
    if (env == nullptr || VMEnv.NativeCoreClass == nullptr || VMEnv.getCallingUidId == nullptr) {
        return orig;
    }
    jint result = env->CallStaticIntMethod(VMEnv.NativeCoreClass, VMEnv.getCallingUidId, orig);
    if (env->ExceptionCheck()) {
        // A UID redirect callback is infrastructure. Do not let one transient Java
        // exception poison an unrelated Binder transaction in the guest process.
        env->ExceptionClear();
        return orig;
    }
    return result;
}

jstring BoxCore::redirectPathString(JNIEnv *env, jstring path) {
    if (path == nullptr) {
        return nullptr;
    }
    env = ensureEnvCreated();
    if (env == nullptr || VMEnv.NativeCoreClass == nullptr || VMEnv.redirectPathString == nullptr) {
        return path;
    }
    jstring redirected = (jstring) env->CallStaticObjectMethod(
            VMEnv.NativeCoreClass, VMEnv.redirectPathString, path);
    if (env->ExceptionCheck()) {
        env->ExceptionClear();
        return path;
    }
    return redirected != nullptr ? redirected : path;
}

jobject BoxCore::redirectPathFile(JNIEnv *env, jobject path) {
    if (path == nullptr) {
        return nullptr;
    }
    env = ensureEnvCreated();
    if (env == nullptr || VMEnv.NativeCoreClass == nullptr || VMEnv.redirectPathFile == nullptr) {
        return path;
    }
    jobject redirected = env->CallStaticObjectMethod(
            VMEnv.NativeCoreClass, VMEnv.redirectPathFile, path);
    if (env->ExceptionCheck()) {
        env->ExceptionClear();
        return path;
    }
    return redirected != nullptr ? redirected : path;
}

int BoxCore::getApiLevel() {
    return VMEnv.api_level;
}

JavaVM *BoxCore::getJavaVM() {
    return VMEnv.vm;
}

void nativeHook(JNIEnv *env) {
    BaseHook::init(env);
    UnixFileSystemHook::init(env);
    VMClassLoaderHook::init(env);
    SystemPropertiesHook::init(env);
    RuntimeHook::init(env);
    LinuxHook::init(env);
    BinderHook::init(env);
}

void hideXposed(JNIEnv *env, jclass clazz) {
    ALOGD("set hideXposed");
    VMClassLoaderHook::hideXposed();
}

void init(JNIEnv *env, jobject clazz, jint api_level) {
    if (env == nullptr) {
        return;
    }

    std::lock_guard<std::mutex> lock(gInitMutex);
    VMEnv.api_level = api_level;
    if (gNativeInitialized) {
        return;
    }

    ALOGD("NativeCore init.");
    jclass localClass = env->FindClass(VMCORE_CLASS);
    if (localClass == nullptr) {
        if (env->ExceptionCheck()) {
            env->ExceptionClear();
        }
        return;
    }

    jclass globalClass = (jclass) env->NewGlobalRef(localClass);
    env->DeleteLocalRef(localClass);
    if (globalClass == nullptr) {
        return;
    }

    jmethodID callingUid = env->GetStaticMethodID(globalClass, "getCallingUid", "(I)I");
    jmethodID redirectString = env->GetStaticMethodID(
            globalClass, "redirectPath", "(Ljava/lang/String;)Ljava/lang/String;");
    jmethodID redirectFile = env->GetStaticMethodID(
            globalClass, "redirectPath", "(Ljava/io/File;)Ljava/io/File;");

    if (env->ExceptionCheck() || callingUid == nullptr
            || redirectString == nullptr || redirectFile == nullptr) {
        if (env->ExceptionCheck()) {
            env->ExceptionClear();
        }
        env->DeleteGlobalRef(globalClass);
        return;
    }

    VMEnv.NativeCoreClass = globalClass;
    VMEnv.getCallingUidId = callingUid;
    VMEnv.redirectPathString = redirectString;
    VMEnv.redirectPathFile = redirectFile;
    JniHook::InitJniHook(env, api_level);
    gNativeInitialized = true;
}

void addIORule(JNIEnv *env, jclass clazz, jstring target_path, jstring relocate_path) {
    if (env == nullptr || target_path == nullptr || relocate_path == nullptr) {
        return;
    }

    const char *target = env->GetStringUTFChars(target_path, nullptr);
    if (target == nullptr) {
        return;
    }
    const char *relocate = env->GetStringUTFChars(relocate_path, nullptr);
    if (relocate == nullptr) {
        env->ReleaseStringUTFChars(target_path, target);
        return;
    }

    IO::addRule(target, relocate);
    env->ReleaseStringUTFChars(relocate_path, relocate);
    env->ReleaseStringUTFChars(target_path, target);
}

void enableIO(JNIEnv *env, jclass clazz) {
    if (env == nullptr) {
        return;
    }

    std::lock_guard<std::mutex> lock(gHookMutex);
    if (gHooksEnabled) {
        return;
    }
    if (!gNativeInitialized) {
        ALOGD("enableIO ignored before NativeCore init");
        return;
    }

    ALOGD("set enableIO");
    IO::init(env);
    nativeHook(env);
    // Installing the same JNI hooks twice can make an original function point back
    // into the hook and recurse until stack overflow. Treat hook install as one-shot.
    gHooksEnabled = true;
}

static JNINativeMethod gMethods[] = {
        {"hideXposed", "()V", (void *) hideXposed},
        {"addIORule", "(Ljava/lang/String;Ljava/lang/String;)V", (void *) addIORule},
        {"enableIO", "()V", (void *) enableIO},
        {"init", "(I)V", (void *) init},
};

int registerNativeMethods(JNIEnv *env, const char *className, JNINativeMethod *methods, int numMethods) {
    jclass clazz = env->FindClass(className);
    if (clazz == nullptr) {
        return JNI_FALSE;
    }
    int result = env->RegisterNatives(clazz, methods, numMethods);
    env->DeleteLocalRef(clazz);
    if (result < 0) {
        return JNI_FALSE;
    }
    return JNI_TRUE;
}

int registerNatives(JNIEnv *env) {
    if (!registerNativeMethods(env, VMCORE_CLASS, gMethods,
                               sizeof(gMethods) / sizeof(gMethods[0]))) {
        return JNI_FALSE;
    }
    return JNI_TRUE;
}

void registerMethod(JNIEnv *jenv) {
    registerNatives(jenv);
}

JNIEXPORT jint JNI_OnLoad(JavaVM *vm, void *reserved) {
    JNIEnv *env = nullptr;
    VMEnv.vm = vm;
    if (vm == nullptr
            || vm->GetEnv(reinterpret_cast<void **>(&env), JNI_VERSION_1_6) != JNI_OK) {
        return JNI_EVERSION;
    }
    if (!registerNatives(env)) {
        return JNI_ERR;
    }
    return JNI_VERSION_1_6;
}
