-- Run once after migration.sql. Only the new schema's expired records are affected.
create extension if not exists pg_cron;
select cron.schedule('fasting-partner-week-retention','17 * * * *','select fasting_partners.purge_expired()');
