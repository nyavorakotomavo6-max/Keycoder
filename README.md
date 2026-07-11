# Nyavo Keyboard

Clavier Android (IME) écrit en Kotlin, sans dépendance réseau ni télémétrie.

## Prérequis

- JDK 17
- Android SDK (compileSdk 35)
- Gradle 8.9

## Build local

    gradle assembleDebug

L'APK généré se trouve dans `app/build/outputs/apk/debug/app-debug.apk`.

## Installation et activation

1. Installer l'APK sur l'appareil ou l'émulateur.
2. Ouvrir l'application Nyavo Keyboard.
3. Appuyer sur "Ouvrir les paramètres clavier".
4. Activer "Nyavo Keyboard" dans la liste des claviers du système.
5. Sélectionner Nyavo Keyboard via le sélecteur de méthode de saisie.

## CI/CD

Le workflow GitHub Actions compile automatiquement l'APK debug à chaque push sur `main`.
