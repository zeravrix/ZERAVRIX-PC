#pragma once

#include <cstdlib>
#include <filesystem>
#include <string>

namespace zeravrix::core {

class StoragePaths final {
public:
    explicit StoragePaths(std::filesystem::path root) : root_(std::move(root)) {}

    static StoragePaths for_current_user() {
#ifdef _WIN32
        if (const auto* profile = std::getenv("USERPROFILE")) return StoragePaths(std::filesystem::path(profile) / ".zeravrix");
#endif
        if (const auto* home = std::getenv("HOME")) return StoragePaths(std::filesystem::path(home) / ".zeravrix");
        return StoragePaths(std::filesystem::temp_directory_path() / ".zeravrix");
    }

    std::filesystem::path root() const { return root_; }
    std::filesystem::path library() const { return root_ / "library"; }
    std::filesystem::path progress() const { return root_ / "progress"; }
    std::filesystem::path history() const { return root_ / "history"; }
    std::filesystem::path downloads() const { return root_ / "downloads"; }
    std::filesystem::path cache() const { return root_ / "cache"; }
    std::filesystem::path sources() const { return root_ / "sources"; }
    std::filesystem::path source_preferences() const { return root_ / "source-prefs"; }
    std::filesystem::path music() const { return root_ / "music"; }
    std::filesystem::path backups() const { return root_ / "backups"; }

    void ensure_directories() const {
        for (const auto& directory : {library(), progress(), history(), downloads(), cache(), sources(), source_preferences(), music(), backups()}) {
            std::filesystem::create_directories(directory);
        }
    }

private:
    std::filesystem::path root_;
};

} // namespace zeravrix::core
