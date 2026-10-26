# Technikplan: Geocaching-App (GDAK-Nachbau), Stand Version 2.2.1 (Build 5)

Grundlage: `Anforderungen.md`. Dieser Plan beschreibt den umgesetzten Stand. Die Versionen der Bibliotheken stehen in `GdakApp/gradle/libs.versions.toml`, dort ist die Quelle der Wahrheit. Offene Punkte stehen in Abschnitt 9.

## 1. Technologie-Stack
- **Sprache:** Kotlin 2.4
- **UI:** Jetpack Compose mit Material 3 (Compose BOM 2026.09.00), App-Theme über Einstellungen (System, Hell, Dunkel)
- **Navigation:** Navigation Compose 2.10.0
- **Karte:** `mapsforge-map-android` und `mapsforge-themes` 0.25.0, rendert `.map`-Dateien direkt. Es wird nur das Standard-Render-Theme verwendet, ohne Dunkelmodus (Entscheidung des Nutzers).
- **Datenbank der Caches:** Android-`SQLiteDatabase` (nicht Room), weil die GSAK-Datenbanken ein fremdes Schema haben
- **Dateiimport:** Storage Access Framework (`ActivityResultContracts.OpenDocument`)
- **Standort:** `LocationManager` (GPS- und Netzwerk-Provider) über `LocationManagerCompat`, zuerst die zuletzt bekannte Position. GPS funktioniert offline.
- **Einstellungen und Katalog:** DataStore (Preferences). Dort liegen das Theme (`ThemeMode`: SYSTEM, LIGHT, DARK), der Katalog der Karten und der Datenbanken sowie die zuletzt gezeigte Kartenposition.
- **Minimum und Ziel:** `minSdk 26`, `compileSdk`/`targetSdk` 37
- **Release-Build:** R8 aktiv (`isMinifyEnabled`), keine Internet-Berechtigung. Die APK heißt `GDAK-<versionName>-<buildType>.apk`.

## 2. Speicherstruktur (App-Speicher, `filesDir`)

```
filesDir/
  maps/
    <kartenId>.map          # importierte Mapsforge-Karte
    <kartenId>.partial      # Kopie während des Imports
  databases/
    <datenbankId>.db        # eine Datei pro benanntem Datenbank-Eintrag
  import_tmp/
    <tmpId>.source.db       # Kopie der gewählten GSAK-Datei
    <tmpId>.work.db         # Arbeitskopie für Schema-Prüfung und Zusammenführen
```

Der Katalog hält pro Eintrag ID, Anzeigename und Dateiname. Bei Datenbanken kommt die Anzahl der Caches dazu. Die aktive Karte und die aktive Datenbank werden ebenfalls in DataStore gehalten.

## 3. Import-Abläufe

### 3.1 Karte importieren
1. Dateiauswahl (`.map`), Anzeigename abfragen
2. Größe prüfen: Abbruch ab **4 GB** (`MapImporter.MAX_IMPORT_BYTES`), zusätzlich während des Kopierens
3. Freier Speicher prüfen, bei zu wenig Platz abbrechen
4. Kopie als `<id>.partial` direkt im Zielordner `maps/` schreiben
5. Kopf der Datei prüfen (Magic-Bytes `mapsforge binary OSM`), sonst abbrechen und die `.partial`-Datei entfernen
6. Erst nach Erfolg auf `<id>.map` umbenennen, Katalog aktualisieren

Mit dieser Reihenfolge gibt es keine doppelte Kopie im Speicher, und ein abgebrochener Import hinterlässt keinen gültigen Eintrag.

### 3.2 Datenbank importieren (mit Zusammenführen)
1. Dateiauswahl (`.db3`, Dateiname egal), Zieleintrag aus Liste wählen oder neu anlegen
2. Größe prüfen: Abbruch ab **512 MB** (`DatabaseImporter.MAX_IMPORT_BYTES`), zusätzlich während des Kopierens. Freier Speicher wird vorab geprüft.
3. Kopie nach `import_tmp/<tmpId>.source.db` schreiben
4. Schema prüfen: Tabelle `Caches` vorhanden, Pflichtspalten `Code`, `Name`, `Latitude`, `Longitude`, `CacheType`, `Archived`, `Found`
5. Ist der Eintrag noch leer oder „Vorher leeren“ gewählt: die Tabellen `Caches`, `CacheMemo`, `Waypoints` und `Logs` leeren und die Quelle übernehmen (Ersetzen)
6. Sonst **Zusammenführen** in einer Transaktion:
   - Arbeitskopie `<tmpId>.work.db` öffnen, Quelle per `ATTACH` einbinden (`ATTACH` ist nur außerhalb der Transaktion erlaubt)
   - `INSERT OR REPLACE` in `Caches` nach `Code`, ebenso `CacheMemo`
   - `Waypoints` und `Logs`: pro betroffenem Cache die Zeilen der Zieldatei ersetzen (`replaceChildRows`)
   - Commit nur bei Erfolg, bei Fehler Rollback, der alte Stand bleibt
7. Arbeitskopie an die Stelle der Datenbank-Datei setzen, temporäre Dateien löschen, Anzahl der Caches im Katalog aktualisieren

Das Ersetzen (Schritt 5) und das Zusammenführen (Schritt 6) laufen über dieselbe Transaktionslogik, damit ein Abbruch nie einen halben Stand hinterlässt.

## 4. Datenzugriff
- **Caches lesen:** `CacheRepository` über eine schreibgeschützt geöffnete `SQLiteDatabase` der aktiven Datenbank
- **Abfragen:**
  - Karte: Caches im sichtbaren Ausschnitt. Die Bounding-Box vergleicht `CAST(Latitude AS REAL)` und `CAST(Longitude AS REAL)`, weil die Spalten als `TEXT` gespeichert sind. Höchstens **500** Ergebnisse (`MAX_MAP_RESULTS`).
  - Filter: Typgruppen (`CacheTypeGroup`), Status, Bewertungen. Die Entfernung zur eigenen Position wird in der App berechnet, nicht in SQL.
  - Suche: `Code` oder `Name` mit `LIKE`. LIKE-Platzhalter in der Suche werden wörtlich behandelt.
- **Schnellansicht:** Zeile aus `Caches` plus `CacheMemo.UserNote`
- **Detailansicht:** `CacheMemo` (Beschreibungen, Hinweise), die letzten **20** Logs mit `LogMemo` (`LOG_LIMIT`), `Waypoints`, `Attributes`

## 5. Karte und Caches anzeigen
- `MapScreen` mit einem `TileRendererLayer` für die aktive `.map`-Datei und dem Standard-Render-Theme (`MapsforgeThemes.DEFAULT`). Kein Dunkelmodus für die Karte.
- Die Karte startet an der zuletzt gezeigten Position, sofern diese zur Kartendatei gehört. Sonst ist der Mittelpunkt der Kartendatei die Startposition.
- Cache-Symbole als eigene Overlay-Schicht (`CacheMarkers`): Buchstabe und Farbe pro Gruppe: T grün, M orange (Multi), M blau (Mystery), E, L, W, V mit eigener Farbe, EarthCache als Globus ohne Buchstaben, Sonstige mit „?“. Gefundene, korrigierte und archivierte Caches sind über Markierungen und Deckkraft unterscheidbar (archiviert: Deckkraft 150 von 255).
- Ein Tipp auf ein Symbol öffnet die Schnellansicht.
- GPS-Position als eigene Markierung (`LocationBlue`), Schaltfläche zum Zentrieren
- Die Statusleiste auf der Karte ist schwarz (`Scaffold` mit `containerColor`), damit Uhrzeit, Empfang und Akku lesbar bleiben.

## 6. Navigation und Bildschirme
- **Bottom-Navigation:** Karte, Liste, Verwaltung, Einstellungen
- **Detailansicht:** eigene Route mit dem Cache-Code als Argument
- **Filter:** `CacheFilterSheet` als `ModalBottomSheet`, mit Suchfeld `CacheSearchBar`
- **Liste:** gleiche Daten und Filter wie die Karte, Tipp öffnet die Schnellansicht
- **Schnellansicht:** `ModalBottomSheet` mit „Details“-Schaltfläche
- **Verwaltung:** Karten (aktiv setzen, importieren, löschen) und Datenbanken (aktiv setzen, importieren mit Zielauswahl und „Vorher leeren“-Option, löschen)
- **Einstellungen:** Darstellung (System, Hell, Dunkel) und Versionsanzeige. Das Theme gilt für die App, nicht für die Karte.

## 7. Projektstruktur (Ist-Stand)

```
app/src/main/java/de/gdak/app/
  data/
    catalog/        # Katalog der Karten und Datenbanken (DataStore)
    cachedb/        # SQLite-Zugriff, Abfragen, Filter, Detailmodelle
    importer/       # Kopieren, Prüfen, Ersetzen, Zusammenführen
    geo/            # Entfernungsberechnung, Bounding-Box
    settings/       # Theme-Einstellung (DataStore)
  ui/
    map/            # Mapsforge-Karte, Cache-Symbole, Schnellansicht, Positionsspeicher
    list/           # Caches-Liste
    detail/         # Detailansicht
    filter/         # Filter-Sheet, Suchfeld, Filter-ViewModel
    manage/         # Karten- und Datenbankverwaltung
    catalog/        # ViewModels der Verwaltung
    settings/
    location/       # Standortabfrage
    theme/          # Farben, Typografie, Theme
    common/         # gemeinsame Bausteine
    format/         # Formatierung
  GdakApp.kt        # Navigation und Scaffold (in ui/)
```

## 8. Tests
- **Vorhanden (Unit-Tests unter `app/src/test`):** `CacheFilterTest`, `GeoTest`, `CoordinateFormatTest`, `GermanDateTest`
- **Fehlend:** Unit-Tests für Schema-Prüfung und Zusammenführen mit kleinen Test-Datenbanken, Tests für den Kartenimport (Header-Prüfung, Abbruch bei Größenlimit)
- **Fehlend:** instrumentierte Tests für Import-Abläufe auf Gerät oder Emulator, inklusive Abbruch und Rollback
- **Testdaten:** `Beispiele_und_Daten/` als Referenz für die GSAK-Datenbank; für das Zusammenführen eine veränderte Kopie erzeugen

## 9. Offene technische Punkte
1. **Import-Tests fehlen:** siehe Abschnitt 8.
2. **Kartenimport, Header-Prüfung (F4):** Die Prüfung liest den Kopf nur mit einem einzigen `read`-Aufruf. Ein kurzer Lesevorgang kann so einen gültigen Kopf verfehlen. Die Prüfung sollte in einer Schleife bis zur vollen Länge lesen.
3. **Datenbank-Import, fremde Schemaobjekte (F3):** Der Import übernimmt Schemaobjekte aus der Quelldatei, die nicht zu den bekannten Tabellen gehören. Das ist noch nicht eingeschränkt.
4. **Abhängigkeiten:** Eine Prüfung auf bekannte Schwachstellen in den Bibliotheken ist nicht erfolgt.
5. **Clustering:** Die Karte begrenzt auf 500 Symbole. Für größere Ausschnitte ist Clustering nicht umgesetzt.
6. **Typ-Symbole:** Alle GSAK-Typen, die keiner Gruppe zugeordnet sind, landen in „Sonstige“ (Symbol „?“). Ob weitere Typen vorkommen, ist erst mit weiteren Datenbanken sicher.

## 10. Stand der Umsetzung
Umgesetzt sind: Projekt-Grundgerüst (Compose, Navigation, Theme, DataStore), Karten-Import und -Anzeige mit Mapsforge, Datenbank-Import mit Zusammenführen und Rollback, Verwaltung, Cache-Symbole auf der Karte, Schnellansicht, Filter, Suche, Liste, GPS-Position mit Zentrieren, Detailansicht.

Offen sind die Punkte aus Abschnitt 9 und die Testlücken aus Abschnitt 8.
