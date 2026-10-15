-- Additive follow-up: do not rewrite the applied initial schema.
alter table nexora_push_private.outbox drop constraint outbox_kind_check;
alter table nexora_push_private.outbox add constraint outbox_kind_check check(kind in ('booking_created','booking_confirmed','legacy_booking_created','legacy_booking_confirmed','reward_updated','commission_updated'));
alter table nexora_push_private.outbox drop constraint outbox_state_check;
alter table nexora_push_private.outbox add constraint outbox_state_check check(state in ('pending','processing','retry','accepted','no_subscription','expired','failed','superseded'));
create index nexora_push_links_site_user on nexora_push_private.links(site_user_id);
create index nexora_push_tickets_expires on nexora_push_private.tickets(expires_at);
create function nexora_push_private.legacy_booking_state(b jsonb) returns text language sql immutable set search_path='' as $$
 select case when coalesce(nullif(b->>'booking_status',''),b->>'status')='confirmed'
  and coalesce((b->>'advance_paise')::numeric,0)>coalesce((b->>'advance_paid_amount')::numeric,0)*100
  and coalesce(b->>'payment_status','pending') not in ('paid','paid_deposit','paid_advance','success','completed')
 then 'pending_payment' else coalesce(nullif(b->>'booking_status',''),b->>'status') end;
$$;
create function nexora_push_private.enqueue_legacy_booking() returns trigger language plpgsql security definer set search_path='' as $$
declare owner uuid;state text:=nexora_push_private.legacy_booking_state(to_jsonb(new));old_state text;
begin
 if tg_op='UPDATE' then old_state:=nexora_push_private.legacy_booking_state(to_jsonb(old));end if;
 if tg_op='INSERT' then
  for owner in select new.owner_id where new.owner_id is not null union select m.user_id from public.salons s join public.organization_members m on m.organization_id=s.organization_id where new.owner_id is null and s.id=new.salon_id and m.role='owner' and m.status='active' loop
   insert into nexora_push_private.outbox(event_key,kind,source_id,recipient_id,status_label) values('legacy_created:'||new.id||':'||owner,'legacy_booking_created',new.id,owner,coalesce(state,'pending')) on conflict do nothing;
  end loop;
 end if;
 -- user_id has a verified auth.users FK. customer_id, email and phone are NOT identity proofs.
 if new.user_id is not null and state='confirmed' and (tg_op='INSERT' or old_state is distinct from state) then
  insert into nexora_push_private.outbox(event_key,kind,source_id,recipient_id,status_label) values('legacy_confirmed:'||new.id||':'||new.user_id,'legacy_booking_confirmed',new.id,new.user_id,'confirmed') on conflict do nothing;
 end if;
 return new;
end $$;
revoke all on function nexora_push_private.legacy_booking_state(jsonb),nexora_push_private.enqueue_legacy_booking() from public,anon,authenticated;
create trigger nexora_push_legacy_booking after insert or update of status,booking_status,payment_status on public.bookings for each row execute function nexora_push_private.enqueue_legacy_booking();
create function nexora_push_private.event_current(e nexora_push_private.outbox) returns boolean language plpgsql stable security definer set search_path='' as $$
begin
 case e.kind
 when 'booking_created' then return exists(select 1 from public.studioflow_bookings b where b.id=e.source_id and b.owner_id=e.recipient_id and b.status not in ('cancelled'));
 when 'booking_confirmed' then return exists(select 1 from public.studioflow_bookings b where b.id=e.source_id and b.customer_id=e.recipient_id and b.status='confirmed');
 when 'legacy_booking_created' then return exists(select 1 from public.bookings b where b.id=e.source_id and nexora_push_private.legacy_booking_state(to_jsonb(b)) not in ('cancelled','expired','no_show') and (b.owner_id=e.recipient_id or b.owner_id is null and exists(select 1 from public.salons s join public.organization_members m on m.organization_id=s.organization_id where s.id=b.salon_id and m.user_id=e.recipient_id and m.role='owner' and m.status='active')));
 when 'legacy_booking_confirmed' then return exists(select 1 from public.bookings b where b.id=e.source_id and b.user_id=e.recipient_id and nexora_push_private.legacy_booking_state(to_jsonb(b))='confirmed');
 when 'reward_updated' then return exists(select 1 from public.growth_partner_reward_claims r where r.id=e.source_id and r.partner_user_id=e.recipient_id and r.status=e.status_label);
 when 'commission_updated' then return exists(select 1 from public.growth_partner_commissions c where c.id=e.source_id and c.partner_user_id=e.recipient_id and c.status=e.status_label);
 else return false;
 end case;
end $$;
revoke all on function nexora_push_private.event_current(nexora_push_private.outbox) from public,anon,authenticated;
create or replace function nexora_push_private.bridge(p_secret text,p_action text,p_data jsonb default '{}'::jsonb) returns jsonb language plpgsql security definer set search_path='' as $$
declare actor uuid;item nexora_push_private.outbox;result jsonb:='[]'::jsonb;lease uuid:=gen_random_uuid();out_state text;
begin
 perform nexora_push_private.authorize(p_secret);
 if p_action='redeem' then
  if coalesce(p_data->>'code','')!~'^[a-f0-9]{64}$' or length(coalesce(p_data->>'siteUser','')) not between 1 and 255 then raise exception 'Invalid link';end if;
  delete from nexora_push_private.tickets where code_hash=encode(extensions.digest(p_data->>'code','sha256'),'hex') and expires_at>now() returning user_id into actor;
  if actor is null then raise exception 'Connection link expired or already used';end if;
  insert into nexora_push_private.links(source_user_id,site_user_id) values(actor,p_data->>'siteUser') on conflict(source_user_id) do update set site_user_id=excluded.site_user_id,linked_at=case when nexora_push_private.links.site_user_id=excluded.site_user_id then nexora_push_private.links.linked_at else now() end;
  return jsonb_build_object('connected',true);
 elsif p_action='connections' then
  return jsonb_build_object('count',(select count(*) from nexora_push_private.links where site_user_id=p_data->>'siteUser'));
 elsif p_action='disconnect' then
  delete from nexora_push_private.links where site_user_id=p_data->>'siteUser';return jsonb_build_object('disconnected',true);
 elsif p_action='health' then
  return jsonb_build_object('queue',(select coalesce(jsonb_agg(x),'[]') from (select kind,state,count(*) as count from nexora_push_private.outbox group by kind,state)x),'linkedAccounts',(select count(*) from nexora_push_private.links),'jobActive',(select active from cron.job where jobname='nexora-beauty-business-push'),'lastJob',(select jsonb_build_object('status',status,'startedAt',start_time) from cron.job_run_details where jobid=(select jobid from cron.job where jobname='nexora-beauty-business-push') order by start_time desc limit 1),'oldestPendingSeconds',(select coalesce(extract(epoch from now()-min(created_at)),0)::integer from nexora_push_private.outbox where state in ('pending','retry','processing')));
 elsif p_action='claim' then
  update nexora_push_private.outbox set state=case when created_at<now()-interval '24 hours' then 'expired' else 'failed' end,finished_at=now() where state in ('pending','retry','processing') and (created_at<now()-interval '24 hours' or attempts>=8 and (lease_until is null or lease_until<now()));
  for item in select o.* from nexora_push_private.outbox o where o.state in ('pending','retry','processing') and o.next_at<=now() and (o.lease_until is null or o.lease_until<now()) order by o.created_at limit 5 for update skip locked loop
   if not nexora_push_private.event_current(item) then update nexora_push_private.outbox set state='superseded',finished_at=now() where id=item.id;continue;end if;
   update nexora_push_private.outbox set state='processing',attempts=attempts+1,lease_id=lease,lease_until=now()+interval '5 minutes' where id=item.id;
   result:=result||jsonb_build_array(jsonb_build_object('id',item.id,'kind',item.kind,'status',item.status_label,'siteUser',(select site_user_id from nexora_push_private.links where source_user_id=item.recipient_id and linked_at<=item.created_at),'createdAt',floor(extract(epoch from item.created_at)*1000),'lease',lease));
  end loop;
  delete from nexora_push_private.outbox where finished_at<now()-interval '30 days';
  return result;
 elsif p_action='ack' then
  out_state:=p_data->>'state';if out_state not in ('accepted','no_subscription','retry','failed') then raise exception 'Invalid result';end if;
  update nexora_push_private.outbox set state=out_state,lease_until=null,next_at=now()+make_interval(secs=>least(900,(30*power(2,least(attempts,5)))::integer)),finished_at=case when out_state='retry' then null else now() end where id=(p_data->>'id')::uuid and lease_id=(p_data->>'lease')::uuid and state='processing';
  return jsonb_build_object('acknowledged',found);
 end if;
 raise exception 'Invalid bridge action';
end $$;
create or replace function nexora_push_private.kick() returns bigint language plpgsql security definer set search_path='' as $$
declare secret text;request_id bigint;
begin
 delete from nexora_push_private.outbox where finished_at<now()-interval '30 days';
 delete from nexora_push_private.tickets where expires_at<now();
 if not exists(select 1 from nexora_push_private.outbox where state in ('pending','retry','processing') and next_at<=now() and (lease_until is null or lease_until<now())) then return null;end if;
 select decrypted_secret into secret from vault.decrypted_secrets where name='nexora_business_push_bridge';
 if secret is null then return null;end if;
 select net.http_post(url:='https://nexora-beauty-network.templateapp67.chatgpt.site/api/push/event-dispatch',headers:=jsonb_build_object('Content-Type','application/json','Authorization','Bearer '||secret),body:='{}'::jsonb,timeout_milliseconds:=120000) into request_id;
 return request_id;
end $$;
revoke all on function nexora_push_private.kick() from public,anon,authenticated;
