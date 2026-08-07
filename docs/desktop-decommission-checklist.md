# Desktop app decommission checklist

This is a checklist to work through deliberately, item by item, with explicit
go-ahead at each step - not a script to run. Nothing here gets executed
automatically. Several of these steps are one-way (deleting files, revoking
credentials) and this document exists specifically so none of them happen as a
side effect of "cleaning up" rather than a decision someone actually made.

**Precondition:** Stage 3 of `docs/cutover-strategy.md` is complete - every
department has finished at least one full grading cycle on the web app, and the
rollback window that stage defines has actually elapsed. Nothing below should start
before that's true.

## 1. Final backup

- Take a full `mysqldump` of the production database after the last day anyone used
  the desktop app for real, labeled clearly with the date, and store it somewhere
  outside the machines that get decommissioned in step 5. This is the permanent
  record of "the database exactly as the desktop app left it," independent of
  whatever the web app does with it afterward.

## 2. Reconcile report file storage

- `ReportStorageService` falls back to preferring `../CO_PO_Assessment`'s own report
  folders when `REPORT_STORAGE_DIR` isn't set (see its class comment) - specifically
  so reports from both apps showed up in one place during the migration period. Once
  the `CO_PO_Assessment` folder is archived or removed (step 6), that fallback
  silently stops matching and every report starts landing in a fresh local folder
  instead, with no error or warning.
- Before touching the `CO_PO_Assessment` folder: set `REPORT_STORAGE_DIR` explicitly
  to a permanent path, and copy the existing report files out of the legacy folder
  into it. Otherwise historical reports aren't lost, but they do become hard to find -
  sitting in a folder the running app no longer looks at.

## 3. Rotate database credentials

- `CO_PO_Assessment/dbcreds.properties` has held a real database password in plain
  text on every machine the desktop app was installed on, for as long as that app
  existed - and, per its own contents, a second, separate-looking production
  credential in a commented-out line. Once the desktop app is no longer a legitimate
  reason for that password to exist on end-user machines, rotate it: change the
  database password and update it in whatever secret store the web backend's
  `DB_PASSWORD` now comes from (see `co-po-backend/src/main/resources/application.yml`'s
  header comment - it has no fallback on purpose).
- This is worth doing even though `dbcreds.properties` was never committed to this
  repo (it's untracked, and now explicitly gitignored) - "never in git" doesn't mean
  "never copied anywhere else" across however many machines ran the desktop app.

## 4. Confirm nothing else depends on the desktop app running

- Check for scheduled tasks, cron jobs, or startup scripts on any machine that
  launch the desktop app headlessly or automatically - it's a JavaFX GUI app, so this
  is unlikely, but worth explicitly confirming rather than assuming, especially on
  whatever machine was treated as "the" install if data entry was ever centralized.
- Confirm no other tool or script reads `CO_PO_Assessment`'s files or output folders
  directly (report folders are covered by step 2; this is asking whether anything
  else - a backup job, a separate reporting tool - points at that folder too).

## 5. Communicate the retirement date

- Tell every faculty member and admin who used the desktop app: the date it stops
  being supported, where the web app lives, and that login uses their existing
  credentials (or however auth was set up for them). Point out the two known display
  differences documented in `docs/cutover-strategy.md` one more time here, since this
  is the point where anyone still mentally cross-checking against desktop numbers
  should stop expecting an exact match on those two specific things.

## 6. Uninstall / archive - the actual one-way steps

Do these last, and only after steps 1-5 are actually done, not just planned:

- Uninstall the desktop app from end-user machines.
- Decide what happens to the `CO_PO_Assessment` source folder in this repository:
  **archiving it (tagging the current commit, or moving it to a separate archive
  repo) is the safer default over deleting it outright** - it's the authoritative
  reference for exactly how the original business logic worked, which is likely to
  matter again the next time someone questions why a number looks the way it does,
  possibly years from now. Deleting it saves comparatively little (it's not shipped,
  not running, not a maintenance burden sitting inertly in version control) against
  a real cost if it turns out to be needed later and isn't there.
- Whichever way that decision goes, it's the kind of repository change that's worth
  doing as its own explicit, confirmed step - not folded into an unrelated commit.
