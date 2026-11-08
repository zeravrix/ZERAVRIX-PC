# ZERAVRIX PC performance rules

- Keep network, parsing, image decoding and disk I/O off the UI thread.
- Use bounded concurrent downloads rather than one thread per image.
- Cache decoded images with an LRU memory cache and a disk cache.
- Decode images near the displayed size when possible.
- Prefetch the next pages/chapter while the reader is idle.
- Avoid recreating reader surfaces during page changes.
- Use hardware accelerated desktop rendering.
- Store downloads/cache under a configurable user-data directory.
- Never block startup waiting for a source or remote image.
- Persist reading position asynchronously.
