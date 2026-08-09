-- Rename the admin table to match the JPA entity mapping.
-- This keeps the earlier migrations' checksums stable and lets Hibernate query
-- the canonical lowercase table name on MySQL deployments where table names are
-- case-sensitive.

RENAME TABLE Admin TO admin;
