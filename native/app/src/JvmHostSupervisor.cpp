#include "zeravrix/app/JvmHostSupervisor.h"

#ifdef _WIN32

#include <Windows.h>

#include <string>
#include <vector>

namespace zeravrix::app {

namespace {

HANDLE as_handle(void* value) noexcept {
    return static_cast<HANDLE>(value);
}

std::wstring quote(std::filesystem::path const& value) {
    return L"\"" + value.wstring() + L"\"";
}

} // namespace

JvmHostSupervisor::~JvmHostSupervisor() {
    stop();
}

bool JvmHostSupervisor::start(std::filesystem::path const& host_executable,
                              std::filesystem::path const& data_root) {
    stop();
    host_executable_ = host_executable;
    data_root_ = data_root;
    return launch(host_executable_, data_root_);
}

bool JvmHostSupervisor::ensure_running() {
    if (process_alive()) return true;
    const auto now = std::chrono::steady_clock::now();
    if (last_restart_ != std::chrono::steady_clock::time_point{} &&
        now - last_restart_ < std::chrono::seconds(2)) {
        return false;
    }
    last_restart_ = now;
    return launch(host_executable_, data_root_);
}

void JvmHostSupervisor::stop() {
    client_.reset();
    auto process = as_handle(process_handle_);
    if (!process) return;
    if (WaitForSingleObject(process, 2000) == WAIT_TIMEOUT) {
        TerminateProcess(process, 1);
        WaitForSingleObject(process, 1000);
    }
    CloseHandle(process);
    process_handle_ = nullptr;
}

bool JvmHostSupervisor::launch(std::filesystem::path const& host_executable,
                               std::filesystem::path const& data_root) {
    if (!std::filesystem::is_regular_file(host_executable)) return false;
    std::filesystem::create_directories(data_root);

    SECURITY_ATTRIBUTES security{};
    security.nLength = sizeof(security);
    security.bInheritHandle = TRUE;

    HANDLE child_stdin_read = nullptr;
    HANDLE parent_stdin_write = nullptr;
    HANDLE parent_stdout_read = nullptr;
    HANDLE child_stdout_write = nullptr;
    if (!CreatePipe(&child_stdin_read, &parent_stdin_write, &security, 0)) return false;
    if (!SetHandleInformation(parent_stdin_write, HANDLE_FLAG_INHERIT, 0)) {
        CloseHandle(child_stdin_read); CloseHandle(parent_stdin_write); return false;
    }
    if (!CreatePipe(&parent_stdout_read, &child_stdout_write, &security, 0)) {
        CloseHandle(child_stdin_read); CloseHandle(parent_stdin_write); return false;
    }
    if (!SetHandleInformation(parent_stdout_read, HANDLE_FLAG_INHERIT, 0)) {
        CloseHandle(child_stdin_read); CloseHandle(parent_stdin_write);
        CloseHandle(parent_stdout_read); CloseHandle(child_stdout_write); return false;
    }

    STARTUPINFOW startup{};
    startup.cb = sizeof(startup);
    startup.dwFlags = STARTF_USESTDHANDLES;
    startup.hStdInput = child_stdin_read;
    startup.hStdOutput = child_stdout_write;
    startup.hStdError = GetStdHandle(STD_ERROR_HANDLE);

    PROCESS_INFORMATION process_info{};
    std::wstring command = quote(host_executable) + L" --stdio --root " + quote(data_root);
    std::vector<wchar_t> mutable_command(command.begin(), command.end());
    mutable_command.push_back(L'\0');
    const BOOL created = CreateProcessW(
        nullptr,
        mutable_command.data(),
        nullptr,
        nullptr,
        TRUE,
        CREATE_NO_WINDOW,
        nullptr,
        host_executable.parent_path().wstring().c_str(),
        &startup,
        &process_info);

    CloseHandle(child_stdin_read);
    CloseHandle(child_stdout_write);
    if (!created) {
        CloseHandle(parent_stdin_write);
        CloseHandle(parent_stdout_read);
        return false;
    }
    CloseHandle(process_info.hThread);
    process_handle_ = process_info.hProcess;
    client_ = std::make_unique<zeravrix::ipc::Win32StreamClient>(parent_stdout_read, parent_stdin_write);
    return client_->connect();
}

bool JvmHostSupervisor::process_alive() const {
    auto process = as_handle(process_handle_);
    if (!process) return false;
    DWORD status = 0;
    if (!GetExitCodeProcess(process, &status)) return false;
    return status == STILL_ACTIVE;
}

} // namespace zeravrix::app

#endif
