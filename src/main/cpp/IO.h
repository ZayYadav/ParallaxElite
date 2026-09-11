//
// Created by Milk on 4/10/21.
//

#ifndef VIRTUAL_APP_IO_H
#define VIRTUAL_APP_IO_H

#include <jni.h>
#include <string>

#include "BoxCore.h"

class IO {
public:
    static void init(JNIEnv *env);

    struct RelocateInfo {
        std::string targetPath;
        std::string relocatePath;
    };

    static void addRule(const char *targetPath, const char *relocatePath);

    static jstring redirectPath(JNIEnv *env, jstring path);

    static jobject redirectPath(JNIEnv *env, jobject path);

    // The returned pointer is either the original input pointer or a thread-local
    // redirected buffer that remains valid until the next redirect on this thread.
    static const char *redirectPath(const char *__path);
};

#endif //VIRTUAL_APP_IO_H
