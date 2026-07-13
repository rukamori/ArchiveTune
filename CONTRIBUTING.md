# 🛠️ Engineering & Build Guide

This document defines the protocols for setting up the development environment, understanding the underlying technology stack, and compiling **ArchiveTune** from the source.

---

## 🏗️ Operational Readiness

To ensure build stability and environment parity, the following hardware and software configurations are mandatory.

### **Development Environment**

* **IDE:** [Android Studio](https://developer.android.com/studio) **Ladybug (2024.2.1) or newer**.
* **Java Runtime:** **JDK 17** (Amazon Corretto or Azul Zulu recommended for deterministic builds).
* **Android SDK:** API Level 34+ (Upside Down Cake).
* **Version Control:** Git 2.40+.

### **Technical DNA (Skill Requirements)**

The ArchiveTune codebase is built on a modern, reactive architecture. Contributors are expected to have a high level of familiarity with:

* **Kotlin (Advanced):** Proficiency in Coroutines, Flow API, and functional paradigms.
* **Jetpack Compose:** Understanding of State Hoisting, Recomposition optimization, and Material 3 design systems.
* **Gradle (KTS):** Ability to navigate Kotlin DSL build scripts and Version Catalogs (`libs.versions.toml`).
* **Modern Android Architecture:** Deep understanding of MVVM, Repository patterns, and UDF (Unidirectional Data Flow).

---

## 📐 Architectural Manifesto

ArchiveTune follows a strict **Clean Architecture** approach. This separation of concerns ensures that the audio engine remains independent of the UI layer.

1. **UI Layer (Compose):** Handles user interactions and renders state emitted by ViewModels.
2. **Domain Layer:** Contains business logic, Use Cases, and high-level audio processing interfaces.
3. **Data Layer:** Manages the single source of truth—coordinating between the YouTube Music API (Retrofit) and the local encrypted cache (Room).
4. **Service Layer (Media3):** A specialized background layer managing the `MediaSession` and low-latency audio pipelines.

---

## 🚀 Environment Initialization

1. **Clone the Source:**
```bash
git clone https://github.com/rukamori/ArchiveTune.git
cd ArchiveTune

```


2. **Secret Management:**
ArchiveTune uses a modular properties system. If your build requires specific API keys (e.g., Discord Client IDs), define them in your `local.properties`:
```properties
# Path to your Android SDK
sdk.dir=/Users/yourname/Library/Android/sdk

```


3. **Syncing the Core:**
Open the project in Android Studio. The IDE will automatically trigger a Gradle sync. We use **Version Catalogs** to ensure all dependencies (Media3, Hilt, Compose) are locked to tested versions.

---

## 📦 Build Pipelines

Use the Gradle Wrapper to execute verified build scripts.

| Command | Output | Context |
| --- | --- | --- |
| `./gradlew assembleDebug` | `app-debug.apk` | Local testing & feature development. |
| `./gradlew assembleRelease` | `app-release.apk` | Production-ready, R8-optimized build. |
| `./gradlew bundleRelease` | `app-release.aab` | Optimized bundle for distribution. |
| `./gradlew clean` | `N/A` | Flushes build cache to resolve sync issues. |

---

## 🛡️ Code Quality & Static Analysis

Before initiating a Pull Request, every contributor must run the following quality gates:

* **Linting:** `./gradlew lintDebug` (Ensures adherence to Android XML/Compose standards).
* **Formatting:** `./gradlew ktlintCheck` (Ensures consistent Kotlin styling).
* **Logic Verification:** `./gradlew testDebugUnitTest` (Runs the architectural unit tests).

---

## ⚖️ Troubleshooting

> [!IMPORTANT]
> **Heap Memory:** If you experience `GC overhead limit exceeded`, ensure your `gradle.properties` has sufficient memory allocated:
> `org.gradle.jvmargs=-Xmx4g -XX:MaxMetaspaceSize=1g`

> [!WARNING]
> **Compose Compiler:** If the build fails due to a Compose version mismatch, verify that the `kotlinCompilerExtensionVersion` in the build script matches the current Kotlin version.

---

<div align="center">
<sub>ArchiveTune: Engineering audio freedom. Part of the <strong>Koiverse</strong> ecosystem.</sub>
</div>