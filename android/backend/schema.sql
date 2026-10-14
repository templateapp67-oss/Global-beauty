-- Companion-only tables. Apply to the explicitly selected Supabase project.
-- SQL setup file, not a fabricated entry in a linked project's migration history.
begin;
create table if not exists public.nexora_mobile_profiles (
  user_id uuid primary key references auth.users(id) on delete cascade,
  display_name text not null default '' check (char_length(display_name) <= 80),
  photo_path text not null default '',
  constraint own_photo_path check (photo_path='' or photo_path like user_id::text || '/%')
);
alter table public.nexora_mobile_profiles enable row level security;
revoke all on public.nexora_mobile_profiles from anon, authenticated;
grant select, insert, update on public.nexora_mobile_profiles to authenticated;
create policy mobile_profile_select on public.nexora_mobile_profiles for select to authenticated using ((select auth.uid())=user_id);
create policy mobile_profile_insert on public.nexora_mobile_profiles for insert to authenticated with check ((select auth.uid())=user_id);
create policy mobile_profile_update on public.nexora_mobile_profiles for update to authenticated using ((select auth.uid())=user_id) with check ((select auth.uid())=user_id);

-- Devices are managed exclusively by a JWT-verifying Edge Function.
create table if not exists public.nexora_mobile_devices (
 user_id uuid not null references auth.users(id) on delete cascade,
 installation_id uuid not null,
 token text not null unique check(char_length(token) between 20 and 4096),
 updated_at timestamptz not null default now(),
 primary key(user_id,installation_id)
);
alter table public.nexora_mobile_devices enable row level security;
revoke all on public.nexora_mobile_devices from anon,authenticated;
grant all on public.nexora_mobile_profiles,public.nexora_mobile_devices to service_role;

insert into storage.buckets (id,name,public,file_size_limit,allowed_mime_types) values
 ('nexora-mobile-photos','nexora-mobile-photos',false,5242880,array['image/jpeg','image/png','image/webp']),
 ('nexora-mobile-documents','nexora-mobile-documents',false,5242880,array['application/pdf','image/jpeg','image/png']);

-- A live profile must still exist: sessions without a companion profile cannot
-- access these buckets. RLS applies to this subquery.
create policy mobile_files_select on storage.objects for select to authenticated using (
 bucket_id in ('nexora-mobile-photos','nexora-mobile-documents') and (storage.foldername(name))[1]=(select auth.uid())::text
 and exists(select 1 from public.nexora_mobile_profiles p where p.user_id=(select auth.uid()))
);
create policy mobile_files_insert on storage.objects for insert to authenticated with check (
 bucket_id in ('nexora-mobile-photos','nexora-mobile-documents') and (storage.foldername(name))[1]=(select auth.uid())::text
 and exists(select 1 from public.nexora_mobile_profiles p where p.user_id=(select auth.uid()))
);
create policy mobile_files_delete on storage.objects for delete to authenticated using (
 bucket_id in ('nexora-mobile-photos','nexora-mobile-documents') and (storage.foldername(name))[1]=(select auth.uid())::text
 and exists(select 1 from public.nexora_mobile_profiles p where p.user_id=(select auth.uid()))
);
-- Objects are immutable UUID names. Replacement upload creates a fresh object;
-- no UPDATE/upsert Storage policy is needed or granted.
commit;
