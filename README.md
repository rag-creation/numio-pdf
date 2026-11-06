# PDF Reader

A private, offline PDF reader for Android, part of the Numio family of apps.

No ads, no accounts, no tracking, and **no internet permission at all**, so your
documents never leave your phone.

## Milestone 1 (this version)

- **Library**: recent PDFs with first-page previews, *Continue reading*, Starred, and search by name
- **Reader**: smooth scrolling through all pages, pinch or double-tap to zoom, tap a page to hide the bars
- **Remembers your place** in every PDF
- **Thumbnail strip** and *Go to page* for long documents
- **Opens PDFs from other apps**: tap a PDF in Files, WhatsApp or an email app and choose PDF Reader
- **Share** a PDF to another app

No storage permission is needed: PDFs are opened through Android's own file
picker, one file at a time.

## Coming next

- Markup: highlight, underline, pen, text and notes
- Fill forms and sign (draw, type, or a saved signature, including Malayalam)
- Page tools: rotate, reorder, delete, extract and merge
- Search inside a PDF, light and dark themes

## Built with

- Kotlin and Jetpack Compose
- Android's own PDF renderer (`android.graphics.pdf`), so no PDF library is bundled
- [Inter](https://rsms.me/inter/) font (SIL Open Font License 1.1, see `licenses/`)

Requires Android 8.0 (API 26) or newer.

## Licence

GPL-3.0. See [LICENSE](LICENSE).

## About

Built by RR with help from Claude. Part of [Numio](https://getnumio.org).
