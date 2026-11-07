#include "zeravrix/reader/PageDiskCache.h"
#include "zeravrix/reader/ReaderPipeline.h"
#include "zeravrix/reader/ReaderState.h"

#include <cassert>
#include <filesystem>
#include <memory>
#include <vector>

int main() {
    zeravrix::reader::ReaderPipeline pipeline(10, 1);
    pipeline.set_pages({
        {0, "page-0"}, {1, "page-1"}, {2, "page-2"}, {3, "page-3"}, {4, "page-4"},
    });

    const auto window = pipeline.prefetch_indices(2, 1, false);
    assert((window == std::vector<std::size_t>{1, 2, 3}));
    const auto rtl_window = pipeline.prefetch_indices(2, 1, true);
    assert((rtl_window == std::vector<std::size_t>{3, 2, 1}));

    pipeline.put_decoded(0, {std::make_shared<int>(0), 6});
    pipeline.put_decoded(1, {std::make_shared<int>(1), 6});
    assert(pipeline.decoded_bytes() <= 10);
    assert(!pipeline.decoded(0).has_value());
    assert(pipeline.decoded(1).has_value());

    const auto before = pipeline.generation();
    const auto after = pipeline.cancel_obsolete_requests();
    assert(after > before);
    pipeline.set_pages({{0, "new-page-0"}});
    assert(pipeline.generation() > after);
    assert(pipeline.decoded_count() == 0);

    const auto disk_root = std::filesystem::temp_directory_path() / "zeravrix-native-cache-check";
    std::filesystem::remove_all(disk_root);
    zeravrix::reader::PageDiskCache disk_cache(disk_root);
    const std::array<std::byte, 4> bytes{std::byte{0x01}, std::byte{0x02}, std::byte{0x03}, std::byte{0x04}};
    assert(disk_cache.store("https://example.invalid/page?x=../unsafe", bytes));
    assert(disk_cache.existing("https://example.invalid/page?x=../unsafe").has_value());
    assert(disk_cache.read("https://example.invalid/page?x=../unsafe") == std::vector<std::byte>(bytes.begin(), bytes.end()));
    assert(disk_cache.path_for("https://example.invalid/page?x=../unsafe").parent_path() == disk_root);
    std::filesystem::remove_all(disk_root);

    zeravrix::reader::ReaderController controller(8);
    controller.set_layout(zeravrix::reader::PageLayout::Double);
    controller.next();
    assert(controller.state().page_index == 2);
    controller.set_zoom(10.0f);
    assert(controller.state().zoom == 4.0f);
    controller.pan(20.0f, -10.0f);
    controller.set_zoom(1.0f);
    assert(controller.state().pan_x == 0.0f && controller.state().pan_y == 0.0f);
    controller.toggle_direction();
    assert(controller.state().direction == zeravrix::reader::ReadingDirection::Rtl);
    return 0;
}
