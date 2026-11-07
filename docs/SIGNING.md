# Signing

Android only installs an update over an installed app if both are signed with the same key. A fresh build machine creates a new debug key every time, so without a fixed key each new build would have to be uninstalled first, which erases the app's data.

## How debug builds are signed

- `app/debug.keystore.enc` is a debug signing key, encrypted with AES-256. It is useless without the passphrase.
- The passphrase is stored only as the GitHub secret `DEBUG_KEYSTORE_PASSPHRASE`. It is not in the repository.
- During a build, the workflow decrypts the key into a temporary folder and passes its path to Gradle through `DEBUG_KEYSTORE_PATH`. `app/build.gradle.kts` uses it for the debug build only.
- If the secret or the file is missing, the build still works and Android's default debug key is used.
- Each run prints the signing certificate fingerprint so you can check that two builds share a key.

## Limits

- This key is for debug builds of this app on your own phone. It must never be used for a release.
- A release will need its own signing key, created and kept outside the repository, with its passphrase in GitHub secrets. Losing a release key means users can no longer update the app, so it must be backed up.
- To replace the debug key, create a new key, encrypt it the same way, and update the file and the secret. Phones then need one uninstall and reinstall.
