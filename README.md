# PDF Reader

A private, offline PDF reader for Android, part of the Numio family of apps.

No ads, no accounts, no tracking, and **no internet permission at all**, so your
documents never leave your phone.

## What it does

**Reading**
- Library of recent PDFs with first-page previews, *Continue reading*, Starred, and search by name
- Smooth scrolling, pinch or double-tap to zoom, one-page layout, and a **book mode**
  (each page full screen, swipe like a real book, with a reading lock)
- **Find in the PDF**: every match lit on the page, with next / previous
- Night and sepia page colours, contents (the PDF's outline) and bookmarks
- Light and dark app themes, or follow the phone
- Remembers your place in every PDF; thumbnail strip and *Go to page*
- Opens PDFs from other apps and shares them on

**Marking and editing**
- Select and copy text; highlight and underline in six colours
- Pen, text boxes (move and pinch to resize) and sticky notes
- Change or remove the page's own words (real removal, not a cover)
- Fill forms and sign: draw, type (Malayalam too) or a saved signature behind the fingerprint lock
- Saves into the original file when Android allows it, or as a new copy

No storage permission is needed: PDFs are opened through Android's own file
picker, one file at a time.

## Coming next

- A list of all your marks, to jump to and export
- Page tools: rotate, reorder, delete, extract and merge
- App lock and private collections
- Offline text recognition for scanned pages (English and Malayalam), then read aloud

## Built with

- Kotlin and Jetpack Compose
- Android's own PDF renderer (`android.graphics.pdf`) to draw pages
- [PdfBox-Android](https://github.com/TomRoush/PdfBox-Android) (Apache-2.0) to read text and save changes
- [Inter](https://rsms.me/inter/) font (SIL Open Font License 1.1, see `licenses/`)

Requires Android 8.0 (API 26) or newer.

## Licence

GPL-3.0. See [LICENSE](LICENSE).

## About

Built by RR with help from Claude. Part of [Numio](https://getnumio.org).
