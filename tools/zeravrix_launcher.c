#define UNICODE
#define _UNICODE
#include <windows.h>
#include <wchar.h>
#include "zeravrix_launcher_res.h"

static void base_dir(wchar_t *out, size_t cap) {
    DWORD n = GetModuleFileNameW(NULL, out, (DWORD)cap);
    if (n == 0 || n >= cap) { out[0] = L'\0'; return; }
    wchar_t *slash = wcsrchr(out, L'\\');
    if (slash) *slash = L'\0';
}

int WINAPI wWinMain(HINSTANCE instance, HINSTANCE previous, PWSTR command_line, int show) {
    (void)instance; (void)previous; (void)command_line; (void)show;
    wchar_t dir[MAX_PATH * 4];
    base_dir(dir, sizeof(dir) / sizeof(dir[0]));
    if (!dir[0]) return 2;
    SetCurrentDirectoryW(dir);
    wchar_t java_path[MAX_PATH * 4];
    wchar_t command[MAX_PATH * 8];
    _snwprintf(java_path, sizeof(java_path) / sizeof(java_path[0]), L"%ls\\runtime\\bin\\javaw.exe", dir);
    _snwprintf(command, sizeof(command) / sizeof(command[0]),
        L"\"%ls\" -Djpackage.app-version=1.4.0 -Xms128m -Xmx2048m -XX:MaxMetaspaceSize=256m -XX:MaxDirectMemorySize=512m -XX:+UseG1GC -XX:+UseStringDeduplication -Dcompose.application.resources.dir=resources -Dcompose.application.configure.swing.globals=true -Dskiko.library.path=app -cp \"app\\*\" com.zeravrix.zeravrix.MainKt",
        java_path);
    STARTUPINFOW startup = {0};
    startup.cb = sizeof(startup);
    PROCESS_INFORMATION process = {0};
    if (!CreateProcessW(java_path, command, NULL, NULL, FALSE, 0, NULL, dir, &startup, &process)) return (int)GetLastError();
    WaitForSingleObject(process.hProcess, INFINITE);
    DWORD exit_code = 0;
    GetExitCodeProcess(process.hProcess, &exit_code);
    CloseHandle(process.hThread);
    CloseHandle(process.hProcess);
    return (int)exit_code;
}

/* The icon resource is compiled into the launcher and becomes the Windows process icon. */
int zeravrix_launcher_resource_anchor = IDI_ZERAVRIX;
