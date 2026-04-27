-- Postgres bootstrap. The 'leaderboard' role and database are created by the
-- official postgres image based on POSTGRES_USER / POSTGRES_DB env vars.
-- Application schema is owned by Flyway (see V1__init.sql in leaderboard-app).
SELECT 'leaderboard postgres ready' AS status;
