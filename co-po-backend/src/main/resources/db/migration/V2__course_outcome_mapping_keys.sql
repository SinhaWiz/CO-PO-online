-- V1 created Course_CO/Course_PO exactly as the legacy schema defines them: plain
-- columns with no primary key or uniqueness constraint at all, so the same
-- (course, CO) pair could be inserted any number of times. Now that the backend is
-- about to manage these as real JPA entities with a composite key, give them one.
-- Safe on a fresh database (both tables are still empty at this point in the
-- migration history for anyone starting clean).
ALTER TABLE Course_CO ADD PRIMARY KEY (course_code, programme, co_id);
ALTER TABLE Course_PO ADD PRIMARY KEY (course_code, programme, po_id);
