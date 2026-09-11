#include <jni.h>
#include <cstdint>
#include <string>

#include "JniHook.h"
#include "Log.h"
#include "ArtMethod.h"

static struct {
    int api_level;
    unsigned int art_field_size;
    int art_field_flags_offset;
    unsigned int art_method_size;
    int art_method_flags_offset;
    int art_method_native_offset;
    int class_flags_offset;

    jclass method_utils_class;
    jmethodID get_method_desc_id;
    jmethodID get_method_declaring_class_id;
    jmethodID get_method_name_id;
    bool ready;
} HookEnv;

static void clearPendingException(JNIEnv *env) {
    if (env != nullptr && env->ExceptionCheck()) {
        env->ExceptionClear();
    }
}

static bool callMethodUtilsString(JNIEnv *env, jmethodID methodId,
                                  jobject javaMethod, std::string &out) {
    if (env == nullptr || HookEnv.method_utils_class == nullptr
            || methodId == nullptr || javaMethod == nullptr) {
        return false;
    }

    jstring value = reinterpret_cast<jstring>(env->CallStaticObjectMethod(
            HookEnv.method_utils_class, methodId, javaMethod));
    if (env->ExceptionCheck() || value == nullptr) {
        clearPendingException(env);
        if (value != nullptr) {
            env->DeleteLocalRef(value);
        }
        return false;
    }

    const char *chars = env->GetStringUTFChars(value, nullptr);
    if (chars == nullptr) {
        clearPendingException(env);
        env->DeleteLocalRef(value);
        return false;
    }
    out.assign(chars);
    env->ReleaseStringUTFChars(value, chars);
    env->DeleteLocalRef(value);
    return true;
}

inline static uint32_t GetAccessFlags(const char *art_method) {
    if (art_method == nullptr || HookEnv.art_method_flags_offset <= 0) {
        return 0;
    }
    return *reinterpret_cast<const uint32_t *>(
            art_method + HookEnv.art_method_flags_offset);
}

inline static bool SetAccessFlags(char *art_method, uint32_t flags) {
    if (art_method == nullptr || HookEnv.art_method_flags_offset <= 0) {
        return false;
    }
    *reinterpret_cast<uint32_t *>(art_method + HookEnv.art_method_flags_offset) = flags;
    return true;
}

inline static bool AddAccessFlag(char *art_method, uint32_t flag) {
    uint32_t old_flag = GetAccessFlags(art_method);
    uint32_t new_flag = old_flag | flag;
    return new_flag != old_flag && SetAccessFlags(art_method, new_flag);
}

inline static bool ClearAccessFlag(char *art_method, uint32_t flag) {
    uint32_t old_flag = GetAccessFlags(art_method);
    uint32_t new_flag = old_flag & ~flag;
    return new_flag != old_flag && SetAccessFlags(art_method, new_flag);
}

inline static bool HasAccessFlag(char *art_method, uint32_t flag) {
    uint32_t flags = GetAccessFlags(art_method);
    ALOGD("AccessFlag:flags = 0x%x,flag = 0x%x", flags, flag);
    return (flags & flag) == flag;
}

inline static bool ClearFastNativeFlag(char *art_method) {
    // FastNative
    return HookEnv.api_level < __ANDROID_API_P__
           && ClearAccessFlag(art_method, kAccFastNative);
}

static void *GetArtMethod(JNIEnv *env, jclass clazz, jmethodID methodId,
                          bool isStatic) {
    if (env == nullptr || clazz == nullptr || methodId == nullptr) {
        return nullptr;
    }

    if (HookEnv.api_level >= __ANDROID_API_Q__) {
        jclass executable = env->FindClass("java/lang/reflect/Executable");
        if (executable == nullptr) {
            clearPendingException(env);
            return nullptr;
        }
        jfieldID artId = env->GetFieldID(executable, "artMethod", "J");
        if (artId == nullptr) {
            clearPendingException(env);
            env->DeleteLocalRef(executable);
            return nullptr;
        }

        jobject method = env->ToReflectedMethod(clazz, methodId,
                                                isStatic ? JNI_TRUE : JNI_FALSE);
        if (method == nullptr || env->ExceptionCheck()) {
            clearPendingException(env);
            if (method != nullptr) {
                env->DeleteLocalRef(method);
            }
            env->DeleteLocalRef(executable);
            return nullptr;
        }

        jlong artMethod = env->GetLongField(method, artId);
        bool failed = env->ExceptionCheck();
        clearPendingException(env);
        env->DeleteLocalRef(method);
        env->DeleteLocalRef(executable);
        return failed ? nullptr
                      : reinterpret_cast<void *>(static_cast<uintptr_t>(artMethod));
    }
    return methodId;
}

static void *GetFieldMethod(JNIEnv *env, jobject field) {
    if (env == nullptr || field == nullptr) {
        return nullptr;
    }

    if (HookEnv.api_level >= __ANDROID_API_Q__) {
        jclass fieldClass = env->FindClass("java/lang/reflect/Field");
        if (fieldClass == nullptr) {
            clearPendingException(env);
            return nullptr;
        }
        jmethodID getArtField = env->GetMethodID(fieldClass, "getArtField", "()J");
        if (getArtField == nullptr) {
            clearPendingException(env);
            env->DeleteLocalRef(fieldClass);
            return nullptr;
        }
        jlong artField = env->CallLongMethod(field, getArtField);
        bool failed = env->ExceptionCheck();
        clearPendingException(env);
        env->DeleteLocalRef(fieldClass);
        return failed ? nullptr
                      : reinterpret_cast<void *>(static_cast<uintptr_t>(artField));
    }
    return env->FromReflectedField(field);
}

bool CheckFlags(void *artMethod) {
    if (artMethod == nullptr || HookEnv.art_method_flags_offset <= 0) {
        return false;
    }
    char *method = static_cast<char *>(artMethod);
    if (!HasAccessFlag(method, kAccNative)) {
        ALOGE("not native method");
        return false;
    }
    ClearFastNativeFlag(method);
    return true;
}

void JniHook::HookJniFun(JNIEnv *env, jobject java_method, void *new_fun,
                         void **orig_fun, bool is_static) {
    if (!HookEnv.ready || env == nullptr || java_method == nullptr
            || new_fun == nullptr || orig_fun == nullptr) {
        return;
    }

    std::string className;
    std::string methodName;
    std::string signature;
    if (!callMethodUtilsString(env, HookEnv.get_method_declaring_class_id,
                               java_method, className)
            || !callMethodUtilsString(env, HookEnv.get_method_name_id,
                                      java_method, methodName)
            || !callMethodUtilsString(env, HookEnv.get_method_desc_id,
                                      java_method, signature)) {
        return;
    }

    HookJniFun(env, className.c_str(), methodName.c_str(), signature.c_str(),
               new_fun, orig_fun, is_static);
}

void JniHook::HookJniFun(JNIEnv *env, const char *class_name,
                         const char *method_name, const char *sign,
                         void *new_fun, void **orig_fun, bool is_static) {
    if (!HookEnv.ready || env == nullptr || class_name == nullptr
            || method_name == nullptr || sign == nullptr
            || new_fun == nullptr || orig_fun == nullptr
            || HookEnv.art_method_native_offset < 0) {
        return;
    }

    jclass clazz = env->FindClass(class_name);
    if (!clazz) {
        ALOGD("findClass fail: %s %s", class_name, method_name);
        clearPendingException(env);
        return;
    }

    jmethodID method = is_static
            ? env->GetStaticMethodID(clazz, method_name, sign)
            : env->GetMethodID(clazz, method_name, sign);
    if (!method) {
        clearPendingException(env);
        ALOGD("get method id fail: %s %s", class_name, method_name);
        env->DeleteLocalRef(clazz);
        return;
    }

    void *rawArtMethod = GetArtMethod(env, clazz, method, is_static);
    if (rawArtMethod == nullptr || !CheckFlags(rawArtMethod)) {
        ALOGE("check flags error. class: %s, method: %s", class_name, method_name);
        env->DeleteLocalRef(clazz);
        return;
    }

    size_t nativeIndex = static_cast<size_t>(HookEnv.art_method_native_offset);
    size_t nativeByteOffset = nativeIndex * sizeof(uintptr_t);
    if (nativeByteOffset + sizeof(uintptr_t) > HookEnv.art_method_size) {
        ALOGE("native offset out of bounds. class: %s, method: %s",
              class_name, method_name);
        env->DeleteLocalRef(clazz);
        return;
    }

    auto artMethod = reinterpret_cast<uintptr_t *>(rawArtMethod);
    *orig_fun = reinterpret_cast<void *>(artMethod[nativeIndex]);
    if (*orig_fun == nullptr || *orig_fun == new_fun) {
        // Do not install a hook that would recurse into itself.
        env->DeleteLocalRef(clazz);
        return;
    }

    JNINativeMethod methods[] = {
            {const_cast<char *>(method_name), const_cast<char *>(sign), new_fun},
    };
    if (env->RegisterNatives(clazz, methods, 1) < 0) {
        clearPendingException(env);
        ALOGE("jni hook error. class: %s, method: %s", class_name, method_name);
        env->DeleteLocalRef(clazz);
        return;
    }

    // FastNative
    if (HookEnv.api_level == __ANDROID_API_O__
            || HookEnv.api_level == __ANDROID_API_O_MR1__) {
        AddAccessFlag(static_cast<char *>(rawArtMethod), kAccFastNative);
    }
    ALOGD("register class: %s, method: %s success!", class_name, method_name);
    env->DeleteLocalRef(clazz);
}

__attribute__((section (".mytext"))) JNICALL void native_offset
        (JNIEnv *env, jclass obj) {
}

__attribute__((section (".mytext"))) JNICALL void native_offset2
        (JNIEnv *env, jclass obj) {
}

__attribute__((section (".mytext"))) JNICALL void set_method_accessible(
        JNIEnv *env, jclass obj, jclass clazz, jobject method) {
    if (!HookEnv.ready || env == nullptr || clazz == nullptr || method == nullptr) {
        return;
    }
    jmethodID methodId = env->FromReflectedMethod(method);
    char *art_method = static_cast<char *>(GetArtMethod(env, clazz, methodId, true));
    if (art_method == nullptr) {
        return;
    }
    AddAccessFlag(art_method, kAccPublic);
    if (HookEnv.api_level >= __ANDROID_API_Q__) {
        AddAccessFlag(art_method, kAccPublicApi);
    }
}

__attribute__((section (".mytext"))) JNICALL void set_field_accessible(
        JNIEnv *env, jclass obj, jclass clazz, jobject field) {
    if (!HookEnv.ready || env == nullptr || field == nullptr) {
        return;
    }
    char *artField = static_cast<char *>(GetFieldMethod(env, field));
    if (artField == nullptr || HookEnv.art_field_flags_offset <= 0) {
        return;
    }
    uint32_t *flags = reinterpret_cast<uint32_t *>(
            artField + HookEnv.art_field_flags_offset);
    *flags |= kAccPublic;
    if (HookEnv.api_level >= __ANDROID_API_Q__) {
        *flags |= kAccPublicApi;
    }
    *flags &= ~kAccFinal;
}

static bool registerNative(JNIEnv *env) {
    jclass clazz = env->FindClass("com/parallaxelite/jnihook/jni/JniHook");
    if (clazz == nullptr) {
        clearPendingException(env);
        return false;
    }
    JNINativeMethod methods[] = {
            {const_cast<char *>("nativeOffset"), const_cast<char *>("()V"),
             (void *) native_offset},
            {const_cast<char *>("nativeOffset2"), const_cast<char *>("()V"),
             (void *) native_offset2},
            {const_cast<char *>("setAccessible"),
             const_cast<char *>("(Ljava/lang/Class;Ljava/lang/reflect/Method;)V"),
             (void *) set_method_accessible},
            {const_cast<char *>("setAccessible"),
             const_cast<char *>("(Ljava/lang/Class;Ljava/lang/reflect/Field;)V"),
             (void *) set_field_accessible},
    };
    int result = env->RegisterNatives(
            clazz, methods, sizeof(methods) / sizeof(methods[0]));
    env->DeleteLocalRef(clazz);
    if (result < 0) {
        clearPendingException(env);
        ALOGE("jni register error.");
        return false;
    }
    return true;
}

static size_t pointerDistance(const void *left, const void *right) {
    uintptr_t a = reinterpret_cast<uintptr_t>(left);
    uintptr_t b = reinterpret_cast<uintptr_t>(right);
    return a > b ? a - b : b - a;
}

void JniHook::InitJniHook(JNIEnv *env, int api_level) {
    HookEnv.ready = false;
    HookEnv.api_level = api_level;
    HookEnv.art_method_native_offset = -1;
    HookEnv.art_method_flags_offset = 0;
    HookEnv.art_field_flags_offset = 0;

    if (env == nullptr || !registerNative(env)) {
        return;
    }

    jclass clazz = env->FindClass("com/parallaxelite/jnihook/jni/JniHook");
    if (clazz == nullptr) {
        clearPendingException(env);
        return;
    }

    jmethodID nativeOffsetId = env->GetStaticMethodID(clazz, "nativeOffset", "()V");
    jmethodID nativeOffset2Id = env->GetStaticMethodID(clazz, "nativeOffset2", "()V");
    jfieldID nativeOffsetFieldId = env->GetStaticFieldID(clazz, "NATIVE_OFFSET", "I");
    jfieldID nativeOffsetField2Id = env->GetStaticFieldID(clazz, "NATIVE_OFFSET_2", "I");
    if (env->ExceptionCheck() || nativeOffsetId == nullptr || nativeOffset2Id == nullptr
            || nativeOffsetFieldId == nullptr || nativeOffsetField2Id == nullptr) {
        clearPendingException(env);
        env->DeleteLocalRef(clazz);
        return;
    }

    jobject reflectedField = env->ToReflectedField(clazz, nativeOffsetFieldId, JNI_TRUE);
    jobject reflectedField2 = env->ToReflectedField(clazz, nativeOffsetField2Id, JNI_TRUE);
    if (reflectedField == nullptr || reflectedField2 == nullptr || env->ExceptionCheck()) {
        clearPendingException(env);
        if (reflectedField != nullptr) env->DeleteLocalRef(reflectedField);
        if (reflectedField2 != nullptr) env->DeleteLocalRef(reflectedField2);
        env->DeleteLocalRef(clazz);
        return;
    }

    void *nativeOffsetField = GetFieldMethod(env, reflectedField);
    void *nativeOffsetField2 = GetFieldMethod(env, reflectedField2);
    env->DeleteLocalRef(reflectedField);
    env->DeleteLocalRef(reflectedField2);

    void *nativeOffset = GetArtMethod(env, clazz, nativeOffsetId, true);
    void *nativeOffset2 = GetArtMethod(env, clazz, nativeOffset2Id, true);
    if (nativeOffsetField == nullptr || nativeOffsetField2 == nullptr
            || nativeOffset == nullptr || nativeOffset2 == nullptr) {
        env->DeleteLocalRef(clazz);
        return;
    }

    size_t fieldSize = pointerDistance(nativeOffsetField, nativeOffsetField2);
    size_t methodSize = pointerDistance(nativeOffset, nativeOffset2);
    // Adjacent ART structures are small. Refuse insane distances instead of scanning
    // arbitrary process memory if a hidden-field layout changed on a new Android build.
    if (fieldSize < sizeof(uint32_t) || fieldSize > 256
            || methodSize < sizeof(uintptr_t) || methodSize > 512) {
        ALOGE("init jni hook error. unreasonable ART layout field=%zu method=%zu",
              fieldSize, methodSize);
        env->DeleteLocalRef(clazz);
        return;
    }

    HookEnv.art_field_size = static_cast<unsigned int>(fieldSize);
    HookEnv.art_method_size = static_cast<unsigned int>(methodSize);

    auto artMethod = reinterpret_cast<uintptr_t *>(nativeOffset);
    size_t methodSlots = methodSize / sizeof(uintptr_t);
    bool nativePointerFound = false;
    for (size_t i = 0; i < methodSlots; ++i) {
        if (reinterpret_cast<void *>(artMethod[i]) == (void *) native_offset) {
            HookEnv.art_method_native_offset = static_cast<int>(i);
            nativePointerFound = true;
            break;
        }
    }
    if (!nativePointerFound) {
        ALOGE("init jni hook error. art_method_native_offset not found!");
        env->DeleteLocalRef(clazz);
        return;
    }

    uint32_t methodFlags = kAccPublic | kAccStatic | kAccNative | kAccFinal;
    if (api_level >= __ANDROID_API_Q__) {
        methodFlags |= kAccPublicApi;
    }
    if (api_level >= __ANDROID_API_S__) {
        methodFlags |= kAccNterpInvokeFastPathFlag;
    }

    char *methodStart = reinterpret_cast<char *>(nativeOffset);
    bool methodFlagsFound = false;
    for (size_t offset = sizeof(uint32_t);
         offset + sizeof(uint32_t) <= methodSize;
         offset += sizeof(uint32_t)) {
        uint32_t value = *reinterpret_cast<uint32_t *>(methodStart + offset);
        if (value == methodFlags) {
            HookEnv.art_method_flags_offset = static_cast<int>(offset);
            methodFlagsFound = true;
            break;
        }
    }
    if (!methodFlagsFound) {
        ALOGE("init jni hook error. art_method_flags_offset not found!");
        env->DeleteLocalRef(clazz);
        return;
    }

    uint32_t fieldFlags = kAccPublic | kAccStatic | kAccFinal;
    if (api_level >= __ANDROID_API_Q__) {
        fieldFlags |= kAccPublicApi;
    }

    char *fieldStart = reinterpret_cast<char *>(nativeOffsetField);
    bool fieldFlagsFound = false;
    for (size_t offset = sizeof(uint32_t);
         offset + sizeof(uint32_t) <= fieldSize;
         offset += sizeof(uint32_t)) {
        uint32_t value = *reinterpret_cast<uint32_t *>(fieldStart + offset);
        if (value == fieldFlags) {
            HookEnv.art_field_flags_offset = static_cast<int>(offset);
            fieldFlagsFound = true;
            break;
        }
    }
    if (!fieldFlagsFound) {
        ALOGE("init jni hook error. art_field_flags_offset not found!");
        env->DeleteLocalRef(clazz);
        return;
    }

    jclass methodUtilsLocal = env->FindClass("com/parallaxelite/jnihook/MethodUtils");
    if (methodUtilsLocal == nullptr) {
        clearPendingException(env);
        env->DeleteLocalRef(clazz);
        return;
    }

    HookEnv.method_utils_class = (jclass) env->NewGlobalRef(methodUtilsLocal);
    env->DeleteLocalRef(methodUtilsLocal);
    if (HookEnv.method_utils_class == nullptr) {
        env->DeleteLocalRef(clazz);
        return;
    }

    HookEnv.get_method_desc_id = env->GetStaticMethodID(
            HookEnv.method_utils_class, "getDesc",
            "(Ljava/lang/reflect/Method;)Ljava/lang/String;");
    HookEnv.get_method_declaring_class_id = env->GetStaticMethodID(
            HookEnv.method_utils_class, "getDeclaringClass",
            "(Ljava/lang/reflect/Method;)Ljava/lang/String;");
    HookEnv.get_method_name_id = env->GetStaticMethodID(
            HookEnv.method_utils_class, "getMethodName",
            "(Ljava/lang/reflect/Method;)Ljava/lang/String;");

    if (env->ExceptionCheck() || HookEnv.get_method_desc_id == nullptr
            || HookEnv.get_method_declaring_class_id == nullptr
            || HookEnv.get_method_name_id == nullptr) {
        clearPendingException(env);
        env->DeleteGlobalRef(HookEnv.method_utils_class);
        HookEnv.method_utils_class = nullptr;
        env->DeleteLocalRef(clazz);
        return;
    }

    HookEnv.ready = true;
    env->DeleteLocalRef(clazz);
}
