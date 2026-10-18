# Anforderungen: Geocaching-App (GDAK-Nachbau), Version 1

## Plattform und Grundprinzip
- Native Android-App, mindestens Android 8.0 (API 26)
- Nur Deutsch
- Komplett offline nutzbar
- Nur lesend: Notizen, Logs und Status werden nicht in der App bearbeitet

## Offline-Karten
- Import über die Android-Dateiauswahl (Storage Access Framework)
- Die Karte wird in den App-Speicher kopiert; vorher prüft die App den freien Speicher
- Mehrere Karten speicherbar, aber nur eine ist aktiv
- Karten können gelöscht werden (mit Bestätigung)
- Ein Standard-Theme mit Hell- und Dunkelmodus
- Standard: Systemeinstellung, im Menü umstellbar
- Die eigene GPS-Position wird als Markierung angezeigt, mit Schaltfläche zum Zentrieren

## Datenbanken (GSAK-Format, SQLite)
- Import über die Android-Dateiauswahl; Dateiname ist egal
- Vor dem Import wird ein benannter Eintrag im Menü angelegt, in den die Datei importiert wird
- Geprüft wird das GSAK-Schema (Tabelle `Caches` muss vorhanden sein)
- Standard beim Import: Zusammenführen nach Cache-Code (vorhandene Caches werden aktualisiert, neue hinzugefügt, nur in der alten Version vorhandene bleiben erhalten)
- Option „Vorher leeren“: entfernt vorher alle Caches dieser Datenbank
- Bei einem Update wird der bestehende Eintrag ersetzt; die neue Datei wird zuerst in eine temporäre Kopie geschrieben und geprüft
- Mehrere Datenbanken speicherbar, nur eine ist aktiv
- Datenbanken können gelöscht werden (mit Bestätigung); die Originaldatei bleibt unberührt

## Fehlerbehandlung (Standardannahme)
- Beschädigte oder nicht passende Datenbank: Import wird mit Fehlermeldung abgebrochen, die alte Version bleibt erhalten
- Karte, die nicht geladen werden kann: Fehlermeldung, keine Änderung am Bestand

## Cache-Anzeige auf der Karte
- Standardmäßig werden alle Caches der aktiven Datenbank angezeigt, auch archivierte
- Archivierte Caches werden ausgegraut dargestellt
- Eigene Symbole pro Cache-Typ (Buchstabe und Farbe), keine Groundspeak-Icons
- Typen aus der Beispieldatenbank: T (Traditional), M (Multi), U (Unbekannt/Mystery), B (Bonus)
- Suche nach Cache-Code oder Name

## Filter (Karte und Liste)
- Cache-Typ (Mehrfachauswahl)
- Status: gefunden, nicht gefunden, DNF, archiviert ein/aus
- Difficulty und Terrain als Bereich
- Entfernung zur eigenen Position (z. B. Umkreis in km)

## Schnellansicht (Klick auf Symbol, Bottom-Sheet)
- Name, Code, Typ, Größe, Difficulty/Terrain, Besitzer, Versteckdatum
- Status (gefunden, DNF, archiviert)
- Eigene Notiz (`UserNote`)
- Schaltfläche „Details“

## Detailansicht
- Kurz- und Langbeschreibung
- Hinweise
- Logs (begrenzt, z. B. die letzten 20)
- Waypoints
- Attribute

## Liste
- Zweite Ansicht neben der Karte, mit denselben Daten und Filtern wie die Karte
- Tipp auf einen Eintrag öffnet die Schnellansicht bzw. zentriert die Karte auf den Cache

## Einstellungen
- Dunkler/heller Modus (Standard: Systemeinstellung)

## Nicht in Version 1
- Bilder (Cache- und Log-Bilder)
- Navigation zum Cache über externe Apps
- Bearbeiten von Notizen, Logs oder Status
- Eigene Render-Themes für die Karte
- Weitere Sprachen

## Beispieldaten (Referenz)
- `Beispiele_und_Daten/Freizeitkarte_ALPS_EAST.map` (Mapsforge-Format, ca. 2,4 GB)
- `Beispiele_und_Daten/ignore_sqlite.db3` (GSAK-Schema, 101 Caches, 13.737 Logs, 139 Waypoints)
- `Beispiele_und_Daten/eigene_sqlite.db3` (leer)
- Screenshots `IMG-20261017-WA*.jpg` (GDAK-Oberfläche)
