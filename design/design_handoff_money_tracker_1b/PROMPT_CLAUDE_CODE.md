Wklej to do Claude Code w katalogu repozytorium money-tracker, po skopiowaniu folderu `design_handoff_money_tracker_1b` do `design/`:

---

Zaimplementuj nowy wygląd aplikacji i widgetu według makiet w `design/design_handoff_money_tracker_1b/`. Najpierw przeczytaj cały `README.md` w tym folderze. Opisuje on wymiary, kolory, typografię, animacje i zachowanie każdego ekranu. Pliki `.dc.html` i `mt-data.js` służą tylko jako referencja (możesz je czytać, żeby sprawdzić szczegóły). Nie kopiuj z nich kodu.

Zasady:
- Zmieniasz tylko warstwę UI (Compose, Material 3, Glance). Nie ruszaj modelu danych, Room, repozytoriów, WorkManagera, logiki alertów ani formaterów w `util/Formatters.kt`. Jeśli czegoś brakuje w ViewModelu (np. stanu odczytu z wykresu albo zakresu w Podsumowaniu), dodaj to minimalnie.
- Wariant: „1b Spokojna lista”. W Portfelu nie ma kart, jest pasek struktury portfela, a przy zakupach paski odchylenia od zera. W Instrumencie wykres stoi bez karty, zakresy są pod wykresem, a statystyki w siatce 2×N.
- Font: Manrope (dodaj do `res/font` albo przez downloadable fonts) z cyframi tabelarycznymi. Ustaw `Typography` w `ui/theme`.
- Kolory: zdefiniuj jasny i ciemny `ColorScheme` z tabeli w README. Kolory zysku i straty, wykresu itp. umieść w osobnym `CompositionLocal` (np. `MoneyColors`). Dynamic color domyślnie wyłączony.
- Kształty: pigułki dla przycisków, pól, segmentów i chipów. Karty z promieniem 28–32 dp. Dodaj `Shapes` w motywie.
- Animacje: krzywa `CubicBezierEasing(0.2f, 0f, 0f, 1f)` i czasy z README (wejście ekranów z prawej z paralaksą, fade i przesunięcie przy zmianie zakładek, morph wykresu przy zmianie zakresu, obrót ikony odświeżania).
- Wykres instrumentu: rysuj na `Canvas`. Dodaj linie cen zakupów (przerywane, w kolorze wyniku zakupu), kropkowaną linię średniej i odczyt dotykiem (pionowa linia, kropka, dymek z ceną i datą).
- Widget (Glance): cztery rozmiary przez `SizeMode.Responsive` (2×2, 4×1, 4×2, 4×3) zgodnie z sekcją „Widget” w README, z radiusem 22 dp, półprzezroczystym tłem z kryciem z Ustawień i motywem Systemowy / Jasny / Ciemny. Zaktualizuj `portfolio_widget_info.xml` (minWidth/minHeight, targetCellWidth/Height, resizeMode, previewLayout). Sparkline i paski odchylenia mogą być bitmapami.
- Wszystkie teksty po polsku, dokładnie jak w README. Dodaj je do `values-pl/strings.xml` i angielskie odpowiedniki do `values/strings.xml`.
- Bez emoji i bez dekoracyjnych gradientów.

Kolejność pracy:
1. Motyw (kolory, typografia, kształty, `MoneyColors`) i wspólne komponenty w `ui/components` (pigułka wyniku, pasek odchylenia, pole-pigułka, segment, przełącznik, snackbar).
2. Portfel i pasek nawigacji z FAB.
3. Instrument z wykresem, menu zakupu, arkuszem sprzedaży i dialogiem usuwania.
4. Podsumowanie, Historia, Ustawienia (z podglądem widgetu).
5. Dodaj / edytuj zakup (dwa kroki).
6. Stany puste.
7. Widget Glance.

Po każdym etapie zbuduj projekt (`./gradlew assembleDebug`) i popraw błędy. Na koniec uruchom testy (`./gradlew test`) i podsumuj, co zostało zrobione i co wymaga ręcznego sprawdzenia na urządzeniu.
