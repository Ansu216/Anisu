# Signing keystore

`anisu.jks` is the signing key used for **both** the debug and the release APK, so
that every APK we publish (nightly and release) has the same signature and installs
as a normal upgrade over the previous one.

| Field | Value |
|---|---|
| File | `keystore/anisu.jks` |
| Format | PKCS12 |
| Key alias | `anisu` |
| Store password | `android` |
| Key password | `android` |
| Certificate | `CN=Anisu, OU=Anisu, O=Anisu` |

## Why is it committed?

So that `./gradlew assembleRelease` and the GitHub Actions workflows can produce a
**signed** APK with zero setup — no secrets to configure, no keystore to generate.
CI runners get the same key as your local machine, which is what makes the nightly
APK installable as an update.

## Security note

`android` is a well-known password. Because the keystore is in this repository, anyone
with read access can build APKs signed as Anisu. That is fine for a personal / nightly
project, but **do not ship this to the Play Store** — Play requires a private upload key.

### Rotating to secrets (recommended if the repo is/should be private)

1. Base64-encode the keystore and store it as a repository secret `KEYSTORE_BASE64`:

   ```bash
   base64 -w0 keystore/anisu.jks
   ```

2. Add secrets `STORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD`.
3. Decode it in the workflow before the Gradle step:

   ```yaml
   - name: Decode keystore
     run: echo "${{ secrets.KEYSTORE_BASE64 }}" | base64 -d > keystore/anisu.jks
   ```

4. Pass the passwords to Gradle (they already exist as overridable properties):

   ```
   ./gradlew assembleRelease \
     -PANISU_STORE_PASSWORD="${{ secrets.STORE_PASSWORD }}" \
     -PANISU_KEY_ALIAS="${{ secrets.KEY_ALIAS }}" \
     -PANISU_KEY_PASSWORD="${{ secrets.KEY_PASSWORD }}"
   ```

5. Delete `keystore/anisu.jks` from the repository and add `keystore/*.jks` to
   `.gitignore`.

## Verify a built APK

```bash
apksigner verify --print-certs app/build/outputs/apk/release/app-release.apk
```

Expected fingerprint (SHA-256):

```
9E:BE:BC:E3:65:42:33:82:EE:E6:45:74:CD:F8:53:60:28:6A:C0:3D:B7:9D:CB:85:DB:E1:30:50:C7:F1:CC:BA
```
