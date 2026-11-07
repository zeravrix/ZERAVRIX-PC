#pragma once

#include <algorithm>
#include <cstddef>

namespace zeravrix::reader {

enum class PageLayout { Single, Double, Auto, Vertical };
enum class ReadingDirection { Ltr, Rtl };

struct ReaderState {
    std::size_t page_index = 0;
    std::size_t page_count = 0;
    PageLayout layout = PageLayout::Auto;
    ReadingDirection direction = ReadingDirection::Ltr;
    float zoom = 1.0f;
    float pan_x = 0.0f;
    float pan_y = 0.0f;
};

class ReaderController final {
public:
    explicit ReaderController(std::size_t page_count = 0) { set_page_count(page_count); }

    ReaderState const& state() const noexcept { return state_; }

    void set_page_count(std::size_t page_count) {
        state_.page_count = page_count;
        state_.page_index = page_count == 0 ? 0 : std::min(state_.page_index, page_count - 1);
        reset_pan_if_fit();
    }

    void set_page_index(std::size_t index) {
        state_.page_index = state_.page_count == 0 ? 0 : std::min(index, state_.page_count - 1);
        reset_pan_if_fit();
    }

    void next() { advance(+1); }
    void previous() { advance(-1); }

    void set_layout(PageLayout layout) {
        state_.layout = layout;
        reset_pan_if_fit();
    }

    void cycle_layout() {
        state_.layout = state_.layout == PageLayout::Single ? PageLayout::Double
            : state_.layout == PageLayout::Double ? PageLayout::Auto
            : state_.layout == PageLayout::Auto ? PageLayout::Vertical
            : PageLayout::Single;
        reset_pan_if_fit();
    }

    void toggle_direction() noexcept {
        state_.direction = state_.direction == ReadingDirection::Ltr ? ReadingDirection::Rtl : ReadingDirection::Ltr;
    }

    void set_zoom(float zoom) {
        state_.zoom = std::clamp(zoom, 0.25f, 4.0f);
        reset_pan_if_fit();
    }

    void pan(float delta_x, float delta_y) {
        if (state_.zoom > 1.0f) {
            state_.pan_x += delta_x;
            state_.pan_y += delta_y;
        } else {
            reset_pan_if_fit();
        }
    }

private:
    void advance(int delta) {
        if (state_.page_count == 0) return;
        const auto step = state_.layout == PageLayout::Double ? 2u : 1u;
        const auto signed_index = static_cast<long long>(state_.page_index) + static_cast<long long>(delta) * step;
        state_.page_index = static_cast<std::size_t>(std::clamp<long long>(signed_index, 0, static_cast<long long>(state_.page_count - 1)));
        reset_pan_if_fit();
    }

    void reset_pan_if_fit() noexcept {
        if (state_.zoom <= 1.0f) state_.pan_x = state_.pan_y = 0.0f;
    }

    ReaderState state_{};
};

} // namespace zeravrix::reader
