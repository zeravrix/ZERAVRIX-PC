#pragma once

#include "zeravrix/reader/BoundedLruCache.h"

#include <algorithm>
#include <atomic>
#include <cstdint>
#include <cstddef>
#include <filesystem>
#include <memory>
#include <optional>
#include <string>
#include <utility>
#include <vector>

namespace zeravrix::reader {

struct PageDescriptor {
    std::size_t index = 0;
    std::string image_url;
    std::string referer;
    std::filesystem::path disk_path;
    bool chapter_end = false;
};

struct DecodedImage {
    // The platform-specific implementation will own a WIC/Direct2D/WinUI
    // resource. This neutral shell keeps byte cost explicit for cache policy.
    std::shared_ptr<void> native_resource;
    std::size_t byte_cost = 0;
};

class ReaderPipeline final {
public:
    explicit ReaderPipeline(std::size_t max_decoded_bytes = 256ull * 1024ull * 1024ull,
                            std::size_t prefetch_radius = 2)
        : decoded_(max_decoded_bytes), prefetch_radius_(prefetch_radius) {}

    void set_pages(std::vector<PageDescriptor> pages) {
        pages_ = std::move(pages);
        generation_.fetch_add(1, std::memory_order_relaxed);
        decoded_.clear();
    }

    std::vector<PageDescriptor> const& pages() const noexcept { return pages_; }

    std::uint64_t cancel_obsolete_requests() noexcept {
        return generation_.fetch_add(1, std::memory_order_relaxed) + 1;
    }

    std::uint64_t generation() const noexcept { return generation_.load(std::memory_order_relaxed); }

    /** Returns a bounded viewport window in reading order, without duplicates. */
    std::vector<std::size_t> prefetch_indices(std::size_t visible_index,
                                              std::size_t visible_count = 1,
                                              bool rtl = false) const {
        if (pages_.empty()) return {};
        const auto first = std::min(visible_index, pages_.size() - 1);
        const auto last = std::min(first + visible_count - 1, pages_.size() - 1);
        const auto before = std::min(prefetch_radius_, first);
        const auto after = std::min(prefetch_radius_, pages_.size() - 1 - last);
        std::vector<std::size_t> result;
        result.reserve((last - first + 1) + before + after);
        for (std::size_t i = first - before; i <= last + after; ++i) result.push_back(i);
        if (rtl) std::reverse(result.begin(), result.end());
        return result;
    }

    void put_decoded(std::size_t page_index, DecodedImage image) {
        const auto cost = image.byte_cost;
        decoded_.put(page_index, std::move(image), cost);
    }

    std::optional<DecodedImage> decoded(std::size_t page_index) { return decoded_.get(page_index); }

    std::size_t decoded_count() const { return decoded_.size(); }
    std::size_t decoded_bytes() const { return decoded_.current_bytes(); }

private:
    std::vector<PageDescriptor> pages_;
    BoundedLruCache<std::size_t, DecodedImage> decoded_;
    std::size_t prefetch_radius_;
    std::atomic<std::uint64_t> generation_{1};
};

} // namespace zeravrix::reader
