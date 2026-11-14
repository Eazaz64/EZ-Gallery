# Fast Gallery

A fast Android gallery for very large image collections (400,000+ WebP/JPEG images in 1,000+ folders).
Personal use: it uses "All files access" so it can read and change your folders directly.

## Features
- Scans only your **main folders** (not the whole storage). After the first scan, only changed folders are re-read.
- Folder grid (home) with **pinned folders** on top, fast scroller, sort chip
- Image grid inside a folder, **pinch to change columns**, fast scroller with date bubble
- Multi-select: long-press, then **drag to select a range**, select all, **move to folder**, **delete** (permanent)
- Viewer: swipe, pinch/double-tap zoom (large images are sub-sampled), ⋮ menu → Info, Rename, Set as wallpaper, Move, Slideshow, Delete
- **Slideshow**: 1–120 s per slide, start from this image or the first image (plays the whole folder once), loop, shuffle, fade/slide/none, keep screen on
- **Sort** folders and images by name (natural order), date modified, date taken, size, ascending/descending; per folder or global
- Rename image (extension kept), rename folder, pin folder
- **Colors by hex code**: app background, text, accent, viewer background, viewer bars
- Thumbnail cache on disk with a size limit (default 1.5 GB)
- **Open with / default gallery**: images opened from file managers or chat apps open in Fast Gallery (inside their folder if it's scanned)
- **Share** one or many images, **Copy to folder**, **search folders** on the home screen
- **Auto refresh**: new or deleted images are picked up each time you return to the app (only changed folders are read)
- **Simple editor** (saved as a copy `name_edit.ext`, original kept): crop with aspect ratios, rotate, flip, straighten, brightness, contrast, resize

"Date taken" is read from the file name (e.g. `IMG_20240312_161800.webp`); if the name has no date, the file's modified time is used. Reading EXIF from 400k files would make scanning far slower.

## Build the APK with GitHub (no Android Studio needed)
1. Create a new repository on GitHub (private is fine), e.g. `FastGallery`.
2. Upload **all files and folders** from this project, including the hidden `.github` folder.
3. Open the repo's **Actions** tab. The "Build APK" run starts automatically (takes about 5–10 minutes).
4. When it shows a green tick, open the run → **Artifacts** → download **FastGallery-apk** (a zip with the `.apk` inside).
5. Copy the APK to your phone/tablet and install it (allow "Install unknown apps" if asked).

Every new upload/commit builds a new APK. All builds use the same signing key (`app/signing.keystore`),
so a new APK installs **over** the old one and your scan data, pins and settings are kept.

## First run
1. Allow **All files access** when asked.
2. Choose your main folder (the "mother" folder that holds your image folders).
3. Tap **Scan main folders**. You can browse while it scans.
