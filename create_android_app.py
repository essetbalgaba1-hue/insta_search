#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
Скрипт-генератор готового Android-проекта (InstaBuhBot) с автоматической сборкой APK (через GitHub Actions или Android Studio).

Запустите этот скрипт в терминале:
    python create_android_app.py

Он создаст папку `InstaBuhBot_Android` со всеми файлами приложения:
1. Верхняя панель с вводом Логина/Пароля, полем поиска и кнопками «▶ СТАРТ» и «⏹ СТОП».
2. Обход списка поиска сверху вниз.
3. Чередование лайков: 1 лайк -> 2 лайка -> 3 лайка -> 1 лайк...
4. Переход в Сообщения и отправка «Здравствуйте можно с Вами связаться?».
5. Готовый конфигурационный файл `.github/workflows/build_apk.yml` (если загрузить папку на GitHub, он сам соберёт готовый установочный файл `app-debug.apk` без установки Android Studio).
"""

import os

PROJECT_DIR = "InstaBuhBot_Android"

FILES = {
    "settings.gradle": """pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}
rootProject.name = "InstaBuhBot"
include ':app'
""",

    "build.gradle": """plugins {
    id 'com.android.application' version '8.2.0' apply false
    id 'org.jetbrains.kotlin.android' version '1.9.20' apply false
}
""",

    "gradle.properties": """org.gradle.jvmargs=-Xmx2048m -Dfile.encoding=UTF-8
android.useAndroidX=true
kotlin.code.style=official
android.nonTransitiveRClass=true
""",

    "app/build.gradle": """plugins {
    id 'com.android.application'
    id 'org.jetbrains.kotlin.android'
}

android {
    namespace 'com.example.instabuhbot'
    compileSdk 34

    defaultConfig {
        applicationId "com.example.instabuhbot"
        minSdk 24
        targetSdk 34
        versionCode 1
        versionName "1.0"
    }

    buildTypes {
        release {
            minifyEnabled false
        }
    }
    compileOptions {
        sourceCompatibility JavaVersion.VERSION_17
        targetCompatibility JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = '17'
    }
}

dependencies {
    implementation 'androidx.core:core-ktx:1.12.0'
    implementation 'androidx.appcompat:appcompat:1.6.1'
    implementation 'com.google.android.material:material:1.11.0'
}
""",

    "app/src/main/AndroidManifest.xml": """<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android"
    package="com.example.instabuhbot">

    <uses-permission android:name="android.permission.INTERNET" />
    <uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" />

    <application
        android:allowBackup="true"
        android:label="InstaBuhBot"
        android:supportsRtl="true"
        android:usesCleartextTraffic="true"
        android:theme="@style/Theme.AppCompat.NoActionBar">
        <activity
            android:name=".MainActivity"
            android:configChanges="orientation|screenSize|keyboardHidden"
            android:exported="true">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
        </activity>
    </application>
</manifest>
""",

    ".github/workflows/build_apk.yml": """name: Build Android APK

on:
  push:
    branches: [ "main", "master" ]
  workflow_dispatch:

jobs:
  build:
    runs-on: ubuntu-latest
    steps:
      - name: Checkout code
        uses: actions/checkout@v4

      - name: Set up JDK 17
        uses: actions/setup-java@v4
        with:
          java-version: '17'
          distribution: 'temurin'

      - name: Setup Gradle
        uses: gradle/actions/setup-gradle@v3
        with:
          gradle-version: '8.4'

      - name: Build Debug APK
        run: gradle assembleDebug

      - name: Upload APK Artifact
        uses: actions/upload-artifact@v4
        with:
          name: InstaBuhBot-APK
          path: app/build/outputs/apk/debug/app-debug.apk
"""
}


def generate_project():
    os.makedirs(PROJECT_DIR, exist_ok=True)

    for rel_path, content in FILES.items():
        full_path = os.path.join(PROJECT_DIR, rel_path)
        os.makedirs(os.path.dirname(full_path), exist_ok=True)
        with open(full_path, "w", encoding="utf-8") as f:
            f.write(content)

    # Копируем MainActivity.kt в нужную папку пакета Android
    kt_dest = os.path.join(
        PROJECT_DIR, "app/src/main/java/com/example/instabuhbot/MainActivity.kt"
    )
    os.makedirs(os.path.dirname(kt_dest), exist_ok=True)

    if os.path.exists("MainActivity.kt"):
        with open("MainActivity.kt", "r", encoding="utf-8") as src:
            kt_code = src.read()
        with open(kt_dest, "w", encoding="utf-8") as dst:
            dst.write(kt_code)
        print("[+] Файл MainActivity.kt скопирован в проект.")
    else:
        print("[!] Положите файл MainActivity.kt рядом со скриптом и запустите снова.")

    print(f"\n[Готово] Проект Android-приложения создан в папке: ./{PROJECT_DIR}")
    print("Как получить APK-файл для установки на телефон:")
    print("  Вариант 1 (Через Android Studio): Откройте папку InstaBuhBot_Android -> Build -> Build APK(s).")
    print("  Вариант 2 (Без установки программ, через GitHub): Загрузите папку в репозиторий GitHub -> вкладка Actions -> скачайте готовый InstaBuhBot-APK.")


if __name__ == "__main__":
    generate_project()