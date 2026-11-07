# ZERAVRIX desktop reader

The reader is designed around large PC monitors rather than a phone viewport.

Modes:
- AUTO: use two-page spread when both pages can fit comfortably.
- SINGLE: one page centered and fitted to the viewport.
- DOUBLE: two consecutive pages side-by-side.
- RTL reverses page ordering for right-to-left manga.

Zoom:
- Fit-to-page is the default.
- Zoom is allowed and preserves aspect ratio.
- Mouse wheel + Ctrl can change zoom.
- Dragging pans a zoomed page.
- Reset returns to fit-to-page.

Performance:
- Decode visible pages first.
- Prefetch the next/previous spread in the background.
- Keep decoded images in a bounded memory cache.
- Disk cache stores downloaded page bytes.
- Never block the UI thread on network or disk I/O.
