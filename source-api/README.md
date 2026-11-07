# ZERAVRIX Source API

Sources are isolated from the desktop UI. A source should expose:
- search(query)
- details(manga)
- chapters(manga)
- pages(chapter)

The desktop application can load source extensions independently so source updates do not require replacing the main UI.
