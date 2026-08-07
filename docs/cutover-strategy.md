# Desktop-to-web cutover strategy

This is a plan, not an executed action - nothing in this document changes any user's
access, moves any data, or schedules anything by itself. It exists so the actual
go/no-go decisions at each stage are made deliberately, by the people who own that
call, instead of by default.

## The constraint that shapes everything here

The web app has never been run against a live database in this environment - only
compiled and unit-tested with mocked repositories. Every stage below assumes **Stage
0 happens first and passes** before any real user or real data touches the web app.
This isn't a formality; skipping it is the one mistake that would make every later
stage untrustworthy.

The good news, discovered while building the migration tooling
(`co-po-backend/migration/README.md`): the desktop app's own database code
(`CoursesDatabaseHelper.assignCOsToCourse` / `assignPOsToCourse`, and
`DatabaseService.updateThresholds`) already catches duplicate-key exceptions
defensively on every insert into `Course_CO`, `Course_PO`, and `Thresholds` - the
exact three tables the new schema adds primary keys to. That means the desktop app
can keep running completely unmodified against the migrated schema. Nothing about
this plan requires freezing or modifying the desktop app up front, which is what
makes a gradual, reversible cutover possible instead of a forced big-bang switch.

## Why not a big-bang switch

Two systems that can both write to the same course's data at the same time, computing
attainment slightly differently, is worse than either system alone - a faculty member
seeing two different attainment percentages for the same course depending on which
app last touched it will trust neither. This plan's central rule is: **for any single
course assignment, only one app is ever the write-of-record at a time.** Reads (viewing
reports, comparing numbers) can safely happen from both sides at once; writes can't.

## Stage 0 - Verify against a real database (blocking prerequisite)

- Take a copy of a real desktop-created database (or the closest available
  equivalent), run the adoption procedure in `co-po-backend/migration/README.md`
  against the copy, and confirm the backend starts cleanly.
- Manually exercise the core flows end to end against that copy: log in as admin and
  faculty, view existing courses/assignments, enter a few marks, generate each report
  type (CO, PO, Course, Summary, Detailed Marks, Consolidated Marks), run a bulk
  import. This is the first time any of this code will have touched real rows instead
  of mocks.
- Fix whatever that surfaces before moving on. Nothing below is meaningful until this
  passes.

## Stage 1 - Shadow / read-only pilot (suggest 1-2 weeks)

- A small number of willing faculty (or just the admin) use the web app in
  **read-only** capacity - viewing existing data, generating reports - against the
  real database, while the desktop app remains the only place anyone actually enters
  data.
- For a handful of courses, generate the same report from both apps and compare the
  numbers directly. They should match exactly, with two known, deliberate exceptions
  worth calling out to whoever's doing the comparing so they're not mistaken for bugs:
  - **Majority-batch tie-breaks** in Summary Report: the desktop app picks a batch
    non-deterministically (iteration-order-dependent) when there's a tie; the web app
    always picks the lowest batch number. A tied case can legitimately show a
    different batch between the two apps.
  - **Summary Report's "Assessment Requirements" vs "Attainment Status" sections**:
    the desktop app shows these as two separately-worded sections that (on reading
    the source) compute the identical thing with inconsistent failure modes; the web
    app consolidates them into one. Not a numeric mismatch, just a layout one.
- Anything else that doesn't match is a real discrepancy worth chasing down before
  Stage 2, not a known difference.

## Stage 2 - Function-by-function write cutover, one department/cohort at a time

- Move one program or department's data entry onto the web app while every other
  department keeps using desktop, rather than switching every user simultaneously.
  Track which cohorts have moved in a simple shared list (a spreadsheet is fine) so
  it's always unambiguous which app is the write-of-record for a given course
  assignment.
- Prefer moving a department at a **natural boundary** - the start of a new
  assessment cycle for that department, not mid-way through one a faculty member has
  already started entering marks for in desktop.
- Before moving a department, give its faculty a short heads-up covering: where to
  log in, that AI feedback summarization (Phase 7) is a new capability the desktop
  app never had, and the two known display differences from Stage 1 above so a
  tie-break edge case doesn't look like data loss.
- Watch for support requests / confusion in the first cycle for each newly-moved
  department before moving the next one.

## Stage 3 - Full cutover

- Once every department has completed at least one full grading cycle successfully
  on the web app, stop directing any users to the desktop app.
- Keep the desktop app installed and a database backup retained for a defined
  rollback window - suggest one full semester - but treat it as off, not as a
  parallel option, past this point. Decommissioning steps live separately in
  `docs/desktop-decommission-checklist.md`.

## Rollback

Because the schema changes are additive (new primary keys, one new table for report
drafts) and confirmed safe for the desktop app to run against unmodified, rolling
back at any point before Stage 3 is just: stop pointing that department's faculty at
the web app, have them resume using desktop. No schema reversal needed. This is the
main practical benefit of the additive-migration approach Phase 11.1 took over a
one-way transformation script.

## Open questions for whoever owns this decision

This plan intentionally doesn't answer these - they're calls for the people running
the actual institution's process, not something to resolve unilaterally:

- Who signs off on moving each stage forward - a single owner, or per-department
  approval?
- Which department goes first in Stage 2? (A smaller or more web-comfortable
  department as a proving ground is one reasonable default, but it's a real choice.)
- What's the actual rollback window in Stage 3 - does one semester match this
  institution's accreditation review cycle, or should it be longer?
