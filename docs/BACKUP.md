# Backup and restore

BirrWise keeps all its data in a database on the phone. A backup is a single file that holds a copy of that data. It is the only way to get the data back after the app is uninstalled or the phone is lost.

## What a backup contains

Accounts, transactions, categories, people, budgets, category rules, savings goals, and import records, exactly as stored, plus the date and the app version. Money is stored as whole cents, so nothing is rounded.

## How it works

- **Where the file goes:** the person picks the place with the system file picker, so the app needs no storage permission. BirrWise never uploads a backup anywhere.
- **Protection:** by default a backup is protected with a passphrase the person chooses (at least 8 characters). The key is derived from the passphrase with PBKDF2 (HMAC-SHA256, 210,000 rounds, random salt) and the data is sealed with AES-256-GCM, which also detects any change to the file. The passphrase is never stored or logged, and it cannot be recovered. A backup without a passphrase is possible, with a warning, and anyone who gets the file can read it.
- **Checked after saving:** the file is read back and compared with what was written before the screen says it worked. A backup that was not written completely is never reported as done.
- **Last backup time:** kept in the app's private settings and shown on the Backup screen.

## Restoring

1. The person chooses a file. If it is protected, the passphrase is asked for. A wrong passphrase, or a file that was changed after it was made, is refused.
2. The file is checked without changing anything: it must be a BirrWise backup, from this or an older version of the app, with unique ids and no row pointing at a missing row.
3. A preview shows what the backup holds and what is in the app now. Nothing changes until the person confirms.
4. The restore deletes and rewrites every table in one database transaction. If anything fails, everything stays as it was.

Restoring replaces all data in the app. It does not merge.

## File format

A plain backup is UTF-8 JSON with `"format": "birrwise-backup"` and a `formatVersion`. A protected backup starts with the bytes `BWBK`, a version byte, the salt, and the nonce, followed by the sealed JSON. When the data model changes, the format version goes up, and newer versions of the app keep reading older backups. An older app refuses a backup from a newer one.

## Limits

- Backups are made by hand. There is no automatic or cloud backup yet.
- The Home screen does not yet remind the person to back up.
- Keep backup files away from the phone, for example on another device or a memory card.
