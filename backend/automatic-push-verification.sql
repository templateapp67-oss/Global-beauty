begin;
-- Synthetic transaction-local source replicas: no real bookings, commissions or
-- reward claims are created, no queue entries commit and no FCM sends happen.
create temp table studioflow_bookings (like public.studioflow_bookings including defaults) on commit drop;
create temp table growth_partner_reward_claims (like public.growth_partner_reward_claims including defaults) on commit drop;
create temp table growth_partner_commissions (like public.growth_partner_commissions including defaults) on commit drop;
create trigger verify_booking after insert or update of status on pg_temp.studioflow_bookings for each row execute function nexora_push_private.enqueue();
create trigger verify_reward after insert or update of status on pg_temp.growth_partner_reward_claims for each row execute function nexora_push_private.enqueue();
create trigger verify_commission after insert or update of status on pg_temp.growth_partner_commissions for each row execute function nexora_push_private.enqueue();
do $$
declare a uuid;b uuid;bid uuid:=gen_random_uuid();rid uuid:=gen_random_uuid();cid uuid:=gen_random_uuid();code text;secret text;result jsonb;claim_attempt integer;blocked boolean;before_count bigint;
begin
 select id into a from auth.users order by created_at limit 1;select id into b from auth.users where id<>a order by created_at limit 1;
 if a is null or b is null then raise exception 'Two existing auth identities required';end if;
 select decrypted_secret into secret from vault.decrypted_secrets where name='nexora_business_push_bridge';
 select count(*) into before_count from nexora_push_private.outbox;
 insert into pg_temp.studioflow_bookings(id,owner_id,customer_id,stylist_id,starts_at,ends_at,status,payload) values(bid,a,b,'queue-verification',now()+interval '1 day',now()+interval '1 day 1 hour','pending','{}');
 if (select count(*) from nexora_push_private.outbox where source_id=bid and kind='booking_created' and recipient_id=a)<>1 then raise exception 'Owner routing failed';end if;
 if exists(select 1 from nexora_push_private.outbox where source_id=bid and kind='booking_confirmed') then raise exception 'Pending booking incorrectly confirmed';end if;
 update pg_temp.studioflow_bookings set status='confirmed' where id=bid;
 update pg_temp.studioflow_bookings set status='confirmed' where id=bid;
 if (select count(*) from nexora_push_private.outbox where source_id=bid and kind='booking_confirmed' and recipient_id=b)<>1 then raise exception 'Customer routing or deduplication failed';end if;
 insert into pg_temp.growth_partner_reward_claims(id,partner_user_id,qualifying_shops,status) values(rid,b,25,'under_review');
 update pg_temp.growth_partner_reward_claims set status='approved' where id=rid;
 if (select count(*) from nexora_push_private.outbox where source_id=rid and recipient_id=b and kind='reward_updated')<>2 then raise exception 'Reward transition routing failed';end if;
 insert into pg_temp.growth_partner_commissions(id,partner_user_id,referred_shop_id,transaction_date,company_commission_paise,partner_commission_paise,status) values(cid,b,gen_random_uuid(),current_date,10000,1000,'pending');
 update pg_temp.growth_partner_commissions set status='cleared' where id=cid;
 update pg_temp.growth_partner_commissions set status='cleared' where id=cid;
 if (select count(*) from nexora_push_private.outbox where source_id=cid and recipient_id=b and kind='commission_updated')<>2 then raise exception 'Commission routing or deduplication failed';end if;
 perform set_config('request.jwt.claim.sub',a::text,true);perform set_config('request.jwt.claims',jsonb_build_object('sub',a,'role','authenticated')::text,true);
 execute 'set local role authenticated';
 code:=public.nexora_create_notification_link();
 blocked:=false;begin perform 1 from nexora_push_private.outbox;exception when insufficient_privilege then blocked:=true;end;
 if not blocked then raise exception 'Authenticated direct queue access was allowed';end if;
 execute 'reset role';
 result:=public.nexora_notification_bridge(secret,'redeem',jsonb_build_object('code',code,'siteUser','rollback-notification-account'));
 if result->>'connected'<>'true' then raise exception 'Account proof failed';end if;
 blocked:=false;begin perform public.nexora_notification_bridge(secret,'redeem',jsonb_build_object('code',code,'siteUser','different-account'));exception when others then blocked:=true;end;
 if not blocked then raise exception 'Ticket replay allowed';end if;
 blocked:=false;begin perform public.nexora_notification_bridge('invalid-secret','claim','{}');exception when insufficient_privilege then blocked:=true;end;
 if not blocked then raise exception 'Invalid bridge secret accepted';end if;
 -- A real, rollback-only source row is needed after source-current validation.
 insert into public.studioflow_accounts(user_id) values(a) on conflict(user_id) do nothing;
 insert into public.studioflow_bookings(owner_id,customer_id,stylist_id,starts_at,ends_at,status,payload) values(a,b,'lease-rollback-fixture',now()+interval '90 days',now()+interval '90 days 1 hour','pending','{}');
 for claim_attempt in 1..3 loop
  result:=public.nexora_notification_bridge(secret,'claim','{}');
  exit when jsonb_array_length(result)>0;
 end loop;
 if jsonb_array_length(result)=0 then raise exception 'Queue lease failed';end if;
 perform public.nexora_notification_bridge(secret,'disconnect',jsonb_build_object('siteUser','rollback-notification-account'));
 if exists(select 1 from nexora_push_private.links where site_user_id='rollback-notification-account') then raise exception 'Disconnect failed';end if;
end $$;
select 'PASS: owner/customer routing, pending-payment exclusion, reward/commission transitions, deduplication, account proof/replay, queue privacy, bridge secret, leasing and disconnect (rolled back)' as verification;
rollback;
