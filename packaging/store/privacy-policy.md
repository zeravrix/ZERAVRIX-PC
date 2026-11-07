# ZERAVRIX Privacy Policy

**Effective date:** 24 August 2026  
**Product:** ZERAVRIX for Windows (Store Edition)  
**Publisher:** U1M

> **Publishing requirement:** Before submitting to Microsoft Store, replace the contact placeholder in this document with a monitored legal/support email address and publish this policy at a stable HTTPS URL. Enter that exact URL in Partner Center.

## Scope

This policy describes the Windows Store Edition of ZERAVRIX, a native manga, manhua, and manhwa reader. The application is designed to keep reading history, library data, downloaded chapters, cached covers, source preferences, and optional local music data on the user’s Windows device.

ZERAVRIX does not provide an account system, advertising identifier, analytics SDK, crash-reporting service, or in-app social profile. It does not intentionally sell personal information or use personal information for targeted advertising.

## Information stored on the device

ZERAVRIX stores application settings, library entries, reading history, chapter progress, downloaded chapter images, cached cover images, source-specific configuration, backup archives, and logs or temporary files required for normal operation. If the user enables My Music, ZERAVRIX reads the local audio files and folders the user selects and stores playlist metadata locally. The user can remove this data using the application’s controls or by deleting the ZERAVRIX data directory after closing the application.

The application may store cookies or a copied HTTP Cookie header when the user explicitly supplies it for a source that requires verification. Cookies are used only for the selected source’s requests and are kept in the local application data directory. Users should not paste credentials or secrets into a cookie field unless they understand the source’s requirements.

## Network requests and source providers

When the user browses a bundled source, searches, opens a manga, loads chapters or pages, downloads content, or refreshes a source, ZERAVRIX sends network requests to that source’s configured website or API. Those providers may receive the IP address, request metadata, user agent, cookies, and the requested catalogue or chapter information as part of ordinary HTTP operation. Their own privacy policies and terms apply.

ZERAVRIX does not upload the user’s local library, reading history, downloaded files, playlists, or backups to U1M. The application does not load remote executable extensions or source JARs in the Store Edition.

## Browser verification

For CAPTCHA or manual source verification, ZERAVRIX opens the source in the user’s default Windows browser. The user completes verification manually. ZERAVRIX does not automate CAPTCHA solving or bypass access controls. If the user chooses to copy a Cookie header back into the application, it is saved locally as described above.

## Children and adult content

The Store Edition permanently excludes adult-labelled sources and does not expose an adult-content setting. The user interface and bundled source registry are intended for the submitted Store age rating. Source providers remain responsible for the content they publish, and U1M cannot guarantee the classification of every remote catalogue item; inappropriate material should be reported through the source provider and U1M support contact.

## Security and retention

ZERAVRIX uses HTTPS where supported by the source adapter and keeps application data within the user’s Windows profile. No security measure is perfect. Users should protect their Windows account and should not share backup archives containing local history or source cookies. Data remains on the device until the user deletes it or uninstalls/clears the application data.

## Changes and contact

U1M may update this policy when application behavior changes. The current version will be published at the same HTTPS URL used in the Store listing.

**Privacy contact:** `REPLACE-BEFORE-SUBMISSION@example.com`  
**Support community:** [ZERAVRIX support Discord](https://discord.gg/YrrmFNCvXx)

## References

[1]: https://learn.microsoft.com/en-us/windows/apps/publish/store-policies "Microsoft Store Policies"
[2]: https://learn.microsoft.com/en-us/windows/apps/publish/publish-your-app/msix/create-app-submission "Create an app submission"
