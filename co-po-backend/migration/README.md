# Adopting an existing desktop-app database

This is for anyone pointing the web backend at a MySQL database that the
CO_PO_Assessment desktop app has already been using for real - not a blank database
the backend will create its own schema in from scratch. If you're starting fresh,
none of this applies: just start the backend and Flyway sets everything up on its own.

## Why this is needed at all

The backend manages its schema with Flyway (`src/main/resources/db/migration`), which
assumes it's either creating a database from nothing or has applied every one of its
own migrations to get a database to its current state. An existing desktop database is
neither - it was built from `CO_PO_Assessment/src/main/resources/schema.sql` instead,
by a completely different tool that Flyway has never seen run.

`application.yml` handles this with `flyway.baseline-on-migrate` and
`baseline-version: 1`, which tells Flyway "treat V1 as already applied, don't try to
run it for real, just start from V2." That works because V1 mirrors what
`schema.sql` already creates almost exactly. It is not a byte-for-byte match though,
and the gaps are exactly what this folder exists to handle.

## The three known schema deltas

1. **`AssessmentQuestion.po_id`** - V1 adds this column; `schema.sql` doesn't have it.
   Since V1 is skipped on an adopted database, an adopted database's
   `AssessmentQuestion` table simply won't have this column. This is harmless and
   needs no action: the backend's JPA entity (`AssessmentQuestion.java`) never mapped
   this column to begin with, so nothing reads or writes it either way. PO mapping for
   questions lives entirely in `AssessmentQuestion_PO`, which both `schema.sql` and V1
   create identically.

2. **`Course_CO` / `Course_PO` primary keys** - `schema.sql` creates both tables with
   no primary key or uniqueness constraint at all, so the desktop app could insert the
   same (course, CO) or (course, PO) mapping more than once with nothing to stop it.
   V2 adds `PRIMARY KEY (course_code, programme, co_id)` / `(..., po_id)` and, unlike
   V1, is **not** skipped by the baseline - it runs for real against an adopted
   database. If any duplicates exist, this `ALTER TABLE` fails and the backend won't
   start. See the preflight check below.

3. **`Thresholds` primary key** - same situation as above: `schema.sql` creates this
   table with no primary key on `type`, so it could (in principle) end up with more
   than one row for the same threshold type. V1 adds `PRIMARY KEY (type)` directly in
   its `CREATE TABLE`, but since V1 is skipped on an adopted database, that primary key
   never gets added. V4 (`V4__backfill_thresholds_primary_key.sql`) adds it back as a
   genuinely idempotent `ALTER TABLE`, safe to run whether V1 actually ran (fresh
   install, primary key already there, no-op) or was skipped (adopted database,
   primary key genuinely missing, gets added). It has the same "fails if duplicates
   exist" risk as V2, covered by the same preflight check.

## Adoption procedure

1. **Point the backend at the existing database.** Set `DB_URL`, `DB_USERNAME`, and
   `DB_PASSWORD` to match wherever that database actually lives - these have no
   built-in fallback for the password on purpose (see `application.yml`), so this step
   can't be skipped or defaulted around. `DB_URL` should look like
   `jdbc:mysql://<host>:<port>/<database-name>`, matching whatever the desktop app's
   own `dbcreds.properties` or `DB_URL`/`DB_USERNAME`/`DB_PASSWORD` environment
   variables already point at, since `DBconfig.java` reads the exact same variable
   names the backend does.

2. **Back up the database first.** This is a one-way schema change. A plain
   `mysqldump` of the whole database before doing anything else costs a few minutes
   and means every step below is safely reversible if something goes wrong.

3. **Run `preflight-check.sql`** against that database (e.g.
   `mysql -u <user> -p <database> < migration/preflight-check.sql`, or paste it into
   whatever MySQL client you use). Every query in it should return zero rows. If any
   query returns rows, it's reporting a duplicate group in `Course_CO`, `Course_PO`,
   or `Thresholds` - decide which row is authoritative and delete the others by hand
   before moving on. Skipping this step doesn't corrupt anything; it just means you'll
   find out about the same problem later, as a failed backend startup instead of a
   clean list of exactly what to fix.

4. **Start the backend once** with those environment variables set. Flyway runs on
   startup: V1 is recorded as baseline (skipped), then V2, V3, and V4 run for real. If
   the preflight check was clean, this succeeds and the database now has every
   constraint the backend expects. Check the startup log for `Successfully applied
   3 migrations` (V2, V3, V4) as confirmation.

5. **From here on, treat it like any other backend-managed database.** No more manual
   schema changes - future changes come from new Flyway migrations the normal way.

## A note on this specific project's dev environment

While building this migration tooling, it turned out the desktop app and this web
backend already point at the identical local database by default in this checkout -
same `DB_URL`/`DB_USERNAME`/`DB_PASSWORD` variable names in both `DBconfig.java` (used
by the desktop app) and `application.yml` (used here), same default local connection.
That's specific to this dev setup and isn't something to rely on in general - a real
deployment adopting a real production desktop database should still follow the full
procedure above, including the backup step, rather than assuming anything is shared
automatically.
