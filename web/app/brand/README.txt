Your logo lives here.

  logo.svg   the RR monogram (shown in the sidebar and on the sign-in screen)
  logo.png   optional fallback if you ever use a PNG instead

The app and home-screen icons in ../icons/ were rendered from logo.svg on the
cocoa background:

  icon-192.png         192 x 192
  icon-512.png         512 x 512
  maskable-512.png     512 x 512, logo inside the middle 80% (Android crops the edges)
  apple-touch-icon.png 180 x 180, no transparency (iPhone)
  icon.svg             browser tab icon

If you change the logo, replace these too, then bump VERSION in ../sw.js so
installed copies update.

icons/monochrome-*.png and monochrome.svg: a white silhouette of the logo for Android's status bar and notification badge.
