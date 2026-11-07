#pragma once

#ifdef _WIN32

#include <cstdint>
#include <filesystem>
#include <memory>
#include <vector>

namespace zeravrix::reader {

struct NativeImageData {
    std::uint32_t width = 0;
    std::uint32_t height = 0;
    std::uint32_t stride = 0;
    std::vector<std::byte> pixels;

    std::size_t byte_cost() const noexcept { return pixels.size(); }
};

class WicImageDecoder final {
public:
    std::shared_ptr<NativeImageData> decode(std::filesystem::path const& path) const;
};

} // namespace zeravrix::reader

#endif
