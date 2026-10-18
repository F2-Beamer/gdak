# GDAK

Eine native Android-App zum Ansehen von Geocaching-Daten offline, ein Nachbau der Geocaching-App GDAK.

Die App ist **nur lesend**: Notizen, Logs und Status werden in der App nicht bearbeitet. Die Oberfläche ist ausschließlich auf Deutsch.

> Status: Version 1 in Entwicklung. Die Anforderungen stehen in [`Anforderungen.md`](Anforderungen.md), die technische Umsetzung in [`Technikplan.md`](Technikplan.md).

## Funktionen

- **Offline-Karten:** Mapsforge-Karten (`.map`) über die Android-Dateiauswahl importieren, mehrere speichern, eine davon aktiv nutzen
- **Cache-Datenbanken:** GSAK-Datenbanken (`.db3`) importieren, mit Zusammenführen nach Cache-Code oder „Vorher leeren“
- **Karte:** Caches mit Symbolen pro Typ (T, M, U/B, „?“), gefundene Caches mit Smiley, archivierte Caches ausgegraut, korrigierte Koordinaten mit rotem Rand
- **Eigene Position:** Standortmarkierung und Schaltfläche zum Zentrieren
- **Suche und Filter:** nach Code oder Name, Cache-Typ, Status (gefunden, nicht gefunden, DNF, archiviert), Difficulty und Terrain, Umkreis
- **Liste:** dieselben Daten und Filter wie die Karte, sortiert nach Entfernung zur Kartenmitte
- **Schnellansicht und Detailansicht:** Größe, Difficulty/Terrain, Besitzer, Versteckdatum, Beschreibungen, Hinweise, eigene Notiz, Wegpunkte, Attribute und die letzten 20 Logs
- **Darstellung:** Hell- und Dunkelmodus, Standard ist die Systemeinstellung

## Datenschutz und Speicherort

Alle Daten bleiben auf dem Gerät. Karten und Datenbanken werden im App-Speicher abgelegt. Die App braucht keine Internetverbindung.

## Technik

| Bereich | Umsetzung |
|---|---|
| Sprache und UI | Kotlin, Jetpack Compose, Material 3 |
| Karte | Mapsforge (`mapsforge-map-android`) |
| Cache-Datenbank | Android-`SQLiteDatabase` (kein Room, weil GSAK ein eigenes Schema hat) |
| Import | Storage Access Framework, Transaktionen mit Rollback |
| Einstellungen | DataStore |
| Mindestversion | Android 8.0 (API 26) |

## Projektstruktur

```
GdakApp/            Android-Projekt (Gradle)
  app/src/main/     Anwendung
  app/src/test/     Unit-Tests (JVM)
  app/src/androidTest/  Tests auf Gerät oder Emulator (Repository, Import)
Anforderungen.md    Funktionale Anforderungen
Technikplan.md      Technische Umsetzung und Umsetzungsreihenfolge
CLAUDE.md           Hinweise für die Arbeit mit Claude Code
```

Die Beispieldaten (Karte, Datenbanken, Fotos) liegen in `Beispiele_und_Daten/` und sind **nicht** im Repository enthalten.

## Bauen und Starten

Voraussetzungen: Android Studio (mit JDK 17 oder neuer) und ein Android-SDK für API 37.

```bash
cd GdakApp
./gradlew :app:assembleDebug
```

Die APK liegt danach unter `GdakApp/app/build/outputs/apk/debug/`.

## Tests

```bash
cd GdakApp
./gradlew :app:testDebugUnitTest          # Unit-Tests (Entfernung, Filter, Format, Datum)
./gradlew :app:connectedDebugAndroidTest  # Geräte-Tests (Datenbankabfragen, Import inkl. Rollback)
```

Die Geräte-Tests brauchen einen laufenden Emulator oder ein angeschlossenes Gerät.

## Bekannte Einschränkungen

- Attribute werden nur als Nummern angezeigt, die Namen folgen später
- Die Suche auf der Karte findet nur Caches im sichtbaren Kartenausschnitt
- Nicht in Version 1: Bilder, Navigation zu Caches über andere Apps, Bearbeiten von Notizen, Logs oder Status, eigene Render-Themes, weitere Sprachen

## Lizenz

Noch nicht festgelegt.
