-- Read-only structural audit after schema.sql has been applied to staging.
select c.relname, c.relrowsecurity
from pg_class c join pg_namespace n on n.oid=c.relnamespace
where n.nspname='public' and c.relname in ('nexora_mobile_profiles','nexora_mobile_devices');
select schemaname,tablename,policyname,roles,cmd,qual,with_check
from pg_policies where tablename='nexora_mobile_profiles' or policyname like 'mobile_files_%';
select id,public,file_size_limit,allowed_mime_types from storage.buckets where id in ('nexora-mobile-photos','nexora-mobile-documents');
select grantee,table_name,privilege_type from information_schema.role_table_grants where table_name in ('nexora_mobile_profiles','nexora_mobile_devices') order by table_name,grantee;
-- Expect RLS=true on both, buckets private, 5 MB cap, no anon grants,
-- authenticated owner-scoped profile SELECT/INSERT/UPDATE only,
-- no authenticated access to devices, no mobile Storage UPDATE policy.
