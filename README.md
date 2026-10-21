# GDAK

Eine native Android-App zum Ansehen von Geocaching-Daten offline, ein Nachbau der Geocaching-App GDAK.

Die App ist **nur lesend**: Notizen, Logs und Status werden in der App nicht bearbeitet. Die Oberfläche ist ausschließlich auf Deutsch.

> Status: Version 2.1.1 (Build 3) ist getestet. Die Anforderungen stehen in [`Anforderungen.md`](Anforderungen.md), die technische Umsetzung in [`Technikplan.md`](Technikplan.md).

## Funktionen

- **Offline-Karten:** Mapsforge-Karten (`.map`) über die Android-Dateiauswahl importieren, mehrere speichern, eine davon aktiv nutzen
- **Cache-Datenbanken:** GSAK-Datenbanken (`.db3`) importieren, mit Zusammenführen nach Cache-Code oder „Vorher leeren“
- **Karte:** Caches mit eigenem Symbol pro Typ, gefundene Caches mit Smiley, archivierte Caches ausgegraut, korrigierte Koordinaten mit rotem Rand. Die Karte zeigt höchstens 500 Caches. Sie beginnt unter der Statusleiste, damit Uhrzeit, Empfang und Akku lesbar bleiben.
- **Cache-Typen:** Traditionell (T), Multi-Cache (M), Mystery (U und B), Event-Cache (E), Lab-Cache (Q), Wherigo (I), Virtueller Cache (V), EarthCache (R). Alle anderen Typen erscheinen unter „Sonstige“ mit einem „?“. Jeder Typ hat im Filter eine eigene Auswahl.
- **Eigene Position:** Standortmarkierung und Schaltfläche zum Zentrieren. Beim ersten Öffnen der App zentriert die Karte auf die aktuelle Position, sofern sie im Kartenbereich liegt. Sonst zeigt sie die Mitte der Kartendatei.
- **Kartenansicht merken:** Die zuletzt gezeigte Ansicht bleibt erhalten, solange dieselbe Karte aktiv ist
- **Suche und Filter:** nach Code oder Name, Cache-Typ, Status (gefunden, nicht gefunden, DNF, archiviert), Difficulty und Terrain, Umkreis bis 100 km
- **Liste:** dieselben Daten und Filter wie die Karte. Sortiert wird nach Entfernung zur Kartenmitte, sonst zur eigenen Position. Ohne bekannte Position wird nach Name sortiert.
- **Schnellansicht:** Kerndaten, Typ, Größe, Difficulty/Terrain, Position, Besitzer, Versteckdatum und eigene Notiz
- **Detailansicht:** Kopf mit Code und Name bleibt beim Scrollen stehen. Darunter wechseln wischbare Tabs zwischen **Details** (Typ, Größe, Wertungen, Besitzer, Versteckdatum, Position, Status), **Beschreibung** (mit Wegpunkten), **Hinweisen**, **Eigenen Notizen**, **Attributen** und **Logs** (die letzten 20). Der GC-Code ist antippbar.
- **Darstellung:** Hell- und Dunkelmodus, Standard ist die Systemeinstellung
- **Version:** Die Versionsnummer steht in den Einstellungen. Die APK heißt zum Beispiel `GDAK-2.1.1-debug.apk`.

## Datenschutz und Speicherort

Alle Daten bleiben auf dem Gerät. Karten und Datenbanken werden im App-Speicher abgelegt. Die App hat keine Internet-Berechtigung und braucht keine Internetverbindung. Die einzige Berechtigung ist der Standort, und zwar nur für die Anzeige der eigenen Position. Das Android-Backup ist ausgeschaltet (`allowBackup="false"`).

## Technik

| Bereich | Umsetzung |
|---|---|
| Sprache und UI | Kotlin, Jetpack Compose, Material 3 |
| Karte | Mapsforge 0.25.0 (`mapsforge-map-android`, `mapsforge-themes`) |
| Cache-Datenbank | Android-`SQLiteDatabase` (kein Room, weil GSAK ein eigenes Schema hat) |
| Import | Storage Access Framework, Transaktionen mit Rollback, Größenlimit 512 MB |
| Einstellungen | DataStore |
| Mindestversion | Android 8.0 (API 26), Ziel- und Compile-SDK 37 |

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

Eine Release-APK entsteht in Android Studio über **Build → Generate Signed App Bundle / APK**. Der Signierschlüssel liegt nicht im Repository. Er muss separat gesichert werden, sonst lassen sich spätere Versionen nicht als Update installieren.

## Tests

```bash
cd GdakApp
./gradlew :app:testDebugUnitTest          # Unit-Tests (Entfernung, Filter, Koordinaten, Datum)
./gradlew :app:connectedDebugAndroidTest  # Geräte-Tests (Datenbankabfragen, Import inkl. Rollback)
```

Die Geräte-Tests brauchen einen laufenden Emulator oder ein angeschlossenes Gerät.

## Bekannte Einschränkungen

- Attribute werden nur als Nummern mit „ja“ oder „nein“ angezeigt, die Namen folgen später
- Die Suche auf der Karte findet nur Caches im sichtbaren Kartenausschnitt
- Die gespeicherte Kartenansicht gilt nur für die laufende Sitzung und wird nicht über einen Neustart hinweg gespeichert
- Nicht in Version 1: Bilder, Navigation zu Caches über andere Apps, Bearbeiten von Notizen, Logs oder Status, eigene Render-Themes, weitere Sprachen

## Lizenz

Copyright (C) 2026 F2-Beamer. GDAK steht unter der GNU General Public License Version 3 (GPL-3.0). Den vollständigen Lizenztext findest du in der Datei [LICENSE](LICENSE).
