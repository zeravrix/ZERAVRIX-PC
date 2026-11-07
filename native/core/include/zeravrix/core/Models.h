#pragma once

#include <cstdint>
#include <optional>
#include <string>
#include <unordered_map>
#include <vector>

namespace zeravrix::core {

struct Manga {
    std::string id;
    std::string title;
    std::optional<std::string> cover_url;
    std::string description;
    std::int64_t source_id = 0;
    std::optional<std::string> author;
    std::optional<std::string> artist;
    std::vector<std::string> genres;
    std::optional<std::string> status;
    std::optional<std::int32_t> cached_chapter_count;
};

struct Chapter {
    std::string id;
    std::string manga_id;
    std::string title;
    std::optional<float> number;
    std::int64_t source_id = 0;
    std::optional<std::int32_t> volume;
    std::optional<std::string> scanlator;
    std::optional<std::int64_t> upload_date;
};

struct MangaPage {
    std::int32_t index = 0;
    std::string image_url;
    std::optional<std::string> referer;
    std::unordered_map<std::string, std::string> headers;
    bool chapter_end = false;
    std::optional<std::string> chapter_label;
};

struct ReadingProgress {
    std::string manga_id;
    std::string chapter_id;
    std::int32_t page_index = 0;
    std::optional<float> chapter_number;
    std::int64_t updated_at = 0;
};

} // namespace zeravrix::core
