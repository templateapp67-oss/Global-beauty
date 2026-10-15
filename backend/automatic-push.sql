-- No existing events are backfilled. Only committed future business changes enqueue.
create schema if not exists nexora_push_private;
revoke all on schema nexora_push_private from public,anon,authenticated;
create table nexora_push_private.links (source_user_id uuid primary key references auth.users(id) on delete cascade, site_user_id text not null, linked_at timestamptz not null default now());
create table nexora_push_private.tickets (code_hash text primary key,user_id uuid not null references auth.users(id) on delete cascade,expires_at timestamptz not null);
create table nexora_push_private.outbox (
 id uuid primary key default gen_random_uuid(),event_key text not null unique,kind text not null check(kind in ('booking_created','booking_confirmed','reward_updated','commission_updated')),
 source_id uuid not null,recipient_id uuid not null references auth.users(id) on delete cascade,status_label text not null,
 state text not null default 'pending' check(state in ('pending','processing','retry','accepted','no_subscription','expired','failed')),
 attempts integer not null default 0,lease_id uuid,lease_until timestamptz,next_at timestamptz not null default now(),created_at timestamptz not null default now(),finished_at timestamptz
);
create index nexora_push_pending on nexora_push_private.outbox(state,next_at);
alter table nexora_push_private.links enable row level security;
alter table nexora_push_private.tickets enable row level security;
alter table nexora_push_private.outbox enable row level security;
revoke all on all tables in schema nexora_push_private from public,anon,authenticated;
create function nexora_push_private.authorize(p_secret text) returns void language plpgsql security definer set search_path='' as $$
declare expected text;
begin
 select decrypted_secret into expected from vault.decrypted_secrets where name='nexora_business_push_bridge';
 if expected is null or p_secret is null or extensions.digest(p_secret,'sha256')<>extensions.digest(expected,'sha256') then raise exception 'Unauthorized' using errcode='42501';end if;
end $$;
create function nexora_push_private.create_link() returns text language plpgsql security definer set search_path='' as $$
declare actor uuid:=auth.uid();code text;
begin
 if actor is null then raise exception 'Sign in first' using errcode='42501';end if;
 delete from nexora_push_private.tickets where expires_at<now() or user_id=actor;
 code:=encode(extensions.gen_random_bytes(32),'hex');
 insert into nexora_push_private.tickets values(encode(extensions.digest(code,'sha256'),'hex'),actor,now()+interval '5 minutes');return code;
end $$;
create function public.nexora_create_notification_link() returns text language sql security invoker set search_path='' as $$ select nexora_push_private.create_link() $$;
grant usage on schema nexora_push_private to authenticated;
grant execute on function nexora_push_private.create_link() to authenticated;
revoke all on function public.nexora_create_notification_link() from public,anon;
grant execute on function public.nexora_create_notification_link() to authenticated;
create function nexora_push_private.bridge(p_secret text,p_action text,p_data jsonb default '{}'::jsonb) returns jsonb language plpgsql security definer set search_path='' as $$
declare actor uuid;item record;result jsonb:='[]'::jsonb;lease uuid:=gen_random_uuid();out_state text;
begin
 perform nexora_push_private.authorize(p_secret);
 if p_action='redeem' then
  if coalesce(p_data->>'code','')!~'^[a-f0-9]{64}$' or length(coalesce(p_data->>'siteUser','')) not between 1 and 255 then raise exception 'Invalid link';end if;
  delete from nexora_push_private.tickets where code_hash=encode(extensions.digest(p_data->>'code','sha256'),'hex') and expires_at>now() returning user_id into actor;
  if actor is null then raise exception 'Connection link expired or already used';end if;
  insert into nexora_push_private.links(source_user_id,site_user_id) values(actor,p_data->>'siteUser') on conflict(source_user_id) do update set site_user_id=excluded.site_user_id,linked_at=now();
  return jsonb_build_object('connected',true);
 elsif p_action='connections' then
  return jsonb_build_object('count',(select count(*) from nexora_push_private.links where site_user_id=p_data->>'siteUser'));
 elsif p_action='disconnect' then
  delete from nexora_push_private.links where site_user_id=p_data->>'siteUser';return jsonb_build_object('disconnected',true);
 elsif p_action='claim' then
  update nexora_push_private.outbox set state='expired',finished_at=now() where state in ('pending','retry','processing') and (created_at<now()-interval '24 hours' or attempts>=8 and (lease_until is null or lease_until<now()));
  for item in select o.* from nexora_push_private.outbox o where o.state in ('pending','retry','processing') and o.next_at<=now() and (o.lease_until is null or o.lease_until<now()) order by o.created_at limit 5 for update skip locked loop
   update nexora_push_private.outbox set state='processing',attempts=attempts+1,lease_id=lease,lease_until=now()+interval '5 minutes' where id=item.id;
   result:=result||jsonb_build_array(jsonb_build_object('id',item.id,'kind',item.kind,'status',item.status_label,'siteUser',(select site_user_id from nexora_push_private.links where source_user_id=item.recipient_id),'lease',lease));
  end loop;
  delete from nexora_push_private.outbox where finished_at<now()-interval '30 days';
  return result;
 elsif p_action='ack' then
  out_state:=p_data->>'state';if out_state not in ('accepted','no_subscription','retry','failed') then raise exception 'Invalid result';end if;
  update nexora_push_private.outbox set state=out_state,lease_until=null,next_at=now()+interval '2 minutes',finished_at=case when out_state='retry' then null else now() end where id=(p_data->>'id')::uuid and lease_id=(p_data->>'lease')::uuid and state='processing';
  return jsonb_build_object('acknowledged',found);
 end if;
 raise exception 'Invalid bridge action';
end $$;
create function public.nexora_notification_bridge(p_secret text,p_action text,p_data jsonb default '{}'::jsonb) returns jsonb language sql security invoker set search_path='' as $$ select nexora_push_private.bridge(p_secret,p_action,p_data) $$;
-- Capability-authenticated bridge only: no data access without the secret.
grant usage on schema nexora_push_private to anon;
revoke all on function nexora_push_private.authorize(text),nexora_push_private.bridge(text,text,jsonb) from public,anon,authenticated;
grant execute on function nexora_push_private.bridge(text,text,jsonb) to anon;
revoke all on function public.nexora_notification_bridge(text,text,jsonb) from public,authenticated;
grant execute on function public.nexora_notification_bridge(text,text,jsonb) to anon;
create function nexora_push_private.enqueue() returns trigger language plpgsql security definer set search_path='' as $$
begin
 if tg_table_name='studioflow_bookings' then
  if tg_op='INSERT' then insert into nexora_push_private.outbox(event_key,kind,source_id,recipient_id,status_label) values('booking_created:'||new.id,'booking_created',new.id,new.owner_id,new.status) on conflict do nothing;end if;
  if new.status='confirmed' and (tg_op='INSERT' or old.status is distinct from new.status) then insert into nexora_push_private.outbox(event_key,kind,source_id,recipient_id,status_label) values('booking_confirmed:'||new.id,'booking_confirmed',new.id,new.customer_id,'confirmed') on conflict do nothing;end if;
 elsif tg_table_name='growth_partner_reward_claims' then
  if (tg_op='INSERT' and new.status<>'not_eligible') or (tg_op='UPDATE' and old.status is distinct from new.status) then insert into nexora_push_private.outbox(event_key,kind,source_id,recipient_id,status_label) values('reward:'||new.id||':'||new.status,'reward_updated',new.id,new.partner_user_id,new.status) on conflict do nothing;end if;
 elsif tg_table_name='growth_partner_commissions' then
  if tg_op='INSERT' or old.status is distinct from new.status then insert into nexora_push_private.outbox(event_key,kind,source_id,recipient_id,status_label) values('commission:'||new.id||':'||new.status,'commission_updated',new.id,new.partner_user_id,new.status) on conflict do nothing;end if;
 end if;
 return new;
end $$;
revoke all on function nexora_push_private.enqueue() from public,anon,authenticated;
create trigger nexora_push_booking after insert or update of status on public.studioflow_bookings for each row execute function nexora_push_private.enqueue();
create trigger nexora_push_reward after insert or update of status on public.growth_partner_reward_claims for each row execute function nexora_push_private.enqueue();
create trigger nexora_push_commission after insert or update of status on public.growth_partner_commissions for each row execute function nexora_push_private.enqueue();
-- Revoke PostgreSQL's default PUBLIC execute on every private helper.
revoke all on function nexora_push_private.create_link() from public,anon;
