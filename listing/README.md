# MedReminder – Google Play Store Listing Kit

This directory contains everything needed to set up the Google Play Console store listing.

---

## 📁 Directory Structure

```
listing/
├── README.md                     ← You are here
├── generate_graphics.html        ← Open in browser to generate & download icon + feature graphic
├── privacy_policy.html           ← Hostable Privacy Policy page (required by Google Play)
├── terms_of_use.html             ← Hostable Terms of Use page
├── medical_disclaimer.html       ← Hostable Medical Disclaimer page
├── DATA_SAFETY.md                ← Pre-filled Data Safety form answers
├── en-US/
│   ├── title.txt                 ← App title (max 30 chars)
│   ├── short_description.txt     ← Short description (max 80 chars)
│   ├── full_description.txt      ← Full description (max 4000 chars)
│   ├── changelogs/
│   │   └── 1.txt                 ← Release notes for versionCode 1
│   └── images/
│       ├── icon.png              ← 512×512 app icon  (generate via HTML)
│       └── featureGraphic.png    ← 1024×500 feature graphic (generate via HTML)
```

---

## 🎨 Generating the Icon & Feature Graphic

1. **Open** `generate_graphics.html` in any modern browser (Chrome, Edge, Firefox).
2. The icon (512×512) and feature graphic (1024×500) are rendered automatically on canvas.
3. Click **⬇ Download Icon PNG** and **⬇ Download Feature Graphic PNG**.
4. Move the downloaded files into `en-US/images/`.

> **Tip:** If you later want a more polished icon, use the vector drawables in
> `app/src/main/res/drawable/ic_launcher_foreground.xml` and
> `ic_launcher_background.xml` as a reference for a designer.

---

## 🚀 Google Play Console – Store Listing Checklist

### 1. App Details
| Field              | File / Value                                 |
|--------------------|----------------------------------------------|
| App name           | `en-US/title.txt`                            |
| Short description  | `en-US/short_description.txt`                |
| Full description   | `en-US/full_description.txt`                 |

### 2. Graphics
| Asset              | Spec                  | File                        |
|--------------------|-----------------------|-----------------------------|
| App icon           | 512 × 512 px, PNG     | `en-US/images/icon.png`     |
| Feature graphic    | 1024 × 500 px, PNG    | `en-US/images/featureGraphic.png` |
| Phone screenshots  | Min 2, 16:9 or 9:16   | Take from emulator / device |
| 7" tablet screenshots | Optional             | —                           |
| 10" tablet screenshots | Optional            | —                           |

### 3. Categorisation
| Field               | Recommended Value             |
|---------------------|-------------------------------|
| Application type    | Application                   |
| Category            | Medical                       |
| Content rating      | Complete the questionnaire    |
| Target audience     | 18+                           |

### 4. Contact Details
| Field               | Value                         |
|---------------------|-------------------------------|
| Email               | *(your support email)*        |
| Phone               | *(optional)*                  |
| Website             | *(optional)*                  |

### 5. Privacy Policy
A privacy policy is **required** for Medical category apps.

Three ready-to-host HTML pages are included in this directory:
- **`privacy_policy.html`** – Privacy Policy
- **`terms_of_use.html`** – Terms of Use
- **`medical_disclaimer.html`** – Medical Disclaimer

All three pages share a consistent design and link to each other via a top navigation bar.

**To deploy:**
1. Upload all three `.html` files to any static hosting (GitHub Pages, Firebase Hosting, Netlify, your own domain, etc.).
2. Paste the **Privacy Policy URL** into Google Play Console → Policy → App content → Privacy policy.
3. Paste the same URL in your app's settings if you link to it in-app.

> Example: if hosted at `https://yourdomain.com/legal/`, users can access
> `https://yourdomain.com/legal/privacy_policy.html`

### 6. Content Declarations
- **Ads:** App does not contain ads.
- **Target audience:** 18 and older.
- **Health app:** Yes – medication reminder / tracker (NOT a medical device).
- **Government apps:** No.
- **News:** No.
- **Data safety:**
  - Data collected: Name (optional), health info (medications) – stored locally only.
  - Data shared: None (unless user enables optional Cloud AI or SMS).
  - Encryption: Android platform encryption.
  - Deletion: User can delete all data in Settings.

### 7. App Bundle
Build a signed release AAB:
```bash
./gradlew bundleRelease
```
Output: `app/build/outputs/bundle/release/app-release.aab`

Make sure your signing config is set up in `~/.gradle/gradle.properties`:
```properties
MEDREMINDER_RELEASE_STORE_FILE=/path/to/keystore.jks
MEDREMINDER_RELEASE_STORE_PASSWORD=****
MEDREMINDER_RELEASE_KEY_ALIAS=medreminder
MEDREMINDER_RELEASE_KEY_PASSWORD=****
```

---

## 📱 Screenshots (Recommended)

Take at least **2 phone screenshots** (required, up to 8). Suggested screens:

1. **Home / Today** – shows daily medication list with progress ring
2. **Add Medication** – form with AI auto-fill button
3. **Full-Screen Alarm** – shows the alarm UI with Take / Snooze / Skip
4. **Adherence Stats** – weekly/monthly adherence chart
5. **AI Insights** – daily or medication analysis card
6. **Family & Caregivers** – caregiver list with emergency badge
7. **OCR Scanner** – camera scanning a medication label
8. **Settings** – showing privacy and AI engine options

### Screenshot Specs
| Device type   | Min size    | Max size      | Format     |
|---------------|-------------|---------------|------------|
| Phone         | 320 × 320   | 3840 × 3840  | PNG / JPEG |
| 7" tablet     | 320 × 320   | 3840 × 3840  | PNG / JPEG |
| 10" tablet    | 320 × 320   | 3840 × 3840  | PNG / JPEG |

---

## 🌍 Localised Listings (Optional)

The app already supports these locales (see `res/values-*/`). You can add
translated listing files for each:

- `es/` – Spanish
- `ru/` – Russian
- `hy/` – Armenian
- `hi/` – Hindi
- `ja/` – Japanese
- `zh-CN/` – Chinese (Simplified)
- `fa/` – Farsi

Create the same `title.txt`, `short_description.txt`, `full_description.txt`
structure under each locale folder.

