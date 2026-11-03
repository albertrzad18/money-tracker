# Money Tracker

Android app (Kotlin, Jetpack Compose, Glance) that shows the % gain/loss of each purchase
("lot") of stocks, ETFs and crypto against the current price, on a home screen widget and in the app.
UI is in Polish and English (`res/values-pl`, `res/values`).

## Build and test

```sh
./gradlew assembleDebug        # debug APK, applicationId gets the .debug suffix
./gradlew testDebugUnitTest    # JVM unit tests (no device needed)
```

- Gradle version catalog: `gradle/libs.versions.toml` — add dependencies there, not inline.
- Configuration cache and build cache are on (`gradle.properties`).
- `local.properties` (SDK path) is git-ignored.
- Release signing: `./gradlew assembleRelease` signs with `release.jks` + `keystore.properties` (both
  git-ignored, keep a backup). Without them the release APK is unsigned.
- UI design reference: `design/design_handoff_money_tracker_1b/` (variant „1b Spokojna lista”).

## Layout (`app/src/main/java/io/github/albertrzad18/moneytracker`)

- `data/db` — Room: instruments, lots, quotes, price and FX history. Schema is exported to
  `app/schemas/`; bump `version` in `AppDatabase.kt` and add a migration for any entity change,
  and commit the new schema JSON.
- `data/remote` — keyless HTTP sources via Retrofit + kotlinx.serialization:
  Yahoo Finance (stocks/ETFs), CoinGecko (crypto), NBP (PLN FX mid rates).
- `data/repo` — repositories; `SettingsRepository` uses DataStore.
- `domain` — pure logic, unit-tested: `PortfolioCalculator`/`Calc` (positions, lots, averages),
  `Fx` (PLN conversion, GBp/ZAc/ILA minor units), `Summary`, `Timeline`, `AlertEvaluator`,
  `MarketHours`, `XtbTickerMapper` (XTB `PKN.PL` → Yahoo `PKN.WA`).
- `ui` — Compose screens (portfolio, instrument, lot editor, history, summary, settings) + `Navigation.kt`.
- `widget` — Glance widget (`PortfolioWidget`) and `WidgetUpdater`.
- `work` — `Refresher` (single refresh: quotes → FX → alerts → widget, guarded by a mutex),
  `RefreshWorker` (WorkManager, ~30 min), notifications for threshold alerts.
- `di/AppModule.kt` — Hilt bindings (incl. `Clock`, inject it instead of calling `Instant.now()`).

## Conventions

- Money and prices are `BigDecimal` with `Calc.MC` (DECIMAL64); never `Double` in domain logic.
  Convert API doubles with `BigDecimal.valueOf` (`toBigDecimalExact`).
- A position is per instrument **and** currency — never mix currencies inside one number.
- Lots may lack a quantity; percentages still work, but value/gain are then null.
- Use `runCatchingCancellable` (`util/Results.kt`) instead of `runCatching` in coroutines.
- Keep domain code free of Android dependencies so it stays JVM-testable.
- API response shapes are covered by fixture tests in `app/src/test/resources/fixtures/`;
  update the fixtures when changing a remote DTO.
- Every user-visible string goes in both `values/strings.xml` and `values-pl/strings.xml`.
