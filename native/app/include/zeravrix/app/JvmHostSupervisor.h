#pragma once

#include <chrono>
#include <filesystem>
#include <memory>
#include <string>

#include "zeravrix/ipc/ipc_client.hpp"

namespace zeravrix::app {

#ifdef _WIN32

class JvmHostSupervisor final {
public:
    JvmHostSupervisor() = default;
    ~JvmHostSupervisor();

    JvmHostSupervisor(JvmHostSupervisor const&) = delete;
    JvmHostSupervisor& operator=(JvmHostSupervisor const&) = delete;

    bool start(std::filesystem::path const& host_executable,
               std::filesystem::path const& data_root);
    bool ensure_running();
    void stop();

    zeravrix::ipc::IpcClient* client() const noexcept { return client_.get(); }

private:
    bool launch(std::filesystem::path const& host_executable,
                std::filesystem::path const& data_root);
    bool process_alive() const;

    std::filesystem::path host_executable_;
    std::filesystem::path data_root_;
    void* process_handle_ = nullptr;
    std::unique_ptr<zeravrix::ipc::IpcClient> client_;
    std::chrono::steady_clock::time_point last_restart_{};
};

#endif

} // namespace zeravrix::app
