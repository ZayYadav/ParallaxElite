#include "IO.h"
#include "Log.h"

#include <cstring>
#include <list>
#include <string>

jmethodID getAbsolutePathMethodId;
static std::list<IO::RelocateInfo> relocate_rule;

static std::string replaceAll(const char *input,
                              const std::string &source,
                              const std::string &destination) {
    if (input == nullptr) {
        return std::string();
    }
    if (source.empty()) {
        return std::string(input);
    }

    std::string result(input);
    std::string::size_type pos = 0;
    while ((pos = result.find(source, pos)) != std::string::npos) {
        result.replace(pos, source.length(), destination);
        pos += destination.length();
    }
    return result;
}

const char *IO::redirectPath(const char *__path) {
    if (__path == nullptr) {
        return nullptr;
    }

    for (const IO::RelocateInfo &info : relocate_rule) {
        if (info.targetPath.empty()) {
            continue;
        }
        if (strstr(__path, info.targetPath.c_str()) != nullptr
                && strstr(__path, "/SdCard/") == nullptr) {
            // The old implementation malloc'ed a buffer, called strlen() on the
            // uninitialized allocation, and could underflow size_t when the
            // replacement was shorter. Besides random SIGSEGV, callers also had no
            // ownership contract and leaked the allocation. Keep storage per-thread
            // instead, with fully bounded std::string operations.
            static thread_local std::string redirectedPath;
            redirectedPath = replaceAll(__path, info.targetPath, info.relocatePath);
            ALOGD("redirectPath %s  => %s", __path, redirectedPath.c_str());
            return redirectedPath.c_str();
        }
    }
    return __path;
}

jstring IO::redirectPath(JNIEnv *env, jstring path) {
    if (path == nullptr) {
        return nullptr;
    }
    return BoxCore::redirectPathString(env, path);
}

jobject IO::redirectPath(JNIEnv *env, jobject path) {
    if (path == nullptr) {
        return nullptr;
    }
    return BoxCore::redirectPathFile(env, path);
}

void IO::addRule(const char *targetPath, const char *relocatePath) {
    if (targetPath == nullptr || relocatePath == nullptr || targetPath[0] == '\0') {
        return;
    }

    // Rules are configured during virtual-runtime startup before hooks are enabled.
    // Replace an existing rule rather than accumulating duplicates on a retry.
    for (IO::RelocateInfo &info : relocate_rule) {
        if (info.targetPath == targetPath) {
            info.relocatePath = relocatePath;
            return;
        }
    }

    IO::RelocateInfo info;
    info.targetPath = targetPath;
    info.relocatePath = relocatePath;
    relocate_rule.push_back(info);
}

void IO::init(JNIEnv *env) {
    if (env == nullptr) {
        return;
    }
    jclass tmpFile = env->FindClass("java/io/File");
    if (tmpFile == nullptr) {
        if (env->ExceptionCheck()) {
            env->ExceptionClear();
        }
        return;
    }
    getAbsolutePathMethodId = env->GetMethodID(tmpFile, "getAbsolutePath", "()Ljava/lang/String;");
    env->DeleteLocalRef(tmpFile);
    if (env->ExceptionCheck()) {
        env->ExceptionClear();
        getAbsolutePathMethodId = nullptr;
    }
}
