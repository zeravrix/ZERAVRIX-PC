#pragma once

#include <cstdint>
#include <istream>
#include <optional>
#include <ostream>
#include <string>
#include <string_view>
#include <utility>

#ifdef _WIN32
#include <windows.h>
#endif

namespace zeravrix::ipc {

inline constexpr std::int32_t kProtocolVersion = 1;

class IpcClient {
public:
    virtual ~IpcClient() = default;
    virtual bool connect() = 0;
    virtual void close() = 0;
    virtual bool send_frame(std::string_view json_line) = 0;
    virtual std::optional<std::string> receive_frame() = 0;
};

/** Deterministic newline-framed transport for protocol tests and diagnostics. */
class StreamClient final : public IpcClient {
public:
    StreamClient(std::istream& input, std::ostream& output) : input_(input), output_(output) {}

    bool connect() override { connected_ = true; return true; }
    void close() override { connected_ = false; }

    bool send_frame(std::string_view json_line) override {
        if (!connected_) return false;
        output_ << json_line << '\n' << std::flush;
        return static_cast<bool>(output_);
    }

    std::optional<std::string> receive_frame() override {
        if (!connected_) return std::nullopt;
        std::string line;
        if (!std::getline(input_, line)) return std::nullopt;
        return line;
    }

private:
    std::istream& input_;
    std::ostream& output_;
    bool connected_ = false;
};

#ifdef _WIN32
/** Transport over inherited Win32 pipe handles used by the native host supervisor. */
class Win32StreamClient final : public IpcClient {
public:
    Win32StreamClient(HANDLE read_handle, HANDLE write_handle)
        : read_handle_(read_handle), write_handle_(write_handle) {}
    ~Win32StreamClient() override { close(); }

    bool connect() override { connected_ = read_handle_ != INVALID_HANDLE_VALUE && write_handle_ != INVALID_HANDLE_VALUE; return connected_; }

    void close() override {
        if (read_handle_ != INVALID_HANDLE_VALUE) { CloseHandle(read_handle_); read_handle_ = INVALID_HANDLE_VALUE; }
        if (write_handle_ != INVALID_HANDLE_VALUE) { CloseHandle(write_handle_); write_handle_ = INVALID_HANDLE_VALUE; }
        connected_ = false;
    }

    bool send_frame(std::string_view json_line) override {
        if (!connected_) return false;
        std::string frame(json_line);
        frame.push_back('\n');
        DWORD written = 0;
        return WriteFile(write_handle_, frame.data(), static_cast<DWORD>(frame.size()), &written, nullptr)
            && written == frame.size();
    }

    std::optional<std::string> receive_frame() override {
        if (!connected_) return std::nullopt;
        std::string line;
        char character = 0;
        DWORD read = 0;
        while (ReadFile(read_handle_, &character, 1, &read, nullptr) && read == 1) {
            if (character == '\n') return line;
            if (character != '\r') line.push_back(character);
        }
        return std::nullopt;
    }

private:
    HANDLE read_handle_ = INVALID_HANDLE_VALUE;
    HANDLE write_handle_ = INVALID_HANDLE_VALUE;
    bool connected_ = false;
};

/** Production local transport. The native app connects; the JVM host listens. */
class NamedPipeClient final : public IpcClient {
public:
    explicit NamedPipeClient(std::wstring pipe_name) : pipe_name_(std::move(pipe_name)) {}
    ~NamedPipeClient() override { close(); }

    bool connect() override {
        close();
        pipe_ = CreateFileW(
            pipe_name_.c_str(),
            GENERIC_READ | GENERIC_WRITE,
            0,
            nullptr,
            OPEN_EXISTING,
            FILE_FLAG_OVERLAPPED,
            nullptr);
        return pipe_ != INVALID_HANDLE_VALUE;
    }

    void close() override {
        if (pipe_ != INVALID_HANDLE_VALUE) {
            CloseHandle(pipe_);
            pipe_ = INVALID_HANDLE_VALUE;
        }
    }

    bool send_frame(std::string_view json_line) override {
        if (pipe_ == INVALID_HANDLE_VALUE) return false;
        std::string frame(json_line);
        frame.push_back('\n');
        DWORD written = 0;
        return WriteFile(pipe_, frame.data(), static_cast<DWORD>(frame.size()), &written, nullptr)
            && written == frame.size();
    }

    std::optional<std::string> receive_frame() override {
        if (pipe_ == INVALID_HANDLE_VALUE) return std::nullopt;
        std::string line;
        char character = 0;
        DWORD read = 0;
        while (ReadFile(pipe_, &character, 1, &read, nullptr) && read == 1) {
            if (character == '\n') return line;
            if (character != '\r') line.push_back(character);
        }
        return std::nullopt;
    }

private:
    std::wstring pipe_name_;
    HANDLE pipe_ = INVALID_HANDLE_VALUE;
};
#endif

} // namespace zeravrix::ipc
