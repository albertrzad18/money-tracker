# Money Tracker

Android app with a home screen widget that shows how much each purchase of a stock, ETF or
cryptocurrency has gained or lost since you bought it.

Most brokers show one average result per instrument. Money Tracker tracks every purchase
(a "lot") separately, so you can see that the shares you bought in March are up 12% while
the ones from July are down 3%.

## Features

- **Per-purchase gain/loss**: the % result of each lot against the current price, plus the
  weighted average price and totals per instrument.
- **Home screen widget**: tiles tinted by each position's result (4×1, 4×2), a portfolio total
  with a sparkline (2×2), or a scrollable list of every purchase (4×3), with a refresh
  button. Theme, opacity and sort order are configurable.
- **Background refresh** every ~30 minutes via WorkManager. It skips closed markets where it can.
- **Backup**: export every purchase (sold ones too) to a JSON file and import it into another
  installation; purchases already in the app are skipped.
- **XTB tickers**: enter `PKN.PL`, `AAPL.US` or `CSPX.UK` and they are mapped to Yahoo
  symbols (`PKN.WA`, `AAPL`, `CSPX.L`).
- **Instrument chart** with lines at your purchase prices and the average price.
- **Selling**, including partial sales. Sold lots move to a history with the realised result.
- **Portfolio summary in PLN**: allocation donut and value-over-time chart, converted with
  NBP exchange rates.
- **Threshold alerts**: notifications when a purchase goes above or below a % level, with a
  re-arm margin so a price hovering around the threshold does not notify on every refresh.
- Polish and English UI.

## Data sources

All of them are public and need no API key or account:

| Data | Source |
| --- | --- |
| Stocks and ETFs | Yahoo Finance (unofficial endpoints) |
| Cryptocurrencies | CoinGecko |
| Exchange rates to PLN | NBP (Narodowy Bank Polski) mid rates |

Your portfolio stays on the device in a local Room database. Nothing is sent anywhere apart
from the price lookups.

## Building

Requirements: Android Studio (or the Android SDK) and JDK 17+. The app targets Android 8.0
(API 26) and above.

```sh
git clone https://github.com/albertrzad18/money-tracker.git
cd money-tracker
./gradlew assembleDebug        # APK in app/build/outputs/apk/debug/
./gradlew testDebugUnitTest    # unit tests
```

You can also open the project in Android Studio and run it on a device or emulator.

## Tech stack

Kotlin, Jetpack Compose (Material 3), Glance app widgets, Room, WorkManager, DataStore,
Hilt, Retrofit + OkHttp, kotlinx.serialization.

## Disclaimer

This is a personal project, not financial advice. Prices come from free public sources and
may be delayed or incomplete. Yahoo Finance endpoints are unofficial and can change without
notice.
