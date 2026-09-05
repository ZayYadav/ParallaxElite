#ifndef SANDHOOK_MACROS_H
#define SANDHOOK_MACROS_H

#include <vector>
#include <algorithm>
#include <cstdint>
#include <string>

#include "oxorany.h"
#include "KittyMemory/MemoryPatch.h"

static std::vector<MemoryPatch> memoryPatches;
static std::vector<uint64_t> offsetVector;

static int findPatchIndex(uint64_t address) {
    auto it = std::find(offsetVector.begin(), offsetVector.end(), address);
    if (it != offsetVector.end()) {
        return std::distance(offsetVector.begin(), it);
    }
    return -1;
}

// Patching a offset without switch.
void patchOffset(const char *fileName, uint64_t offset, std::string hexBytes, bool isOn) {
    MemoryPatch patch = MemoryPatch::createWithHex(fileName, offset, hexBytes);

    int idx = findPatchIndex(offset);
    if (idx != -1) {
        patch = memoryPatches[idx];
    } else {
        memoryPatches.push_back(patch);
        offsetVector.push_back(offset);
    }

    if (!patch.isValid()) {
        // LOGE(oxorany("Failing offset: 0x%llu, please re-check the hex"), offset);
        return;
    }
    
    if (isOn) {
        if (!patch.Modify()) {
            // LOGE(oxorany("Something went wrong while patching this offset: 0x%llu"), offset);
        }
    } else {
        if (!patch.Restore()) {
            // LOGE(oxorany("Something went wrong while restoring this offset: 0x%llu"), offset);
        }
    }
}

// Patching with absolute address
void patchOffsetSym(uintptr_t absolute_address, std::string hexBytes, bool isOn) {
    MemoryPatch patch = MemoryPatch::createWithHex(absolute_address, hexBytes);

    int idx = findPatchIndex(absolute_address);
    if (idx != -1) {
        patch = memoryPatches[idx];
    } else {
        memoryPatches.push_back(patch);
        offsetVector.push_back(absolute_address);
    }

    if (!patch.isValid()) {
        // LOGE(oxorany("Failing offset: 0x%llu, please re-check the hex"), absolute_address);
        return;
    }
    
    if (isOn) {
        if (!patch.Modify()) {
            // LOGE(oxorany("Something went wrong while patching this offset: 0x%llu"), absolute_address);
        }
    } else {
        if (!patch.Restore()) {
            // LOGE(oxorany("Something went wrong while restoring this offset: 0x%llu"), absolute_address);
        }
    }
}

// Patching Macros (using targetLibName from context)
#define PATCH(offset, hex) patchOffset(targetLibName, string2Offset(oxorany(offset)), oxorany(hex), true)
#define PATCH_LIB(lib, offset, hex) patchOffset(oxorany(lib), string2Offset(oxorany(offset)), oxorany(hex), true)

#define PATCH_SYM(sym, hex) patchOffsetSym((uintptr_t)dlsym(dlopen(targetLibName, 4), oxorany(sym)), oxorany(hex), true)
#define PATCH_LIB_SYM(lib, sym, hex) patchOffsetSym((uintptr_t)dlsym(dlopen(oxorany(lib), 4), oxorany(sym)), oxorany(hex), true)

// Switch versions (controlled by boolean)
#define PATCH_SWITCH(offset, hex, boolean) patchOffset(targetLibName, string2Offset(oxorany(offset)), oxorany(hex), boolean)
#define PATCH_LIB_SWITCH(lib, offset, hex, boolean) patchOffset(oxorany(lib), string2Offset(oxorany(offset)), oxorany(hex), boolean)

#define PATCH_SYM_SWITCH(sym, hex, boolean) patchOffsetSym((uintptr_t)dlsym(dlopen(targetLibName, 4), oxorany(sym)), oxorany(hex), boolean)
#define PATCH_LIB_SYM_SWITCH(lib, sym, hex, boolean) patchOffsetSym((uintptr_t)dlsym(dlopen(oxorany(lib), 4), oxorany(sym)), oxorany(hex), boolean)

// Restore macros
#define RESTORE(offset) patchOffset(targetLibName, string2Offset(oxorany(offset)), "", false)
#define RESTORE_LIB(lib, offset) patchOffset(oxorany(lib), string2Offset(oxorany(offset)), "", false)

#define RESTORE_SYM(sym) patchOffsetSym((uintptr_t)dlsym(dlopen(targetLibName, 4), oxorany(sym)), "", false)
#define RESTORE_LIB_SYM(lib, sym) patchOffsetSym((uintptr_t)dlsym(dlopen(oxorany(lib), 4), oxorany(sym)), "", false)

#endif // SANDHOOK_MACROS_H