-- Run this against an EXISTING desktop-created database BEFORE ever starting this
-- backend against it. It's only needed when adopting a database that already has
-- real data in it (i.e. it was created and used by the CO_PO_Assessment desktop app)
-- rather than starting the backend against a blank one. See README.md in this same
-- folder for the full adoption procedure this fits into.
--
-- The desktop app's schema.sql never enforced uniqueness on a few columns that this
-- backend's Flyway migrations turn into real PRIMARY KEYs (V2 for Course_CO /
-- Course_PO, V4 for Thresholds). Adding a PRIMARY KEY to a table that already
-- contains duplicate values for it fails immediately and stops the backend from
-- starting at all - so this script checks for that in advance, without changing
-- anything, so any duplicates can be resolved by hand on your own schedule instead of
-- as an emergency during a failed startup.
--
-- Every SELECT below should return zero rows. Any row returned identifies a duplicate
-- group - decide which row is authoritative and delete the rest directly in MySQL
-- before starting the backend for the first time against this database.

-- 1. Duplicate Course_CO mappings (V2 adds PRIMARY KEY (course_code, programme, co_id))
SELECT course_code, programme, co_id, COUNT(*) AS duplicate_count
FROM Course_CO
GROUP BY course_code, programme, co_id
HAVING COUNT(*) > 1;

-- 2. Duplicate Course_PO mappings (V2 adds PRIMARY KEY (course_code, programme, po_id))
SELECT course_code, programme, po_id, COUNT(*) AS duplicate_count
FROM Course_PO
GROUP BY course_code, programme, po_id
HAVING COUNT(*) > 1;

-- 3. Duplicate Thresholds types (V4 adds PRIMARY KEY (type))
SELECT type, COUNT(*) AS duplicate_count
FROM Thresholds
GROUP BY type
HAVING COUNT(*) > 1;
