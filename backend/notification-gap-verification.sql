begin;
-- Test records and payment fields exist only in this rolled-back transaction.
-- The timer cannot see them, no HTTP dispatch is requested and no push is sent.
do $$
declare a uuid;b uuid;stale uuid:=gen_random_uuid();fresh uuid:=gen_random_uuid();legacy uuid:=gen_random_uuid();late uuid:=gen_random_uuid();secret text;result jsonb;entry jsonb;ticket text;linked_before timestamptz;claimed uuid;
begin
 select id into a from auth.users order by created_at limit 1;select id into b from auth.users where id<>a order by created_at limit 1;
 if a is null or b is null then raise exception 'Two test identities required';end if;
 perform set_config('request.jwt.claim.sub',a::text,true);perform set_config('request.jwt.claims',jsonb_build_object('sub',a,'role','authenticated')::text,true);
 select decrypted_secret into secret from vault.decrypted_secrets where name='nexora_business_push_bridge';
 insert into nexora_push_private.links(source_user_id,site_user_id,linked_at) values(a,'rollback-gap-user',now()-interval '1 day') on conflict(source_user_id) do update set site_user_id=excluded.site_user_id,linked_at=excluded.linked_at;
 select linked_at into linked_before from nexora_push_private.links where source_user_id=a;
 ticket:=public.nexora_create_notification_link();perform public.nexora_notification_bridge(secret,'redeem',jsonb_build_object('code',ticket,'siteUser','rollback-gap-user'));
 if (select linked_at from nexora_push_private.links where source_user_id=a)<>linked_before then raise exception 'Same-account re-link erased consent age';end if;
 insert into public.studioflow_bookings(id,owner_id,customer_id,stylist_id,starts_at,ends_at,status,payload) values(stale,a,b,'gap-rollback',now()+interval '2 days',now()+interval '2 days 1 hour','pending','{}'),(fresh,a,b,'gap-rollback-2',now()+interval '3 days',now()+interval '3 days 1 hour','pending','{}');
 update public.studioflow_bookings set status='confirmed' where id=stale;update public.studioflow_bookings set status='cancelled' where id=stale;
 insert into public.bookings(id,owner_id,user_id,customer_name,status,booking_status,payment_status,advance_paise,advance_paid_amount) values(legacy,a,b,'Notification rollback verification','confirmed','pending_payment','pending',2500,0);
 if exists(select 1 from nexora_push_private.outbox where source_id=legacy and kind='legacy_booking_confirmed') then raise exception 'Pending canonical booking notified confirmed';end if;
 update public.bookings set booking_status='confirmed' where id=legacy;
 if exists(select 1 from nexora_push_private.outbox where source_id=legacy and kind='legacy_booking_confirmed') then raise exception 'Unpaid advance notified confirmed';end if;
 update public.bookings set payment_status='paid_deposit',advance_paid_amount=25 where id=legacy;
 if (select count(*) from nexora_push_private.outbox where source_id=legacy and kind='legacy_booking_confirmed' and recipient_id=b)<>1 then raise exception 'Canonical customer notification missing';end if;
 result:=public.nexora_notification_bridge(secret,'claim','{}');
 if (select count(*) from nexora_push_private.outbox where source_id=stale and state='superseded')<>2 then raise exception 'Cancelled stale notifications were not suppressed';end if;
 if not exists(select 1 from jsonb_array_elements(result)e where e->>'kind'='legacy_booking_created' and e->>'siteUser'='rollback-gap-user') then raise exception 'Canonical owner recipient resolution failed';end if;
 if not exists(select 1 from jsonb_array_elements(result)e where e->>'kind'='legacy_booking_confirmed') then raise exception 'Canonical confirmation not leased';end if;
 entry:=result->0;claimed:=(entry->>'id')::uuid;
 if (public.nexora_notification_bridge(secret,'ack',jsonb_build_object('id',claimed,'lease',gen_random_uuid(),'state','accepted'))->>'acknowledged')::boolean then raise exception 'Wrong lease could acknowledge';end if;
 perform public.nexora_notification_bridge(secret,'ack',jsonb_build_object('id',claimed,'lease',entry->>'lease','state','retry'));
 if not exists(select 1 from nexora_push_private.outbox where id=claimed and state='retry' and next_at>=now()+interval '60 seconds') then raise exception 'Retry backoff failed';end if;
 insert into public.studioflow_accounts(user_id) values(b) on conflict(user_id) do nothing;
 insert into public.studioflow_bookings(id,owner_id,customer_id,stylist_id,starts_at,ends_at,status,payload) values(late,b,a,'gap-late-link',now()+interval '4 days',now()+interval '4 days 1 hour','pending','{}');
 insert into nexora_push_private.links(source_user_id,site_user_id,linked_at) values(b,'rollback-late-link',now()+interval '1 second') on conflict(source_user_id) do update set site_user_id=excluded.site_user_id,linked_at=excluded.linked_at;
 result:=public.nexora_notification_bridge(secret,'claim','{}');
 if not exists(select 1 from jsonb_array_elements(result)e where e->>'kind'='booking_created' and e->'siteUser'='null'::jsonb) then raise exception 'Events before connection leaked into new link';end if;
 if has_function_privilege('anon','nexora_push_private.event_current(nexora_push_private.outbox)','execute') or has_function_privilege('authenticated','nexora_push_private.kick()','execute') then raise exception 'Private helper is callable by browser';end if;
end $$;
select 'PASS: legacy owner/customer routing, unpaid exclusion, cancelled-event suppression, link-time boundary, same-account renewal, lease isolation, backoff, private helpers (all rolled back; no push sent)' as verification;
rollback;
