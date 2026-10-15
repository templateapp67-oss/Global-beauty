-- One event for each actual committed transition; unchanged status updates remain no-ops.
create or replace function nexora_push_private.enqueue() returns trigger language plpgsql security definer set search_path='' as $$
begin
 if tg_table_name='studioflow_bookings' then
  if tg_op='INSERT' then insert into nexora_push_private.outbox(event_key,kind,source_id,recipient_id,status_label) values('booking_created:'||new.id,'booking_created',new.id,new.owner_id,new.status) on conflict do nothing;end if;
  if new.status='confirmed' and (tg_op='INSERT' or old.status is distinct from new.status) then insert into nexora_push_private.outbox(event_key,kind,source_id,recipient_id,status_label) values('booking_confirmed:'||new.id,'booking_confirmed',new.id,new.customer_id,'confirmed') on conflict do nothing;end if;
 elsif tg_table_name='growth_partner_reward_claims' then
  if (tg_op='INSERT' and new.status<>'not_eligible') or (tg_op='UPDATE' and old.status is distinct from new.status) then insert into nexora_push_private.outbox(event_key,kind,source_id,recipient_id,status_label) values('reward:'||new.id||':'||new.status||':'||gen_random_uuid(),'reward_updated',new.id,new.partner_user_id,new.status) on conflict do nothing;end if;
 elsif tg_table_name='growth_partner_commissions' then
  if tg_op='INSERT' or old.status is distinct from new.status then insert into nexora_push_private.outbox(event_key,kind,source_id,recipient_id,status_label) values('commission:'||new.id||':'||new.status||':'||gen_random_uuid(),'commission_updated',new.id,new.partner_user_id,new.status) on conflict do nothing;end if;
 end if;
 return new;
end $$;
revoke all on function nexora_push_private.enqueue() from public,anon,authenticated;
