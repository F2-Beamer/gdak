# Mitwirken an GDAK

Danke, dass du GDAK verbessern möchtest. Diese Hinweise helfen dabei, Änderungen schnell zu prüfen.

## Voraussetzungen

- Android Studio (aktuelle Version)
- JDK 17
- Android SDK mit `compileSdk 37`

## Projekt bauen und testen

Der Gradle-Projektordner ist `GdakApp/`.

```bash
cd GdakApp
./gradlew assembleDebug
./gradlew test
```

Unter Windows heißt der Befehl `gradlew.bat`.

## Änderungen vorschlagen

1. Erstelle einen Branch von `main`.
2. Halte die Änderung klein und auf ein Thema beschränkt.
3. Führe `./gradlew test` aus und prüfe, ob der Debug-Build durchläuft.
4. Öffne einen Pull Request mit einer kurzen Beschreibung, was sich ändert und warum.

## Code-Stil

- Kommentare, Fehlermeldungen und Texte in der App sind auf Deutsch. Bitte bleib dabei.
- Passe dich dem vorhandenen Stil an: Namensgebung, Kommentardichte und Aufbau.
- Keine Secrets, Keystores oder `local.properties` committen. Diese Dateien sind in `.gitignore` ausgeschlossen.

## Release-APK signieren

Die Release-APK wird mit einem eigenen Schlüssel signiert. Der Schlüssel (`.jks` oder `.keystore`) und seine Passwörter gehören nicht ins Repository. `.gitignore` schließt `*.jks`, `*.keystore` und `keystore.properties` aus.

In Android Studio: **Build → Generate Signed App Bundle / APK** und dort den Schlüssel auswählen. Die Build-Konfiguration im Repository enthält keine Signierdaten.

## Sicherheitslücken

Bitte melde Sicherheitslücken nicht über Pull Requests oder Issues. Nutze stattdessen die Funktion „Report a vulnerability“ im Tab **Security** des GitHub-Repositorys.
