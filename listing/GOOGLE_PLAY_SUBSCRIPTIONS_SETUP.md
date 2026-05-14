# Google Play Console — Subscription Setup Guide

## Prerequisites

1. **Google Play Developer account** ($25 one-time fee)
2. **App published** (at least to internal/closed testing track) — subscriptions cannot be created until you upload at least one AAB/APK
3. **Merchant account linked** in Google Play Console → Setup → Payments profile

---

## Step 1: Upload Your First AAB (if not done yet)

1. Open [Google Play Console](https://play.google.com/console)
2. Select your app **MedReminder** (`med.reminder.com`)
3. Go to **Testing → Internal testing** → **Create new release**
4. Upload the signed AAB (build with `./gradlew bundleRelease`)
5. Complete the release and roll out to internal testing
6. ⚠️ You **must** complete this before you can create in-app products

---

## Step 2: Create Subscriptions

Navigate to: **Monetize → Products → Subscriptions** → Click **"Create subscription"**

You need to create **3 subscriptions** matching the product IDs in your code:

---

### Subscription 1: Basic

| Field                | Value                           |
|----------------------|---------------------------------|
| **Product ID**       | `medreminder_basic_monthly`     |
| **Name**             | MedReminder Basic               |
| **Description**      | Full tracking power without limits — unlimited medications, advanced reports, drug interaction checker, family members, and data export. |

#### Base Plan (Monthly)

| Field                  | Value                         |
|------------------------|-------------------------------|
| **Base plan ID**       | `basic-monthly`               |
| **Billing period**     | 1 Month                       |
| **Auto-renewing**      | Yes                           |
| **Price**              | **$1.99 USD**                 |
| **Grace period**       | 7 days (recommended)          |
| **Account hold**       | 30 days (recommended)         |
| **Resubscribe**        | Enabled                       |

#### Offer (Optional — Free Trial)

| Field              | Value                             |
|--------------------|-----------------------------------|
| **Offer ID**       | `basic-monthly-trial`             |
| **Eligibility**    | New customer acquisition          |
| **Phase 1**        | Free trial — 3 days               |
| **Phase 2**        | Auto-converts to $1.99/month      |

---

### Subscription 2: Pro ⭐ (Most Popular)

| Field                | Value                           |
|----------------------|---------------------------------|
| **Product ID**       | `medreminder_pro_monthly`       |
| **Name**             | MedReminder Pro                 |
| **Description**      | Smart AI insights powered by the cloud — daily AI analysis, weekly reports, 5 medication deep analyses per month, ~150K tokens included. |

#### Base Plan (Monthly)

| Field                  | Value                         |
|------------------------|-------------------------------|
| **Base plan ID**       | `pro-monthly`                 |
| **Billing period**     | 1 Month                       |
| **Auto-renewing**      | Yes                           |
| **Price**              | **$4.99 USD**                 |
| **Grace period**       | 7 days                        |
| **Account hold**       | 30 days                       |
| **Resubscribe**        | Enabled                       |

#### Offer (Optional — Free Trial)

| Field              | Value                             |
|--------------------|-----------------------------------|
| **Offer ID**       | `pro-monthly-trial`               |
| **Eligibility**    | New customer acquisition          |
| **Phase 1**        | Free trial — 7 days               |
| **Phase 2**        | Auto-converts to $4.99/month      |

---

### Subscription 3: Premium

| Field                | Value                           |
|----------------------|---------------------------------|
| **Product ID**       | `medreminder_premium_monthly`   |
| **Name**             | MedReminder Premium             |
| **Description**      | Maximum AI power for the whole family — unlimited Cloud AI analyses, family plan up to 5 members, all AI providers, priority processing. |

#### Base Plan (Monthly)

| Field                  | Value                         |
|------------------------|-------------------------------|
| **Base plan ID**       | `premium-monthly`             |
| **Billing period**     | 1 Month                       |
| **Auto-renewing**      | Yes                           |
| **Price**              | **$9.99 USD**                 |
| **Grace period**       | 7 days                        |
| **Account hold**       | 30 days                       |
| **Resubscribe**        | Enabled                       |

#### Offer (Optional — Free Trial)

| Field              | Value                             |
|--------------------|-----------------------------------|
| **Offer ID**       | `premium-monthly-trial`           |
| **Eligibility**    | New customer acquisition          |
| **Phase 1**        | Free trial — 7 days               |
| **Phase 2**        | Auto-converts to $9.99/month      |

---

## Step 3: Set Prices for All Regions

For each subscription after setting the USD price:

1. Click **"Set prices"** or **"Manage prices"**
2. Select **all countries** you want to support
3. Click **"Update prices"** → Google auto-converts from USD base
4. Review the converted prices and click **"Save"**

---

## Step 4: Activate Subscriptions

After creating each subscription:

1. Make sure all required fields are filled
2. Click **"Activate"** for each subscription
3. Status should change from "Draft" to **"Active"**

---

## Step 5: Configure Subscription Settings (Global)

Go to **Monetize → Monetization setup**:

| Setting                          | Recommended Value              |
|----------------------------------|--------------------------------|
| **Grace period (default)**       | 7 days                         |
| **Account hold (default)**       | 30 days                        |
| **Pause subscription**           | Disabled (not recommended for this app type) |
| **Resubscribe from Play Store**  | Enabled                        |
| **Allow upgrade/downgrade**      | Enabled (proration: immediate) |

---

## Step 6: Set Up Google Play Billing License Testing

Go to **Setup → License testing**:

1. Add tester email addresses (your team's Google accounts)
2. Set **License response** to `RESPOND_NORMALLY`
3. Testers added here can make purchases without being charged

> ⚠️ **Important**: The Google account used for testing must be added to the license testers list AND must be signed in on the test device.

---

## Step 7: Link to Internal Testing Track

1. Go to **Testing → Internal testing**
2. Create a **testers list** with your test emails
3. Share the **opt-in URL** with testers
4. Testers must accept the invitation before they can see subscriptions

---

## Step 8: Verify Integration

### On a test device:
1. Install the app from internal testing track (or use `adb install`)
2. Sign in with a license tester Google account
3. Navigate to the subscription/paywall screen
4. Verify that:
   - [ ] All 3 subscription plans appear with correct prices
   - [ ] Tapping "Subscribe" opens the Google Play purchase dialog
   - [ ] Test purchase completes successfully (no real charge for license testers)
   - [ ] Subscription status updates in the app after purchase
   - [ ] App features unlock correctly for the purchased tier

### Test scenarios:
- [ ] Purchase Basic → verify unlimited medications unlock
- [ ] Purchase Pro → verify Cloud AI features unlock
- [ ] Purchase Premium → verify all features unlock
- [ ] Cancel subscription → verify features downgrade after expiry
- [ ] Upgrade Basic → Pro → verify immediate upgrade works

---

## Code ↔ Console Product ID Mapping

| Tier      | Code Product ID                  | Console Product ID               | Monthly Price |
|-----------|----------------------------------|----------------------------------|---------------|
| Free      | `null` (no product)              | N/A                              | $0            |
| Basic     | `medreminder_basic_monthly`      | `medreminder_basic_monthly`      | $1.99         |
| Pro       | `medreminder_pro_monthly`        | `medreminder_pro_monthly`        | $4.99         |
| Premium   | `medreminder_premium_monthly`    | `medreminder_premium_monthly`    | $9.99         |

> The Product IDs in Google Play Console **must match exactly** with the values in `SubscriptionTier.kt`.

---

## Real-Time Developer Notifications (RTDN) — Optional but Recommended

For server-side subscription validation (if you add a backend later):

1. Go to **Monetize → Monetization setup**
2. Under **Google Cloud Pub/Sub**, enter your Cloud Pub/Sub topic
3. This sends real-time notifications for subscription state changes

> For now, the app uses client-side validation via `BillingManager.queryExistingPurchases()`, which is sufficient for the current architecture.

---

## Troubleshooting

| Issue | Solution |
|-------|----------|
| "Item not found" error | Ensure product ID matches exactly, subscription is Active, and the APK version code on the device matches a published version |
| Purchase dialog doesn't appear | Verify `INTERNET` permission (already in manifest ✅), billing library version matches Console settings |
| Subscription not showing up | Wait 15-30 minutes after activation; clear Google Play Store cache on device |
| Test purchase charged real money | Add tester email to **License testing** list in Play Console |
| `BillingClient` disconnects frequently | Already handled with exponential backoff in `BillingManager.kt` ✅ |

---

## Summary Checklist

- [ ] Upload at least one AAB to any testing track
- [ ] Create `medreminder_basic_monthly` subscription ($1.99/month)
- [ ] Create `medreminder_pro_monthly` subscription ($4.99/month)
- [ ] Create `medreminder_premium_monthly` subscription ($9.99/month)
- [ ] Set regional prices for all target countries
- [ ] Activate all 3 subscriptions
- [ ] Add license testers
- [ ] Set up internal testing track with testers
- [ ] Verify end-to-end purchase flow on a test device

