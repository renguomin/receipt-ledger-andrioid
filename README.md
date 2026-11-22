# Receipt ledger

Android app that scans everyday receipts, reads the text, and fills the tax fields. You confirm before anything is saved. Canada first (T2125-style categories), United States as a second country pack.

This is a record-keeping aid, not tax advice.

**Privacy policy (required for Google Play):** [PRIVACY.md](PRIVACY.md). Public URL once the repo is public: `https://github.com/renguomin/receipt-ledger-andrioid/blob/main/PRIVACY.md`

## What it does

1. Opens Google’s document scanner so a folded slip can be flattened and brightened.
2. Reads words and their positions on the cleaned photo with **RapidOCR** (Paddle PP-OCR on-device). If that returns nothing, it falls back to ML Kit. No network for OCR.
3. Fills store, date, totals, GST/HST/PST/QST lines, and a category guess.
4. Marks weak guesses so you can fix them.
5. Optional “Try a harder read” sends the photo to Gemini if you turn that on and paste an API key.
6. Saves the photo plus the fields on the phone.
7. Learns store abbreviations when you correct them (`CDN TIRE` → Canadian Tire).
8. Exports CSV or PDF for an accountant. Filter by year first if you want one tax year.
9. Backs up everything to one file in your Google Drive, and restores from it. No setup, no sign-in.

## Project layout

- `app` — Compose screens, camera/scanner, RapidOCR, Room, Gemini HTTP
- `receipt-engine` — parsing, tax categories, aliases, CSV (no Android, no Play Services)

## Open in Android Studio

1. Install [Android Studio](https://developer.android.com/studio) if you do not have it.
2. Open this folder.
3. Let it download the Android SDK if asked.
4. Run the `app` configuration on a phone or emulator with Google Play (the document scanner comes from Play services).

Create `local.properties` with your SDK path if Studio does not do it for you:

```
sdk.dir=/Users/YOU/Library/Android/sdk
```

This project keeps its own Gradle cache in `.gradle-home/` (not in `~/.gradle`). It does not change the Mac’s default Java.

## Tests that do not need a phone

```
./gradlew :receipt-engine:test
```

## Gemini (optional)

In Settings, turn on “Harder read” and paste a Google AI Studio API key. Receipt photos stay on the phone unless you tap **Try a harder read**.

## Backup

Settings → **Backup** packs the database, the photos your receipts point at, and your settings into
one zip, then hands it to Android's file picker. Pick **Google Drive** and it lands in your Google
account. There is nothing to configure: no sign-in, no API key, no Google Cloud project. Android and
the Drive app do the account work.

The chosen file is remembered, so every later backup is a single tap that replaces it — no picker.
Anywhere the picker offers works too: another cloud provider, the SD card, or local storage. The
summary names where it landed, so a backup that went to the phone instead of Drive is obvious.

**Back up daily** then makes it unattended. The permission taken when the file was chosen survives
reboots, so a WorkManager job can overwrite that same file from the background with no screen and no
sign-in. It aims for 2am local, skips a low battery, and waits for a connection when the destination
is in the cloud. Because it always replaces one file, disk use stays flat.

**Restore** reads that same file back, refuses it if it came from a build with a newer database
schema (Room cannot migrate backwards), swaps the files in, and restarts the app. *Restore a
different file* opens the picker if you want an older copy or one from another phone.

Two notes on why it works this way:

- Google Drive does not support Android's folder picker (`ACTION_OPEN_DOCUMENT_TREE`), only single
  files, which is why the app remembers one file rather than a backup folder.
- `ACTION_CREATE_DOCUMENT` can only create, never overwrite, and Drive allows two files to share a
  name in one folder, so creating repeatedly leaves duplicates behind. **Change where it saves**
  therefore offers *replace a file I already have* (`ACTION_OPEN_DOCUMENT` with the write flag) as
  well as *create a new one*. The replace path refuses any file whose name is not one of our
  backups, since it overwrites whatever it is pointed at.
- The Drive REST API is the other way to do this, and what apps that "log in once and save to Drive
  automatically" use. Its `drive.appdata` scope is **non-sensitive**, so it needs no Google review
  for personal use — but it does need a one-time OAuth client registered against this package name
  and signing certificate, and it stops working the moment the signing key changes. The picker route
  needs no registration at all, which is why it is the default here.

Receipts and photos are deliberately excluded from Android Auto Backup (see
`app/src/main/res/xml/data_extraction_rules.xml`): the photo directory is far bigger than the 25 MB
Auto Backup allows, and a silently restored older database crashes the app on every launch.
