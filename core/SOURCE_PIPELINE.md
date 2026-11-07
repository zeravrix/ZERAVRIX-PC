# Source pipeline

Search results are source-owned DTOs and are converted into app-domain records.
Page URLs are not displayed directly by the UI. The reader receives a PageRef and asks
the image cache for a local/streamed image. This keeps source/network work off the UI thread.

Recommended PC flow:
Source -> coroutine dispatcher -> repository -> cache/database -> UI state -> reader.
