## Publishing to Maven (Local + Maven Central via Sonatype)

This project publishes the Android library as:

- **groupId**: `ai.synheart`
- **artifactId**: `synheart-behavior`
- **version**: from `sdkVersion` in `build.gradle`

### Prerequisites

- **JDK**: Use **Java 21** to run Gradle for this repo.
  - This repo uses the Gradle wrapper on the Gradle 8.x line; running with Java 25 will fail with `Unsupported class file major version 69`.
  - On macOS:

```bash
export JAVA_HOME=$(/usr/libexec/java_home -v 21)
export PATH="$JAVA_HOME/bin:$PATH"
java -version
```

- **Sonatype Central token**: username + password for publishing.
- **PGP signing key**: Maven Central requires signed artifacts.

### Step 0: Bump the version (release)

Edit `build.gradle`:

- Update `def sdkVersion = "X.Y.Z"`

Also update `README.md` and `CHANGELOG.md` as needed.

### Step 1: Test publish locally (no signing required)

```bash
./gradlew --stop
./gradlew clean publishToMavenLocal --no-daemon
```

Artifacts will be available under:

- `~/.m2/repository/ai/synheart/synheart-behavior/<version>/`

### Step 2: Provide Sonatype + GPG credentials (do NOT commit secrets)

Gradle will read credentials from either Gradle project properties or environment variables.
The most reliable approach is using `ORG_GRADLE_PROJECT_*` environment variables (Gradle maps these to project properties automatically).

```bash
export ORG_GRADLE_PROJECT_sonatypeUsername="***"
export ORG_GRADLE_PROJECT_sonatypePassword="***"

# Preferred: base64-encoded ASCII-armored private key (single line)
export ORG_GRADLE_PROJECT_GPG_PRIVATE_KEY_BASE64="***"
export ORG_GRADLE_PROJECT_GPG_PASSPHRASE="***"
```

#### Creating `GPG_PRIVATE_KEY_BASE64`

```bash
KEY_ID="YOUR_KEY_ID"
export ORG_GRADLE_PROJECT_GPG_PRIVATE_KEY_BASE64="$(
  gpg --armor --export-secret-keys "$KEY_ID" | base64 | tr -d '\n\r'
)"
```

### Step 3: Publish to Sonatype (staging)

```bash
./gradlew --stop
./gradlew publishToSonatype --no-daemon --stacktrace
```

### Step 4: Close the staging repository

```bash
./gradlew closeSonatypeStagingRepository --no-daemon --stacktrace
```

Or publish + close in one command:

```bash
./gradlew publishToSonatype closeSonatypeStagingRepository --no-daemon --stacktrace
```

### Step 5: Release the staging repository

```bash
./gradlew releaseSonatypeStagingRepository --no-daemon --stacktrace
```

### If you need the staging repository id

```bash
./gradlew findSonatypeStagingRepository --no-daemon
```

Then:

```bash
./gradlew releaseSonatypeStagingRepository \
  --staging-repository-id "YOUR_STAGING_REPO_ID" \
  --no-daemon --stacktrace
```

