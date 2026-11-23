-- A chore nobody has triaged is its own state, distinct from one deliberately set to MEDIUM.
-- The CHECK needs no change: it holds unless it evaluates to false, and NULL IN (...) is unknown.
ALTER TABLE task ALTER COLUMN priority DROP NOT NULL;
