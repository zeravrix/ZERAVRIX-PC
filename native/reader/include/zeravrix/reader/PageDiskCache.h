#pragma once

#include <array>
#include <cstddef>
#include <cstdint>
#include <filesystem>
#include <fstream>
#include <iterator>
#include <optional>
#include <span>
#include <string>
#include <utility>
#include <vector>

namespace zeravrix::reader {

class PageDiskCache final {
public:
    explicit PageDiskCache(std::filesystem::path root) : root_(std::move(root)) {
        std::filesystem::create_directories(root_);
    }

    std::optional<std::filesystem::path> existing(std::string const& cache_key) const {
        const auto path = path_for(cache_key);
        std::error_code error;
        if (!std::filesystem::is_regular_file(path, error) || std::filesystem::file_size(path, error) == 0) return std::nullopt;
        return path;
    }

    bool store(std::string const& cache_key, std::span<const std::byte> bytes) {
        if (bytes.empty()) return false;
        const auto path = path_for(cache_key);
        const auto temporary = path.string() + ".part";
        {
            std::ofstream output(temporary, std::ios::binary | std::ios::trunc);
            if (!output) return false;
            output.write(reinterpret_cast<char const*>(bytes.data()), static_cast<std::streamsize>(bytes.size()));
            output.flush();
            if (!output) return false;
        }
        std::error_code error;
        std::filesystem::rename(temporary, path, error);
        if (error) {
            std::filesystem::remove(path, error);
            error.clear();
            std::filesystem::rename(temporary, path, error);
        }
        if (error) std::filesystem::remove(temporary, error);
        return !error;
    }

    std::vector<std::byte> read(std::string const& cache_key) const {
        const auto path = existing(cache_key).value_or(std::filesystem::path{});
        if (path.empty()) return {};
        std::ifstream input(path, std::ios::binary);
        if (!input) return {};
        std::vector<char> raw((std::istreambuf_iterator<char>(input)), {});
        std::vector<std::byte> bytes(raw.size());
        for (std::size_t index = 0; index < raw.size(); ++index) bytes[index] = static_cast<std::byte>(static_cast<unsigned char>(raw[index]));
        return bytes;
    }

    std::filesystem::path path_for(std::string const& cache_key) const {
        return root_ / (hex_hash(cache_key) + ".page");
    }

private:
    static std::string hex_hash(std::string const& value) {
        // Two independent FNV-1a lanes provide a deterministic, path-safe key.
        std::uint64_t first = 1469598103934665603ull;
        std::uint64_t second = 1099511628211ull;
        for (unsigned char byte : value) {
            first ^= byte; first *= 1099511628211ull;
            second ^= static_cast<unsigned char>(byte + 17u); second *= 1469598103934665603ull;
        }
        constexpr char digits[] = "0123456789abcdef";
        std::string result(32, '0');
        for (int i = 0; i < 16; ++i) {
            result[i] = digits[(first >> ((15 - i) * 4)) & 0xF];
            result[16 + i] = digits[(second >> ((15 - i) * 4)) & 0xF];
        }
        return result;
    }

    std::filesystem::path root_;
};

} // namespace zeravrix::reader
