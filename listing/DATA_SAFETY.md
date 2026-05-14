# Google Play Data Safety Form – MedReminder

Use these answers when filling out the **Data safety** section in Google Play Console.

---

## Overview

| Question | Answer |
|----------|--------|
| Does your app collect or share any of the required user data types? | Yes |
| Is all of the user data collected by your app encrypted in transit? | Yes (HTTPS for optional cloud AI; SMS via carrier encryption) |
| Do you provide a way for users to request that their data is deleted? | Yes (Settings → Clear all data) |

---

## Data Types Collected

### Personal info → Name
| Field | Value |
|-------|-------|
| Is this data collected, shared, or both? | Collected |
| Is this data processed ephemerally? | No |
| Is this data required or optional? | Optional |
| Why is this data collected? | App functionality (personalized greetings) |
| Is this data shared with third parties? | No |

### Health info → Health info
| Field | Value |
|-------|-------|
| Is this data collected, shared, or both? | Collected |
| Is this data processed ephemerally? | No |
| Is this data required or optional? | Required (core feature) |
| Why is this data collected? | App functionality |
| Is this data shared with third parties? | No* |

> *If user explicitly enables Cloud AI, medication details are sent to the
> selected cloud AI provider for analysis. This is opt-in only.

### Contacts → Phone number
| Field | Value |
|-------|-------|
| Is this data collected, shared, or both? | Collected |
| Is this data processed ephemerally? | No |
| Is this data required or optional? | Optional (only for caregiver feature) |
| Why is this data collected? | App functionality (caregiver SMS alerts) |
| Is this data shared with third parties? | No |

### Photos and videos → Photos
| Field | Value |
|-------|-------|
| Is this data collected, shared, or both? | Collected |
| Is this data processed ephemerally? | Yes |
| Is this data required or optional? | Optional (OCR scanning) |
| Why is this data collected? | App functionality |
| Is this data shared with third parties? | No |

### Location → Approximate/Precise location
| Field | Value |
|-------|-------|
| Is this data collected, shared, or both? | Collected |
| Is this data processed ephemerally? | Yes |
| Is this data required or optional? | Optional (emergency caregiver SMS only) |
| Why is this data collected? | App functionality |
| Is this data shared with third parties? | No (sent in SMS to user-configured caregiver only) |

---

## Data NOT Collected
- Financial info
- Messages (app sends SMS but doesn't read incoming)
- Files and docs
- Calendar
- App activity / Web browsing
- Device or other IDs (no analytics, no tracking)
- Emails
- Search history

