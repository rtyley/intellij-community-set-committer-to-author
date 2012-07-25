/*
 * Copyright 2000-2012 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

#include "IdeaWin32.h"
#include <windows.h>


static jfieldID nameID = NULL;
static jfieldID attributesID = NULL;
static jfieldID timestampID = NULL;
static jfieldID lengthID = NULL;

#define BROKEN_SYMLINK_ATTR -1

static jclass getFileInfoClass(JNIEnv *env) {
    return env->FindClass("com/intellij/openapi/util/io/win32/FileInfo");
}

static HANDLE FindFileInner(JNIEnv *env, jstring path, LPWIN32_FIND_DATA lpData) {
    const jchar* str = env->GetStringChars(path, 0);
    const HANDLE h = FindFirstFile((LPCWSTR)str, lpData);
    env->ReleaseStringChars(path, str);
    return h;
}

static bool CopyObjectArray(JNIEnv *env, jobjectArray dst, jobjectArray src, jint count) {
    for (int i = 0; i < count; i++) {
        jobject p = env->GetObjectArrayElement(src, i);
        env->SetObjectArrayElement(dst, i, p);
        env->DeleteLocalRef(p);
    }
    return true;
}

static inline LONGLONG pairToInt64(DWORD lowPart, DWORD highPart) {
    ULARGE_INTEGER large;
    large.LowPart = lowPart;
    large.HighPart = highPart;
    return large.QuadPart;
}

#define IS_SET(flags, flag) ((flags & flag) == flag)
#define FILE_SHARE_ATTRIBUTES (FILE_SHARE_READ | FILE_SHARE_WRITE | FILE_SHARE_DELETE)

static jobject CreateFileInfo(JNIEnv *env, jstring path, bool append, LPWIN32_FIND_DATA lpData, jclass fileInfoClass) {
    DWORD attributes = lpData->dwFileAttributes;
    LONGLONG timestamp = pairToInt64(lpData->ftLastWriteTime.dwLowDateTime, lpData->ftLastWriteTime.dwHighDateTime);
    LONGLONG length = pairToInt64(lpData->nFileSizeLow, lpData->nFileSizeHigh);

    if (IS_SET(attributes, FILE_ATTRIBUTE_REPARSE_POINT)) {
        if (IS_SET(lpData->dwReserved0, IO_REPARSE_TAG_SYMLINK)) {
            attributes = BROKEN_SYMLINK_ATTR;
            timestamp = 0;
            length = 0;

            size_t nameLen = env->GetStringLength(path) + wcslen(lpData->cFileName) + 2;
            wchar_t *lpName = (wchar_t *)malloc(nameLen * sizeof(wchar_t));
            if (lpName != NULL) {
                const jchar *dirName = env->GetStringChars(path, 0);
                wcscpy_s(lpName, nameLen, (LPCWSTR)dirName);
                env->ReleaseStringChars(path, dirName);
                if (append) {
                    wcscat_s(lpName, nameLen, L"\\");
                    wcscat_s(lpName, nameLen, lpData->cFileName);
                }

                // read symlink target attributes
                HANDLE th = CreateFile(lpName, 0, FILE_SHARE_ATTRIBUTES, NULL, OPEN_EXISTING, FILE_FLAG_BACKUP_SEMANTICS, NULL);
                if (th != INVALID_HANDLE_VALUE) {
                    BY_HANDLE_FILE_INFORMATION targetData;
                    if (GetFileInformationByHandle(th, &targetData)) {
                        attributes = targetData.dwFileAttributes | FILE_ATTRIBUTE_REPARSE_POINT;
                        timestamp = pairToInt64(targetData.ftLastWriteTime.dwLowDateTime, targetData.ftLastWriteTime.dwHighDateTime);
                        length = pairToInt64(targetData.nFileSizeLow, targetData.nFileSizeHigh);
                    }
                    CloseHandle(th);
                }
                free(lpName);
            }
        }
        else {
            attributes &= (~ FILE_ATTRIBUTE_REPARSE_POINT);  // keep reparse flag only for symlinks
        }
    }

    jobject o = env->AllocObject(fileInfoClass);
    if (o == NULL) {
        return NULL;
    }

    jstring fileName = env->NewString((jchar*)lpData->cFileName, (jsize)wcslen(lpData->cFileName));
    if (fileName == NULL) {
        return NULL;
    }
    env->SetObjectField(o, nameID, fileName);

    env->SetIntField(o, attributesID, attributes);
    env->SetLongField(o, timestampID, timestamp);
    env->SetLongField(o, lengthID, length);

    return o;
}


JNIEXPORT void JNICALL Java_com_intellij_openapi_util_io_win32_IdeaWin32_initIDs(JNIEnv *env, jclass cls) {
    jclass fileInfoClass = getFileInfoClass(env);
    if (fileInfoClass == NULL) {
        return;
    }

    nameID = env->GetFieldID(fileInfoClass, "name", "Ljava/lang/String;");
    attributesID = env->GetFieldID(fileInfoClass, "attributes", "I");
    timestampID = env->GetFieldID(fileInfoClass, "timestamp", "J");
    lengthID = env->GetFieldID(fileInfoClass, "length", "J");
}


JNIEXPORT jobject JNICALL Java_com_intellij_openapi_util_io_win32_IdeaWin32_getInfo(JNIEnv *env, jobject method, jstring path) {
    WIN32_FIND_DATA data;
    HANDLE h = FindFileInner(env, path, &data);

    if (h == INVALID_HANDLE_VALUE) {
        if (GetLastError() != ERROR_ACCESS_DENIED) {
            return NULL;
        }

        // there is a chance that directory listing is denied but direct file access will succeed
        WIN32_FILE_ATTRIBUTE_DATA attrData;
        const jchar* str = env->GetStringChars(path, 0);
        BOOL res = GetFileAttributesEx((LPCWSTR)str, GetFileExInfoStandard, &attrData);
        env->ReleaseStringChars(path, str);
        if (!res) {
            return NULL;
        }

        data.dwFileAttributes = attrData.dwFileAttributes;
        data.dwReserved0 = 0;
        data.ftLastWriteTime = attrData.ftLastWriteTime;
        data.nFileSizeLow = attrData.nFileSizeLow;
        data.nFileSizeHigh = attrData.nFileSizeHigh;
    }
    else {
        FindClose(h);
    }

    jclass fileInfoClass = getFileInfoClass(env);
    if (fileInfoClass == NULL) {
        return NULL;
    }

    return CreateFileInfo(env, path, false, &data, fileInfoClass);
}


JNIEXPORT jstring JNICALL Java_com_intellij_openapi_util_io_win32_IdeaWin32_resolveSymLink(JNIEnv *env, jobject method, jstring path) {
    WIN32_FIND_DATA data;
    HANDLE h = FindFileInner(env, path, &data);
    if (h == INVALID_HANDLE_VALUE) {
        return NULL;
    }
    FindClose(h);	

    if (!IS_SET(data.dwFileAttributes, FILE_ATTRIBUTE_REPARSE_POINT) || !IS_SET(data.dwReserved0, IO_REPARSE_TAG_SYMLINK)) {
        return NULL;
    }

    const jchar* str = env->GetStringChars(path, 0);
    HANDLE th = CreateFile((LPCWSTR)str, 0, FILE_SHARE_ATTRIBUTES, NULL, OPEN_EXISTING, FILE_FLAG_BACKUP_SEMANTICS, NULL);
    env->ReleaseStringChars(path, str);
    if (th == INVALID_HANDLE_VALUE) {
        return NULL;
    }

    jstring result = NULL;

    TCHAR name[MAX_PATH];
    DWORD len = GetFinalPathNameByHandle(th, name, MAX_PATH, 0);
    if (len > 0) {
        if (len < MAX_PATH) {
            result = env->NewString((jchar *)name, len);
        }
        else {
            TCHAR *name = (TCHAR *)malloc(sizeof(TCHAR) * (len + 1));
            if (name != NULL) {
                len = GetFinalPathNameByHandle(th, name, len, 0);
                if (len > 0) {
                    result = env->NewString((jchar *)name, len);
                }
                free(name);
            }
        }
    }

    CloseHandle(th);
    return result;
}


JNIEXPORT jobjectArray JNICALL Java_com_intellij_openapi_util_io_win32_IdeaWin32_listChildren(JNIEnv *env, jobject method, jstring path) {
    WIN32_FIND_DATA data;
    HANDLE h = FindFileInner(env, path, &data);
    if (h == INVALID_HANDLE_VALUE) {
        return NULL;
    }

    jclass fileInfoClass = getFileInfoClass(env);
    if (fileInfoClass == NULL) {
        return NULL;
    }

    jobjectArray rv, old;
    int len = 0, maxlen = 16;
    rv = env->NewObjectArray(maxlen, fileInfoClass, NULL);
    if (rv == NULL) {
        goto error;
    }

    do {
        if (len == maxlen) {
            old = rv;
            rv = env->NewObjectArray(maxlen <<= 1, fileInfoClass, NULL);
            if (rv == NULL || !CopyObjectArray(env, rv, old, len)) {
                goto error;
            }
            env->DeleteLocalRef(old);
        }
        jobject o = CreateFileInfo(env, path, true, &data, fileInfoClass);
        env->SetObjectArrayElement(rv, len++, o);
        env->DeleteLocalRef(o);
    }
    while (FindNextFile(h, &data));

    FindClose(h);	

    old = rv;
    rv = env->NewObjectArray(len, fileInfoClass, NULL);
    if (rv == NULL || !CopyObjectArray(env, rv, old, len)) {
        goto error;
    }
    return rv;

error:
    FindClose(h);	
    return NULL;
}


BOOL APIENTRY DllMain(HMODULE hModule, DWORD ul_reason_for_call, LPVOID lpReserved) {
    return TRUE;
}
