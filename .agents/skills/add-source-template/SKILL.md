---
name: add-source-template
description: Use when adding a manga/manhwa site to ZERAVRIX using a native template family (engine E2) instead of a bespoke parser.
---
# Add a native source (blueprint §10.4, §18.2)
1. Identify the site's engine family (Madara, MangaThemesia, custom JSON API, etc.) by inspecting its HTML/API. Record evidence (URL patterns, selectors) in the source file header.
2. Prefer an official API if one exists (e.g. MangaDex). Respect the site's terms; set a polite rate limit.
3. Add a config entry (id, name, base URL, language, content rating, template, overrides) - not new code, unless the template cannot express it.
4. Record fixtures (HTML/JSON) for popular, search, details, chapters, pages under `core-sources/src/test/resources/fixtures/<id>/`.
5. Write tests against fixtures. Add the id to the nightly canary list.
6. Adult sites: `contentRating = ADULT`; excluded from the Store build automatically.
7. Add an icon (favicon into resources, with fallback letter tile).
