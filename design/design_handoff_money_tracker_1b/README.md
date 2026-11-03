# Handoff: Money Tracker, redesign „1b Spokojna lista”

## Przegląd
Nowy wygląd całej aplikacji Money Tracker (Kotlin, Jetpack Compose, Material 3) i widgetu na ekran główny (Glance). Kierunek 1b „Spokojna lista”: bez kart na liście, duże liczby, dużo powietrza. Struktura portfela jest pokazana jako pasek, a wynik każdego zakupu jako odchylenie od zera. Paleta pochodzi z systemu Organic (krem, piasek, terakota, szałwia), font to **Manrope**, obsługiwany jest motyw jasny i ciemny.

## O plikach projektu
Pliki w tym folderze to **makiety referencyjne w HTML**: prototypy pokazujące docelowy wygląd i zachowanie. To nie jest kod produkcyjny do skopiowania. Zadanie polega na **odtworzeniu ich w istniejącym kodzie aplikacji** (Compose + Material 3, widget w Glance) według wzorców, które już są w repozytorium. Logika danych, ViewModele, Room, WorkManager i formatery zostają bez zmian. Zmienia się tylko warstwa UI.

Podgląd: otwórz `Prototyp 1b.dc.html` w przeglądarce. Potrzebny jest internet (fonty Google i ikony Lucide z unpkg).

## Wierność
**High-fidelity.** Kolory, typografia, odstępy, promienie i animacje są docelowe. Jednostka: 1 px w makiecie = 1 dp (ramka telefonu 360×780 dp). Rozmiary tekstu w px traktuj jako sp.

## Design tokens

### Kolory: jasny / ciemny
| Rola | Jasny | Ciemny | Mapowanie M3 (propozycja) |
|---|---|---|---|
| Tło | #f5ead8 | #1f1d1a | background, surfaceContainerLowest |
| Powierzchnia (nav bar, karty, arkusze) | #ebddc5 | #2d2a25 | surface, surfaceContainer* |
| Tekst | #201e1d | #f5ead8 | onBackground, onSurface |
| Tekst drugorzędny (muted) | #645c50 | #c0b6a5 | onSurfaceVariant |
| Akcent (FAB, przyciski, aktywne segmenty) | #c67139 | #d67f48 | primary |
| Tekst na akcencie | #f9f4ed | #201e1d | onPrimary |
| Wskaźnik nawigacji (aktywna pigułka) | #ffe1d0 | #643312 | secondaryContainer |
| Ikona na wskaźniku | #643312 | #ffe1d0 | onSecondaryContainer |
| Akcent drugorzędny tekstowy (nagłówki grup, link „Użyj bieżącego kursu”) | #8c491a | #f6a06b | tertiary |
| Zysk | #56633f | #b9cb98 | (własny) |
| Tło zysku | #dfe9c9 | #323a24 | (własny) |
| Strata | #a83a2c | #f19a87 | error |
| Tło straty | #f6d9cf | #45261f | errorContainer |
| Linia wykresu | #c67139 | #e08a52 | (własny) |
| Wypełnienie pod wykresem | rgba(198,113,57,.16) | rgba(224,138,82,.18) | (własny) |
| Druga linia wykresu („Zainwestowano”) | #7a8a5e | #aebf92 | (własny) |
| Linie / tor pasków | rgba(32,30,29,.10) | rgba(245,234,216,.10) | outlineVariant |
| Obramowanie pól | rgba(32,30,29,.16) | rgba(245,234,216,.16) | outline |
| Zielony blok (zrealizowany wynik, puste stany) | #e1eecc / tekst #3d472b | #3d472b / tekst #e1eecc | (własny) |
| Tło widgetu | #f9f4ed | #262420 | (Glance) |
| Tor przełącznika wył. | #dcd3c4 | #474238 | — |
| Scrim | rgba(46,43,37,.42) | rgba(0,0,0,.55) | scrim |

Paleta kolorów udziałów (donut i pasek struktury), w kolejności malejącej wartości: #c67139, #7a8a5e, #d9a441, #8c491a, #a19786.

Neutralny wynik (|%| < 0,05) używa koloru muted. Kolory zysku i straty dobieraj funkcją `tone()`, odpowiednikiem istniejącego `changeColor()`.

Wyłącz Material You (dynamic color) albo zostaw je jako opcję. Domyślnie ma działać powyższa paleta.

### Typografia: Manrope (Google Fonts, wagi 400–800)
Jeden krój w całej aplikacji, z cyframi tabelarycznymi (`fontFeatureSettings = "tnum"`) wszędzie tam, gdzie są liczby.
| Styl | Rozmiar | Waga | Interlinia | Użycie |
|---|---|---|---|---|
| Wartość portfela (hero) | 40 | 700 | 1.1, tracking −0.01em | Portfel |
| Cena instrumentu | 38 | 700 | 1.1 | Instrument |
| Tytuł ekranu duży | 30 | 700 | 1.1 | Podsumowanie, Historia, Ustawienia |
| Tytuł ekranu mały | 22 | 700 | — | Portfel (1b) |
| Tytuł paska górnego | 20–21 | 700 | 1.15 | Instrument, Dodaj zakup |
| Wynik pozycji | 20 | 700 | — | lista Portfel |
| Symbol pozycji, sekcja | 19 | 700 | 1.15 | lista, „Zakupy”, karty |
| Wartość statystyki | 16 | 700 | — | siatka statystyk |
| Treść | 14–15 | 400–600 | 1.4 | |
| Etykieta / meta | 12–13 | 400–600 | — | muted |
| Pigułka zakresu | 13 | 700 | — | |
| Etykieta nav | 11.5 | 700 aktywna / 500 | — | |

### Kształt i odstępy
- Pigułki (radius 999) dla przycisków, pól tekstowych, segmentów, chipów zakresów, wskaźnika nawigacji i pasków.
- Karty i kontenery: radius 32 (Podsumowanie, Ustawienia), 28 (Historia, karta instrumentu w edytorze), 22–24 (menu kontekstowe, wewnętrzne listy).
- Arkusz dolny: radius 32 u góry. Dialog: radius 32.
- Marginesy ekranu: 20 dp (lista 1b) albo 16 dp (ekrany z kartami).
- Cienie: sm `0 1px 2px rgba(46,43,37,.08)`, md (FAB, snackbar), lg (menu, arkusz, dialog). Użyj odpowiedników `shadowElevation` 1 / 3 / 6.

### Ikony
Lucide, linia 2.5 (w Compose: odpowiedniki Material Symbols Rounded albo wektory Lucide w `res/drawable`). Użyte: wallet, chart-pie, history, settings, plus, arrow-left, ellipsis-vertical, refresh-cw, search, calendar, x, check, chevron-up/down, trash-2, pencil, hand-coins, signal/wifi/battery (tylko pasek statusu makiety).

### Ruch
Główna krzywa: `cubic-bezier(.2,0,0,1)` (M3 emphasized decelerate).
- Wejście w instrument i edytor: ekran wsuwa się z prawej (translateX 104% → 0, 420–440 ms), ekran pod spodem przesuwa się o −56 dp (paralaksa).
- Zmiana zakładki: fade 240 ms + translateY 12 → 0 dp w 360 ms.
- FAB: pojawia się tylko na Portfelu, scale .8 + translateY 20 → 1 (320 ms).
- Arkusz sprzedaży: translateY 105% → 0 (420 ms), scrim fade 320 ms.
- Dialog: scale .94 → 1 (300 ms) + fade 240 ms.
- Przełącznik: kciuk 240 ms, kolor toru 220 ms.
- Odświeżanie: ikona obraca się o 360° w 1.1 s. Po zakończeniu pojawia się snackbar „Kursy zaktualizowane” na 2.2 s.
- Zmiana zakresu wykresu: morph ścieżki 420 ms (w Compose: animuj punkty serii).
- Naciśnięcie: scale .96–.985.

## Ekrany

### 1. Portfel (zakładka 1, wariant 1b)
- **Pasek górny** (padding 18/20/0): „Portfel” 22/700, po prawej godzina aktualizacji 12 muted i okrągły przycisk odświeżania 40 dp (ikona 18, hover/pressed: tło surface).
- **Hero** (padding 22/20/0): „Wartość portfela” 13 muted. Pod spodem kwota 40/700 (suma w PLN, bez prefiksu ≈). Dalej wiersz 15: wynik % 700 w kolorze tonu i „{zysk} od zakupu” muted.
- **Pasek struktury**: wysokość 10, odstęp 3 między segmentami, każdy segment pigułką o szerokości proporcjonalnej do udziału (kolory z palety udziałów). Pod nim legenda w zawijanym wierszu (gap 6/14): kropka 8, symbol 12, udział % muted.
- **Lista pozycji** (padding 30/20/120, gap 30, bez kart):
  - Wiersz 1: symbol 19/700 i pod nim nazwa 12 muted z wielokropkiem. Po prawej wynik łączny % 20/700 w kolorze tonu.
  - Wiersz 2 (12, mt 6): bieżąca cena (tekst 600), zmiana dzienna „+1,2% dziś” w kolorze tonu, „·”, „śr. {średnia}” muted.
  - Zakupy (mt 10), wiersz po 30 dp, gap 12: etykieta o szerokości 132 „dd.MM.yy  @cena” (cena muted). Dalej **pasek odchylenia**: tor 5 dp w kolorze linii z wypełnieniem od środka (50%) w prawo dla zysku i w lewo dla straty. Szerokość = min(50%, |%| / 30 × 50%), czyli ±30% wypełnia pół toru. Po prawej % 13/700, szerokość 54, wyrównanie do prawej.
  - Kliknięcie w pozycję otwiera Instrument.
- **Extended FAB** „Dodaj zakup”: prawa krawędź 16, 96 nad dołem (nad nav barem), wysokość 56, padding 0 22 0 18, pigułka w kolorze akcentu, ikona plus 22, tekst 16/700, cień md.
- **Pasek nawigacji**: wysokość 80, tło surface, padding dolny 14. Cztery pozycje: Portfel, Podsumowanie, Historia, Ustawienia. Wskaźnik 60×32 pigułka (secondaryContainer), ikona 21, etykieta 11.5.

### 2. Instrument (wariant 1b)
- **Pasek górny** 64: wstecz (48 okrągły), symbol 20/700 i nazwa 12 muted, plus (48), który otwiera edytor z wybranym instrumentem.
- Treść (padding 4/20/40): cena 38/700. Wiersz 14: zmiana dzienna (700, kolor tonu) · „wynik” muted · wynik % (700, kolor tonu).
- **Wykres** bez karty, wysokość 200, mt 30: obszar z wypełnieniem i linia 2.5 w kolorze wykresu. Poziome linie referencyjne: cena każdego zakupu (przerywana 4/5, kolor tonu zakupu, 1.5 dp) i średnia (kropkowana 1/5, muted). Etykiety max/min 11 muted przy prawej krawędzi.
  - **Odczyt**: dotknięcie lub przeciągnięcie pokazuje pionową linię 1.5 dp (tekst 30% krycia), kropkę 12 dp z obwódką 3 dp w kolorze tła i dymek-pigułkę nad wykresem (tło = kolor tekstu, tekst = kolor tła, 12/600, „{cena} · {data}”, dla 1T także godzina). Przy krawędziach dymek jest dociągany do lewej lub prawej.
- **Zakresy** (mt 18, rozłożone na całą szerokość): 1T, 1M, 6M, 1R, MAX. Pigułki 34 dp, min. szer. 50, 13/700. Aktywna ma tło w kolorze tekstu i tekst w kolorze tła, nieaktywne są przezroczyste z tekstem muted. Domyślnie 6M.
- Podpis 12 muted: „Przerywane linie to ceny Twoich zakupów, kropkowana to średnia. Dotknij wykresu, żeby odczytać wartości.” Dla krypto przy MAX dochodzi zdanie o limicie 365 dni.
- **Statystyki**: siatka 2 kolumny, gap 18/16, mt 30. Etykieta 12 muted, wartość 16/700. Pola: Średnia cena (ważona) albo Średnia cen zakupu, Liczba zakupów, Ilość, Zainwestowano, Wartość, Zysk / strata (kolor tonu). Pola bez danych są pomijane.
- **Zakupy** (nagłówek 19/700, mt 36). Wiersz z paddingiem 12: data 15/600, pod nią „@cena  × ilość” 12 muted. Po prawej % 16/700 i kwota 12 w kolorze tonu, przycisk „więcej” 40. Pod wierszem pasek odchylenia 5 dp z kreską środka (1.5 dp, muted 50%).
  - **Menu „więcej”** (DropdownMenu): tło surface, radius 22, cień lg, padding 6. Pozycje 44 dp, radius 16, 14/600, ikona 18: Edytuj, Sprzedaj, Usuń (Usuń w kolorze straty).
- **Sprzedane** (19/700, mt 28): „data zakupu → data sprzedaży”, „@kupno → sprzedaż  × ilość”, % i kwota.

### 3. Sprzedaż (ModalBottomSheet)
Tło surface, radius 32 u góry, padding 10/20/28, uchwyt 36×4 (muted 40%). Tytuł „Sprzedaż” 24/700, pod nim „Kupione {data} po {cena}” 14 muted. Pola-pigułki 50 dp z etykietą nad polem (12 muted, wcięcie 18): Data sprzedaży (z ikoną kalendarza), Cena sprzedaży ({waluta}), Sprzedana ilość (maks. X), gdy zakup ma ilość. Wiersz-pigułka „Wynik” na tle inset: kwota 13 i pigułka % 15/700 (tło tonu). Wynik liczy się na żywo. Przyciski 52: „Anuluj” (outlined) i „Sprzedaj” (filled). Po zatwierdzeniu: snackbar „Sprzedano · wynik +X%”.

### 4. Usuwanie (AlertDialog)
Radius 32, padding 24. „Usunąć zakup?” 22/700, „Zakup z {data} zostanie usunięty na stałe.” 14 muted. Przyciski: „Anuluj” (text) i „Usuń” (tło straty, tekst straty, pigułka 44). Po usunięciu: snackbar „Zakup usunięty”.

### 5. Podsumowanie
Tytuł 30/700 (padding 18/20/10). Karty surface radius 32, padding 20, gap 12, margines 16:
1. **Wartość portfela**: kwota 30/700 z prefiksem „≈ ” i % 15/700. Wiersze 14: Zainwestowano, Zysk / strata. Sekcja „Według walut” (12/700 muted) z wierszami-pigułkami 34 dp na tle inset: kod waluty 12/700, wartość, zysk 12, % 13/700. Przypis 12 muted o kursach NBP.
2. **Struktura portfela**: segment „Instrumenty / Typy” (pigułki 38, aktywny w kolorze akcentu). Donut 124 dp (grubość 5/36 średnicy, zaokrąglone końce, przerwy między segmentami, animacja 500 ms) i legenda: kropka 10, nazwa 13, udział muted.
3. **Wartość w czasie**: chipy 6M / 1R / MAX (34 dp). Wykres 170 dp: obszar i linia „Wartość” w kolorze wykresu, przerywana linia „Zainwestowano” w drugim kolorze, max/min 11 muted. Legenda i przypis „W PLN po kursie NBP z każdego dnia.”
4. **Zrealizowany wynik** na zielonym bloku: wiersze per waluta i „Razem w PLN”.
Przypis o zakupach bez ilości.

### 6. Historia
Tytuł 30/700. Karty surface radius 28, padding 16/18, gap 10: symbol 18/700, nazwa 12 muted, po prawej % 15/700 i kwota 12. Pod spodem „data → data” 13 i „@kupno → sprzedaż × ilość” 12 muted. Kliknięcie otwiera instrument, jeśli jest jeszcze w portfelu. Na dole zielony blok „Zrealizowany wynik”.

### 7. Ustawienia
Tytuł 30/700, sekcje z nagłówkiem 18/700 i kartą surface radius 32:
- **Alerty**: przełącznik „Alerty progowe” (52×32, tor w kolorze akcentu albo wyłączony, kciuk 24) z opisem. Po włączeniu dwa pola-pigułki 48: „Alert wzrostu, %” i „Alert spadku, %”, pod nimi „0 wyłącza dany alert.”
- **Widget**: podgląd paska 4×1 na kolorowej „tapecie” (radius 24, 96 dp), który zmienia się na żywo. Segment Motyw: Systemowy / Jasny / Ciemny. Suwak „Krycie tła” 0–100 co 10 z wartością %. Lista „Widoczne na widgecie” na tle inset: checkbox 24 (radius 8), symbol i nazwa (wyszarzone do 55%, gdy ukryte), strzałki w górę i w dół 40 dp (nieaktywne do 30%). Podpis „Kolejność wspólna z aplikacją”.
- **Odświeżanie**: tekst o auto-odświeżaniu co 30 min, „Ostatnio dziś, HH:mm” i przycisk outlined „Odśwież teraz” (podczas odświeżania „Odświeżanie…”).
- **O aplikacji**: źródła kursów, zastrzeżenie, wersja.

### 8. Dodaj / edytuj zakup (pełny ekran)
- Pasek: wstecz, „Dodaj zakup” albo „Edytuj zakup”, przy edycji ikona kosza (kolor straty), która otwiera dialog usuwania.
- **Krok 1: wyszukiwanie**: pole-pigułka 52 z obwódką 1.5 w kolorze akcentu, ikoną lupy i czyszczeniem. Placeholder „np. PKN.PL, AAPL, VWCE, bitcoin”. Bez zapytania: podpowiedź o tickerach XTB. Wyniki grupowane: „Akcje i ETF-y” i „Kryptowaluty” (13/700 w kolorze tertiary). Wiersz: symbol 15/700, nazwa 12 muted, „Typ · giełda” po prawej, hover/pressed tło surface radius 24. Brak wyników: „Nic nie znaleziono.”
- **Krok 2: formularz**: karta instrumentu (surface, radius 28): symbol 19/700, nazwa i giełda, bieżąca cena. Pola-pigułki 52 z etykietą nad polem: Data zakupu (kalendarz), Cena za sztukę ({waluta}). Pod ceną: „Bieżący kurs: X”. Przy pustym polu pojawia się akcja tekstowa „Użyj bieżącego kursu”, a po wpisaniu podgląd „+X% teraz” w kolorze tonu. Ilość (opcjonalnie) z podpisem o kwocie zysku. Przełącznik „Własne progi alertów” (domyślne +10% / −10%) rozwija dwa pola. Przycisk „Zapisz” 54 w kolorze akcentu, wyłączony (45% krycia), dopóki brak poprawnej ceny. Po zapisie: snackbar „Dodano zakup {symbol}” albo „Zmiany zapisane”.
- Wstecz z formularza (przy dodawaniu przez wyszukiwarkę) wraca do kroku 1.

### 9. Stany puste
Okrąg 112 dp (zielony blok albo brzoskwiniowy wskaźnik dla Historii) z ikoną 44, tytuł 24–26/700, opis 15 muted (max 290 dp), wyrównanie do lewej.
- Portfel: „Brak zakupów” + „Dodaj akcję, ETF lub kryptowalutę z ceną, po której kupiłeś. Widget pokaże wynik każdego zakupu.” + przycisk 52 „Dodaj zakup”. FAB jest wtedy ukryty.
- Podsumowanie: „Nie ma jeszcze czego podsumować”.
- Historia: „Nic jeszcze nie sprzedano”.

### Snackbar
Pigułka: tło w kolorze tekstu, tekst w kolorze tła, 14/600, padding 12/20, wyśrodkowana, 96 dp nad dołem (28 dp, gdy otwarty jest pełny ekran). Wejście: fade i translateY 16 → 0.

## Widget (Glance), wariant 1b
Wspólne: tło widgetu (#f9f4ed / #262420) z kryciem z Ustawień (domyślnie ok. 90%), **radius 22** we wszystkich rozmiarach, tekst w kolorach motywu, ikona odświeżania 14–16 muted w prawym górnym rogu, która odświeża kursy (jak dziś). Motyw: Systemowy / Jasny / Ciemny z Ustawień. Rozmieszczenie zależne od rozmiaru: `SizeMode.Responsive`. W makiecie widać rozmycie pod widgetem; Glance go nie obsługuje, więc zostaje samo półprzezroczyste tło.

| Rozmiar | Makieta (dp) | Zawartość |
|---|---|---|
| 2×2 | 156×156 | padding 14/14/12. U góry „Portfel · HH:mm” 11/600 muted i ikona odświeżania. Wynik łączny % 30/700 w kolorze tonu, kwota 13/600. Na dole sparkline 30 dp wartości portfela z 1R (linia 2 dp w kolorze wykresu). |
| 4×1 | 328×72 | padding 0/16/0/18. Trzy pierwsze pozycje obok siebie: symbol 11/600 muted i % 16/700 w kolorze tonu. Ikona odświeżania na końcu. |
| 4×2 | 328×156 | padding 12/12/10. Nagłówek „Portfel” 13/700, godzina 11 muted, odświeżanie. Do 4 pozycji, wiersze 22 dp, gap 4: symbol (szer. 64, 12/600), pasek odchylenia 6 dp z kreską środka i % (szer. 52, 13/700). |
| 4×3 | 328×240 | jak 4×2 w nagłówku. Przewijana lista (LazyColumn) wszystkich pozycji: symbol 12/700 i cena 12 muted. Pod spodem każdy zakup w wierszu 20 dp: „dd.MM.yy  @cena” 11 muted (szer. 104), pasek odchylenia 4 dp, % 12/700. |

Uwagi Glance: pasek odchylenia zrób jako `Row` z dwiema połówkami (`defaultWeight`) i `Box` o szerokości wyliczonej w dp, albo jako bitmapę. Sparkline narysuj na `Canvas` do `Bitmap`, a potem `Image`. Kliknięcie w widget otwiera aplikację, a w pozycję otwiera Instrument.

Kolejność i widoczność pozycji na widgecie pochodzą z Ustawień, tak jak dziś.

## Stan
Bez zmian w modelu danych. Nowe pola UI-state:
- Instrument: `range` (domyślnie 6M), `scrubIndex: Int?`, `menuLotId: Long?`.
- Podsumowanie: `allocationBy: Instrument|Type`, `timelineRange: 6M|1R|MAX`.
- Sprzedaż: `price`, `qty` z walidacją (qty ≤ ilość zakupu), wynik liczony na żywo.
- Edytor: `step: Search|Form`, `query`, pola formularza, `customThresholds`.
- Ustawienia: przełącznik alertów, progi, motyw widgetu, krycie (0–100 co 10), lista widoczności i kolejności.

Formatowanie liczb jak w istniejącym `Formatters.kt` (spacja nierozdzielająca jako separator tysięcy, przecinek dziesiętny, minus U+2212, „+” dla zysku).

## Pliki
- `Prototyp 1b.dc.html`: punkt wejścia (interaktywne telefony jasny i ciemny, pulpit z widgetami, wszystkie ekrany).
- `MT App.dc.html`: wszystkie ekrany aplikacji. Wariant 1b to bloki `show.pb` (Portfel) i `show.db` (Instrument); reszta ekranów jest wspólna.
- `MT Widget.dc.html`: widgety. Wariant 1b to bloki `s2x2b`, `s4x1b`, `s4x2b`, `s4x3b`.
- `MT Home.dc.html`: makieta pulpitu.
- `mt-data.js`: dane przykładowe, formatery, generowanie wykresów, tokeny motywów (`THEMES`).
- `_ds/…/styles.css`: tokeny systemu Organic (źródło palety).
