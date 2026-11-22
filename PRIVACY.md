# Privacy policy for Receipt ledger

**Effective date:** 21 November 2026

**App:** Receipt ledger (`com.receiptledger.app`)  
**Developer:** Guomin Ren (GitHub: [renguomin](https://github.com/renguomin))

This policy describes what the Android app **Receipt ledger** does with your information. It is based on how the app is built today, not on a generic template.

Receipt ledger helps you photograph everyday receipts, read the text, fill tax-related fields, and keep those records for personal or small-business bookkeeping. It is a record-keeping aid, not tax advice, and not a bank or accounting service.

## Short version

- The developer does **not** run an account system or an app server that stores your receipts.
- Receipt photos and the fields you save live **on your phone**, in storage only this app can read.
- Reading text on the photo (OCR) runs **on the device**. That step does not need the internet.
- If you save a **Gemini API key**, the app sends each new receipt **photo** to **Google** so Google’s model can fill merchant, date, totals, and similar fields. That is optional. With no key, nothing from the receipt is sent to Google for reading.
- Backup and export only go where **you** pick in Android’s file picker (for example Google Drive, Downloads, or another app). The developer does not receive those files.
- There are **no ads**, **no analytics SDK**, **no crash-reporting SDK**, and **no sign-in** to this app.

## What the app stores on your phone

The app stores:

- **Photos** of receipts you scan, import from the gallery, or pull from a PDF
- **Fields you confirm**, including store name, date, currency, subtotal, tax amounts (such as GST, HST, PST, QST), total, payment method (this can include a card brand and last four digits if they were on the slip), vendor GST/HST number if visible, receipt number, line items, whether the purchase is personal or business, and a tax category
- The **raw text** read from the photo, used to fill fields and to search
- **Store-name shortcuts** you teach it (for example `CDN TIRE` → Canadian Tire)
- **Categories** you add yourself
- **Settings:** country (Canada or United States), currency, default personal vs business, whether AI search is on, your Gemini API key and optional model name, and the backup file you chose

This stays in the app’s private files and a local database (`receipt-ledger.db`). Other apps cannot read it unless you share a file yourself.

Uninstalling the app removes this on-device data. A backup file you saved elsewhere is not removed.

Android’s automatic cloud backup is told **not** to copy the receipt database or photo folder. Those files are large and restoring an old database can break the app. Use the in-app backup if you want a copy off the phone.

## What leaves your phone

### 1. On-device reading (default)

The app reads words on the photo with **RapidOCR** on the phone. If that returns nothing, it tries **ML Kit** text recognition on the phone. That reading does not send the photo to the developer or to a server the developer operates.

### 2. Google Play services document scanner

The scan screen (edge finding, flattening a folded slip, contrast) is **Google’s ML Kit Document Scanner**, delivered by **Google Play services**. Play services may download scanner models to the device. That screen is Google’s, not ours. Google’s terms and privacy policy apply to Play services. The app then copies the cleaned JPEG into its own storage.

### 3. Google Gemini (only if you add an API key)

If you paste a Google AI Studio / Gemini API key in Settings (or put it in `gemini-api-key.txt` in the app’s files):

- **Every new receipt photo** is sent to Google’s Generative Language API (`generativelanguage.googleapis.com`) as a JPEG, together with a prompt that asks for JSON fields and the list of tax category names. The phone also reads the photo itself at the same time; if Google cannot be reached, the on-device fields are used instead.
- Opening the review screen may make a small **connection check** to that Google host. A check without a key does not include your photo.
- If you turn on **AI search** (the sparkle in the search bar), each search sends your **query** plus a short **index** of saved receipts: store names, item names, and brief snippets of receipt text. It does not send the photos again for search.

The API key is **yours**. It is stored on the phone and sent to Google as part of the request. The developer never sees your key, your photos, or Google’s replies. What Google keeps, for how long, and in which region is governed by **Google’s** Gemini / Google AI Studio terms and privacy policy, not by this app.

You can stop this by deleting the API key in Settings. New scans then stay on the phone. Data Google already received is under Google’s policies.

### 4. Backup

**Back up now** packs the database, receipt photos, and settings into one zip and opens Android’s system picker. You choose the destination: Google Drive, another cloud app, Downloads, or a file you already made.

The app does **not** sign into Google, does not request Drive API access, and does not see your Google password. If you pick Drive, **Google Drive** (your account) holds the zip. Later backups can overwrite that same file, including a daily job around 2am if you turn that on.

**Restore** reads a zip you pick and replaces the receipts on this phone with that copy.

### 5. Export and sharing

CSV and PDF export use Android’s share / save sheet. The file goes only to the app or location you choose (email, Drive, Files, an accountant’s tool, and so on). The developer does not receive exports.

The app uses Android **FileProvider** so another app you pick can open a file you just exported. That grant is for that share only.

### 6. Internet permission

The app requests `INTERNET` so it can talk to Google’s Gemini API when a key is present, and so Play services can fetch scanner models. There is no developer backend.

## What this app does not do

- Create accounts or require an email login
- Show ads or use an advertising ID
- Include Firebase, Google Analytics, Crashlytics, or similar tracking SDKs
- Sell your data
- Access your contacts, location, microphone, or SMS
- Upload receipts to a server the developer operates

## Children

Receipt ledger is for adults keeping personal or small-business records. It is not directed at children under 13, and we do not knowingly collect children’s data.

## Your choices

- **Do not add a Gemini key** — photos are not sent to Google for reading.
- **Turn AI search off** — searches stay on the phone.
- **Edit or delete** a receipt in the app (that removes the saved fields and the stored photo for that receipt).
- **Skip backup**, or pick a destination you control.
- **Uninstall** to erase on-device data.

## Third parties

| Who | When | What |
| --- | --- | --- |
| Google Play services | You scan a paper receipt | Scanner UI and on-device models |
| Google Gemini (Generative Language API) | You saved an API key | Receipt JPEG and prompts; search text if AI search is on |
| Google Drive or any app you pick in the system picker | You back up, restore, or export | The zip, CSV, or PDF you chose to save or share |

Those companies have their own privacy policies. This app cannot change them.

## Google Play Data safety (how this maps)

When you fill Play Console’s Data safety form, this is the honest mapping for **the developer’s** collection. “Collected by us” means stored on a server we operate. We operate none.

- **We do not collect** user data on our own servers.
- **Photos and videos:** stored on the device. **Shared with Google** only if a Gemini API key is saved (receipt image used to fill fields).
- **Financial info** (purchase history, amounts, tax, payment last-four if printed on the slip): stored on the device. **Shared with Google** in the photo and, for AI search, as short text in the search index, only with a key / AI search on.
- **Personal info:** we do not ask for your name or email. A receipt photo may show whatever was printed or written on the paper.
- **App activity / diagnostics:** no analytics SDK. Android and Google may have their own device logs; this app does not add a crash reporter.
- **Data encrypted in transit:** yes, HTTPS to Google’s API.
- **Users can request deletion from us:** there is nothing on our servers to delete. Delete receipts in the app, remove the Gemini key, and delete backup/export files you created. For copies Google holds, use Google’s tools and policies.

## Changes

If the app starts storing data on a developer server, adding ads, or sending photos without a key you supplied, this policy will be updated in this file and the Play listing will use the new URL or the same URL with a new date.

## Contact

Questions about this policy or the app: open an issue on the public repository

[https://github.com/renguomin/receipt-ledger-andrioid](https://github.com/renguomin/receipt-ledger-andrioid)

You can also use the email listed on the Google Play store listing for Receipt ledger.

This document is for transparency. It is not legal advice.
