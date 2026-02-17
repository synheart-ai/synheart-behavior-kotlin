## Publishing to Maven (Maven Local + Maven Central via Sonatype)

This project publishes the Android library as:

- **groupId**: `ai.synheart`
- **artifactId**: `synheart-behavior`
- **version**: from `sdkVersion` in `build.gradle`

Publishing is configured in `build.gradle` using:

- `maven-publish` (publication: `release`)
- `signing` (in-memory PGP key; required for Sonatype publish/close/release)
- `io.github.gradle-nexus.publish-plugin` (Sonatype Central “staging api”)

### Prerequisites (one-time)

- **Sonatype Central account + namespace access** for `ai.synheart`
  - Create a token in Sonatype Central and keep the **username/password** handy.
- **GPG signing key** (Maven Central requires signed artifacts)
  - Create an ASCII-armored keypair.
  - Publish the **public key** to keyservers.
  - Keep the **private key** + passphrase available for Gradle (see below).

### Step 0: Bump the version (release)

Edit `build.gradle`:

- Update `def sdkVersion = "X.Y.Z"`

Optional (recommended): update the version shown in `README.md`.

### Step 1: Test publish locally (no signing required)

This verifies the publication wiring without touching Sonatype:

```bash
./gradlew --stop
./gradlew clean publishToMavenLocal --no-daemon
```

You should then find artifacts under:

- `~/.m2/repository/ai/synheart/synheart-behavior/<version>/`

### Step 2: Provide Sonatype + GPG credentials (do NOT commit secrets)

Gradle will pick these up either as **project properties** or **environment variables**.
The most reliable approach is using `ORG_GRADLE_PROJECT_*` env vars (Gradle automatically maps them to project properties).

Set these (recommended):

```bash
export ORG_GRADLE_PROJECT_sonatypeUsername="***"
export ORG_GRADLE_PROJECT_sonatypePassword="***"

# Preferred: base64-encoded ASCII-armored private key (single line)
export ORG_GRADLE_PROJECT_GPG_PRIVATE_KEY_BASE64="***"
export ORG_GRADLE_PROJECT_GPG_PASSPHRASE="***"
```

Notes:

- The build also supports `SONATYPE_USERNAME` / `SONATYPE_PASSWORD` and `GPG_PRIVATE_KEY` / `GPG_PRIVATE_KEY_BASE64` / `GPG_PASSPHRASE` as plain env vars.
- This repo’s `.env` and `env (2)` are git-ignored. If you use a credentials file, load it into your shell **before** running Gradle:

```bash
set -a
source ./.env
# or: source "./env (2)"
set +a
```

#### Creating `GPG_PRIVATE_KEY_BASE64`

Export your secret key in ASCII armor, base64 it, and strip newlines:

```bash
KEY_ID="YOUR_KEY_ID"
export ORG_GRADLE_PROJECT_GPG_PRIVATE_KEY_BASE64="$(
  gpg --armor --export-secret-keys "$KEY_ID" | base64 | tr -d '\n\r'
)"
```

### Step 3–5: Publish to Maven Central (recommended: all-in-one)

**Recommended:** Run publish, close, and release in a **single** Gradle run. This avoids the “No staging repository with name sonatype created” error that occurs when you run `releaseSonatypeStagingRepository` in a separate run.

```bash
./gradlew --stop
./gradlew publishToSonatype closeSonatypeStagingRepository releaseSonatypeStagingRepository --no-daemon --stacktrace
```

After a successful run, the artifact is released to Maven Central. It may take **10–30 minutes** (sometimes longer) to appear in search and for dependency resolution: https://central.sonatype.com/search?q=ai.synheart

---

**Alternative (step-by-step):** If you prefer to run each step separately:

- **Step 3 – Publish:** uploads to a new staging repository  
  `./gradlew publishToSonatype --no-daemon --stacktrace`
- **Step 4 – Close:** validates and closes the staging repo (must be in the **same** run as publish, or the plugin won’t know the repo id)  
  `./gradlew closeSonatypeStagingRepository --no-daemon --stacktrace`
- **Step 5 – Release:** promotes to Maven Central (must be in the **same** run as publish+close, or you must pass `--staging-repository-id`; see below)

### If you need the staging repository id

If you ran publish + close in one run but then want to run **release** in a **separate** run, you must pass the staging repository id (otherwise you get “No staging repository with name sonatype created”).

**Find the id:** In [Sonatype Central](https://central.sonatype.com/) → **Staging profiles** → open your closed repository; the id looks like `ai.synheart--2822fc6c-5414-4ff9-ba94-4c74a896f634`. Alternatively, run:

```bash
./gradlew findSonatypeStagingRepository --no-daemon
```

**Release with that id** (syntax may vary by plugin version; if one fails, try the other):

```bash
./gradlew releaseSonatypeStagingRepository -PstagingRepositoryId=YOUR_STAGING_REPO_ID --no-daemon --stacktrace
# or: ./gradlew releaseSonatypeStagingRepository --staging-repository-id=YOUR_STAGING_REPO_ID --no-daemon --stacktrace
```

### Publishing snapshots

If `version` ends with `-SNAPSHOT`, the Nexus Publish plugin will publish to the configured snapshots repository.

Example:

- set `sdkVersion` to `0.3.2-SNAPSHOT`
- run:

```bash
./gradlew publishToSonatype --no-daemon --stacktrace
```

### Troubleshooting

- **Signing failures**: ensure both the private key (prefer base64) and passphrase are set. Signing is only required for Sonatype publish/close/release tasks; `publishToMavenLocal` should work without signing.
- **Bad base64 / newlines**: re-generate `GPG_PRIVATE_KEY_BASE64` making sure it’s a single line (the build normalizes whitespace, but keeping it one-line is safest).
- **Credentials not picked up**: prefer `ORG_GRADLE_PROJECT_sonatypeUsername` / `ORG_GRADLE_PROJECT_sonatypePassword` over custom env var names. Ensure the credentials file is sourced in the same shell where you run `./gradlew`.
- **JAVA_HOME is set to an invalid directory**: Gradle needs a valid JDK path. On macOS with Homebrew OpenJDK 17, use e.g. `export JAVA_HOME="/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home"`. The path must end with `Home` (not e.g. `Homenexport`); fix any broken line in `~/.zshrc` or `~/.bash_profile`.

