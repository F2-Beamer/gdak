# Technikplan: Geocaching-App (GDAK-Nachbau), Version 1

Grundlage: `Anforderungen.md`. Dieser Plan beschreibt die technische Umsetzung. Versionsnummern von Bibliotheken werden beim Projektaufbau geprüft, nicht hier festgeschrieben.

## 1. Technologie-Stack
- **Sprache:** Kotlin
- **UI:** Jetpack Compose mit Material 3 (Hell- und Dunkelmodus über das Systemtheme)
- **Karte:** `mapsforge-map-android` (Mapsforge-Bibliothek für Android, rendert `.map`-Dateien direkt)
- **Datenbank der Caches:** Android-`SQLiteDatabase` (nicht Room), weil die GSAK-Datenbanken ein fremdes Schema haben
- **Dateiimport:** Storage Access Framework (`ActivityResultContracts.OpenDocument`)
- **Standort:** `FusedLocationProviderClient` (bzw. `LocationManager` als Fallback), GPS funktioniert offline
- **Einstellungen:** DataStore (Preferences) für Theme und die aktive Karte bzw. Datenbank
- **Minimum:** `minSdk 26`

## 2. Speicherstruktur (App-Speicher, `filesDir`)

```
filesDir/
  maps/
    <kartenId>.map          # kopierte Mapsforge-Karte
  databases/
    <datenbankId>.db        # eine Datei pro benanntem Datenbank-Eintrag
  import_tmp/               # temporäre Kopien beim Import
```

Ein Katalog (Metadaten) wird in DataStore oder einer kleinen eigenen SQLite-Datei gehalten:
- Liste der Karten: `id`, `anzeigename`, `dateiname`, `größe`, `importiert_am`
- Liste der Datenbanken: `id`, `anzeigename`, `dateiname`, `anzahl_caches`, `importiert_am`
- aktive Karte: `id`
- aktive Datenbank: `id`

## 3. Import-Abläufe

### 3.1 Karte importieren
1. Dateiauswahl (`.map`), Anzeigename abfragen
2. Freier Speicher prüfen (Dateigröße + Puffer), bei zu wenig Platz abbrechen
3. Kopie nach `import_tmp/` schreiben
4. Kopf der Datei prüfen (Magic-Bytes `mapsforge binary OSM`), sonst abbrechen
5. Bei Ersetzen eines bestehenden Eintrags: alte Datei erst nach erfolgreicher Kopie löschen
6. Verschieben nach `maps/`, Katalog aktualisieren

### 3.2 Datenbank importieren (mit Zusammenführen)
1. Dateiauswahl (`.db3`, Dateiname egal), Zieleintrag aus Liste wählen oder neu anlegen
2. Kopie nach `import_tmp/` schreiben
3. Schema prüfen: `SELECT name FROM sqlite_master WHERE name='Caches'`, Pflichtspalten `Code`, `Name`, `Latitude`, `Longitude`, `CacheType`, `Archived`, `Found` vorhanden
4. Ist der Eintrag noch leer oder „Vorher leeren“ gewählt: Zieldatei durch neue Datei ersetzen
5. Sonst **Zusammenführen** in einer Transaktion:
   - Zieldatei öffnen, neue Datei per `ATTACH` einbinden
   - `INSERT OR REPLACE` in `Caches` nach `Code`
   - `CacheMemo`, `Waypoints` ebenso nach Schlüssel
   - `Logs`: pro betroffenem `Code` die alten Logs löschen und neu einfügen (Logs ohne Cache-Eintrag werden nicht übernommen)
   - Commit nur bei Erfolg; bei Fehler Rollback, alte Daten bleiben
6. Temporäre Datei löschen, Anzahl der Caches im Katalog aktualisieren

Die Ersetzung nach Schritt 4 und das Zusammenführen nach Schritt 5 laufen über dieselbe Transaktionslogik, damit ein Abbruch nie einen halben Stand hinterlässt.

## 4. Datenzugriff
- **Caches lesen:** Schreibgeschützt geöffnete `SQLiteDatabase` der aktiven Datenbank
- **Abfragen:**
  - Karte: Caches innerhalb des sichtbaren Kartenausschnitts (Bounding-Box auf `Latitude`/`Longitude`, die als `TEXT` gespeichert sind und vor dem Vergleich in `REAL` umgewandelt werden müssen)
  - Filter: Typ, Status, Difficulty, Terrain, Entfernung zur eigenen Position (Entfernung in der App berechnet, nicht in SQL)
  - Suche: `Code` oder `Name` (`LIKE`)
- **Schnellansicht:** Caches-Zeile plus `CacheMemo.UserNote`
- **Detailansicht:** `CacheMemo` (Beschreibungen, Hinweise), `Logs` mit `LogMemo` (begrenzt), `Waypoints`, `Attributes`

Hinweis zu den Koordinaten: Die Spalten `Latitude` und `Longitude` sind in der Beispieldatenbank `TEXT` (z. B. `48.109433`), nicht `REAL`. Das muss in den Abfragen beachtet werden.

## 5. Karte und Caches anzeigen
- Eine `MapView` aus Mapsforge, die die aktive `.map`-Datei lädt
- Standard-Render-Theme von Mapsforge, mit hellem und dunklem Stil passend zum Systemtheme
- Cache-Symbole als eigene Overlay-Schicht (Marker mit Buchstabe und Farbe pro Typ), für archivierte Caches mit reduzierter Deckkraft
- Overlay wird bei Kartenbewegung neu für den sichtbaren Ausschnitt aufgebaut, mit Begrenzung der Anzahl (101 Caches sind unkritisch; für größere Datenbanken später Clustering)
- GPS-Position als eigene Markierung, Schaltfläche zum Zentrieren

## 6. Navigation und Bildschirme
- **Hauptbildschirm:** Karte mit Suche, Filter und Umschalter Karte/Liste
- **Liste:** gleiche Daten und Filter wie die Karte, Tipp öffnet Schnellansicht
- **Schnellansicht:** `ModalBottomSheet` mit „Details“-Schaltfläche
- **Detailansicht:** eigene Seite
- **Verwaltung:** Karten (Liste, aktiv setzen, importieren, löschen) und Datenbanken (Liste, aktiv setzen, importieren mit Zielauswahl und „Vorher leeren“-Option, löschen)
- **Einstellungen:** Theme (System, Hell, Dunkel)

## 7. Projektstruktur (Vorschlag)

```
app/src/main/java/de/.../gdak/
  data/
    catalog/        # Katalog der Karten und Datenbanken (DataStore)
    cachedb/        # SQLite-Zugriff, Abfragen, Zusammenführen
    import/         # Kopieren, Prüfen, Ersetzen
  domain/
    model/          # Cache, Log, Waypoint, Filter
    geo/            # Entfernungsberechnung, Bounding-Box
  ui/
    map/            # Mapsforge-Karte, Cache-Overlay, GPS-Marker
    list/           # Caches-Liste
    detail/         # Schnellansicht, Detailansicht
    manage/         # Karten- und Datenbankverwaltung
    settings/
    theme/
```

Namensraum `de.…` und Paketname werden beim Anlegen festgelegt.

## 8. Tests
- **Unit-Tests:** Entfernungsberechnung, Filterlogik, Schema-Prüfung, Zusammenführen (mit kleinen Test-Datenbanken)
- **Instrumentierte Tests:** Import-Abläufe auf Gerät oder Emulator, inklusive Abbruch und Rollback
- **Testdaten:** `Beispiele_und_Daten/ignore_sqlite.db3` als Referenz; für Zusammenführen eine veränderte Kopie erzeugen

## 9. Offene technische Punkte (vor Umsetzung klären)
1. **Mapsforge-Version:** Die aktuelle Android-Bibliothek und ihre Abhängigkeiten werden beim Projektaufbau geprüft.
2. **Speicherplatz-Schätzung:** Kopie plus temporäre Kopie bei Import der 2,4-GB-Karte bedeutet kurzzeitig ca. 4,8 GB. Alternativ die Karte direkt in den Zielpfad schreiben und nur bei Erfolg den Eintrag aktivieren. Empfehlung: direkt in `maps/` schreiben, mit `.partial`-Endung und Umbenennung nach Erfolg.
3. **Logs-Anzeige:** 13.737 Logs bei 101 Caches sind in der Schnellansicht nicht nötig; in der Detailansicht nur die letzten 20 pro Cache laden.
4. **Typ-Symbole:** Welche Typen außer T, M, U, B vorkommen, ist erst mit weiteren Datenbanken sicher. Unbekannte Typen bekommen ein neutrales Symbol.

## 10. Umsetzungsreihenfolge (Vorschlag)
1. Projekt-Grundgerüst (Compose, Navigation, Theme, DataStore)
2. Karten-Import, Verwaltung und Anzeige mit Mapsforge
3. Datenbank-Import mit Zusammenführen und Rollback, Verwaltung
4. Cache-Overlay auf der Karte, Schnellansicht
5. Filter, Suche, Liste
6. GPS-Position und Zentrieren
7. Detailansicht
8. Tests und Feinschliff
