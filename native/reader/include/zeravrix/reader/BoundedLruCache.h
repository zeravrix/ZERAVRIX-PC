#pragma once

#include <cstddef>
#include <list>
#include <mutex>
#include <optional>
#include <unordered_map>
#include <utility>

namespace zeravrix::reader {

/**
 * Thread-safe LRU cache bounded by total caller-supplied byte cost.
 * Values are evicted before the cache can exceed max_bytes; zero-cost values
 * are still bounded by entry count through the same eviction path.
 */
template <typename Key, typename Value, typename Hash = std::hash<Key>>
class BoundedLruCache final {
public:
    explicit BoundedLruCache(std::size_t max_bytes) : max_bytes_(max_bytes) {}

    BoundedLruCache(BoundedLruCache const&) = delete;
    BoundedLruCache& operator=(BoundedLruCache const&) = delete;

    std::optional<Value> get(Key const& key) {
        std::scoped_lock lock(mutex_);
        auto found = entries_.find(key);
        if (found == entries_.end()) return std::nullopt;
        items_.splice(items_.begin(), items_, found->second);
        return found->second->value;
    }

    void put(Key key, Value value, std::size_t byte_cost) {
        std::scoped_lock lock(mutex_);
        if (byte_cost > max_bytes_) {
            erase_locked(key);
            return;
        }
        auto found = entries_.find(key);
        if (found != entries_.end()) {
            current_bytes_ -= found->second->byte_cost;
            found->second->value = std::move(value);
            found->second->byte_cost = byte_cost;
            current_bytes_ += byte_cost;
            items_.splice(items_.begin(), items_, found->second);
        } else {
            items_.push_front(Item{std::move(key), std::move(value), byte_cost});
            entries_[items_.front().key] = items_.begin();
            current_bytes_ += byte_cost;
        }
        evict_locked();
    }

    bool erase(Key const& key) {
        std::scoped_lock lock(mutex_);
        return erase_locked(key);
    }

    void clear() {
        std::scoped_lock lock(mutex_);
        items_.clear();
        entries_.clear();
        current_bytes_ = 0;
    }

    std::size_t size() const {
        std::scoped_lock lock(mutex_);
        return items_.size();
    }

    std::size_t current_bytes() const {
        std::scoped_lock lock(mutex_);
        return current_bytes_;
    }

    std::size_t max_bytes() const noexcept { return max_bytes_; }

private:
    struct Item {
        Key key;
        Value value;
        std::size_t byte_cost;
    };
    using List = std::list<Item>;
    using Iterator = typename List::iterator;

    bool erase_locked(Key const& key) {
        auto found = entries_.find(key);
        if (found == entries_.end()) return false;
        current_bytes_ -= found->second->byte_cost;
        items_.erase(found->second);
        entries_.erase(found);
        return true;
    }

    void evict_locked() {
        while (current_bytes_ > max_bytes_ && !items_.empty()) {
            auto oldest = std::prev(items_.end());
            current_bytes_ -= oldest->byte_cost;
            entries_.erase(oldest->key);
            items_.erase(oldest);
        }
    }

    const std::size_t max_bytes_;
    mutable std::mutex mutex_;
    List items_;
    std::unordered_map<Key, Iterator, Hash> entries_;
    std::size_t current_bytes_ = 0;
};

} // namespace zeravrix::reader
