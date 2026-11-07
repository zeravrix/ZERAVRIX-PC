# ZERAVRIX Microsoft Store submission checklist

This checklist is a handoff document, not a certification result. Microsoft makes the final eligibility and certification decision. The Store policy and submission requirements should be rechecked immediately before submission because Microsoft can update them.[1] [2]

## 1. Publisher and identity

| Status | Action | Owner |
|---|---|---|
| [ ] | Create or verify the U1M Partner Center developer account and complete identity verification. | U1M |
| [ ] | Reserve the exact product name **ZERAVRIX**. | U1M |
| [x] | Apply the exact package identity values reported by Partner Center: package name `U1M.Zeravrix` and publisher `CN=D7CFED99-F946-4B71-8F83-D7F3A1DD152D`. | U1M |
| [ ] | Confirm that U1M owns or is authorized to use the ZERAVRIX name, logo, screenshots, and all third-party source or artwork references included in the listing. | U1M |

Microsoft’s Store developer account process and Partner Center are documented in the official onboarding guidance.[3]

## 2. Privacy and support

| Status | Action |
|---|---|
| [ ] | Replace `REPLACE-BEFORE-SUBMISSION@example.com` in `store/privacy-policy.md` with a monitored U1M legal/support address. |
| [ ] | Publish the final privacy policy at a stable HTTPS URL. |
| [ ] | Put the published URL in Partner Center and verify that it is publicly reachable without login. |
| [ ] | Confirm the Discord support URL remains active or replace it with a maintained support URL. |
| [ ] | Recheck the policy against the package’s actual behavior, especially local music access, source cookies, browser verification, downloaded pages, and source-provider requests. |

## 3. Package build

| Status | Action |
|---|---|
| [x] | Build core, source-api, reader, and desktop JARs from the Store Edition source. |
| [x] | Assemble the native Windows x64 launcher and bundled Java runtime payload. |
| [x] | Remove JavaFX WebView and JavaFX JAR payloads. |
| [x] | Remove custom third-party theme assets and adult source adapter code from the active Store build. |
| [x] | Remove remote repository and user-installed executable extension loading from the active Store build. |
| [x] | Include the bundled non-adult source adapters and keep catalogue, details, chapters, pages, reading, download, local cache, library, progress, and music flows. |
| [x] | Generate a genuine MSIX with `AppxManifest.xml`, `AppxBlockMap.xml`, and `[Content_Types].xml` using a real MSIX packager. |
| [x] | Create the recommended `.msixupload` wrapper containing the MSIX package. |
| [x] | Replace the placeholder identity with the exact case-sensitive Partner Center package name and publisher values. |
| [ ] | On Windows with the Windows SDK, rebuild with `makeappx.exe` after the identity replacement, or verify the supplied MSIX with the Windows App Certification Kit. |
| [ ] | If the final identity or version changes, rebuild the package and recalculate the hash. |
| [ ] | Upload the `.msixupload` file—not the previous hand-built archive—to Partner Center and complete package validation. |

MSIX is the recommended route for this build. For Windows 10 and later, Microsoft recommends uploading `.msixupload` or `.appxupload` rather than a raw `.msix` file. Microsoft describes MSIX separately from MSI/EXE submission; do not submit the interactive Inno Setup installer as an EXE route unless its package meets the Store’s signing and silent-install requirements.[4] [5] [6]

## 4. Functional and policy validation

| Status | Test |
|---|---|
| [ ] | Install the final package on a clean Windows x64 machine or clean VM. |
| [ ] | Confirm startup is responsive and no JVM, Skiko, native launcher, or missing-runtime error appears. |
| [ ] | Browse each bundled source, search, open a detail page, list chapters, open a chapter, load all page images, and navigate to the next/previous chapter. |
| [ ] | Download a chapter, close the app, disable network access, and read the downloaded pages offline. |
| [ ] | Verify library resume opens the saved chapter and page; verify history and progress remain after restart. |
| [ ] | Verify legacy settings cannot enable adult mode and legacy adult library/download records are not exposed in the Store UI. |
| [ ] | Verify Sources has no extension repository, Install JAR, or remote executable-extension control. |
| [ ] | Verify manual verification opens the default Windows browser and does not automate CAPTCHA solving or bypass. |
| [ ] | Verify Settings contains no NSFW toggle and no custom artwork-theme selector. |
| [ ] | Verify Store screenshots do not show adult content, unlicensed third-party artwork, browser credentials, cookies, personal paths, or development UI. |
| [ ] | Test suspend/resume, minimize-to-tray if enabled, clean exit, uninstall, and reinstall. |

Store policy requires responsive and stable behavior and places requirements on products that browse the web and on adult content.[1] The release must be reviewed against the current policy before upload.

## 5. Listing and age rating

| Status | Action |
|---|---|
| [ ] | Copy the approved text from `store/store-listing.md` into Partner Center and edit it to match the exact shipped behavior. |
| [ ] | Select the most accurate category and complete the age-rating questionnaire honestly. |
| [ ] | Add the published privacy-policy URL, support URL, and publisher information. |
| [ ] | Upload the required logo/tile assets and at least the required screenshots at the requested sizes. |
| [ ] | Use screenshots from the final Store build, with readable English UI or clearly documented localized UI. |
| [ ] | Check spelling, version, supported Windows versions, x64 architecture, and feature claims. |
| [ ] | Add a content/rights note for third-party source catalogues and cover images where Partner Center requests it. |

## 6. Final acceptance gate

Do not call the release Store-approved until Partner Center reports successful validation and certification. A successful local build, archive, or smoke test demonstrates packaging readiness only; it does not substitute for Microsoft’s review.

## References

[1]: https://learn.microsoft.com/en-us/windows/apps/publish/store-policies "Microsoft Store Policies"
[2]: https://learn.microsoft.com/en-us/windows/apps/publish/publish-your-app/msix/create-app-submission "Create an app submission"
[3]: https://learn.microsoft.com/en-us/windows/apps/publish/partner-center/open-a-developer-account "Open a developer account"
[4]: https://learn.microsoft.com/en-us/windows/apps/publish/publish-your-app/msix/app-package-requirements "MSIX app package requirements"
[5]: https://learn.microsoft.com/en-us/windows/apps/publish/publish-your-app/msi/app-package-requirements "MSI/EXE app package requirements"
[6]: https://learn.microsoft.com/en-us/windows/apps/publish/publish-your-app/msix/upload-app-packages "Upload MSIX app packages"
