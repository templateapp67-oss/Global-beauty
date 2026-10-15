begin;
create temp table growth_partner_commissions(like public.growth_partner_commissions including defaults) on commit drop;
create trigger verify_real_transitions after insert or update of status on pg_temp.growth_partner_commissions for each row execute function nexora_push_private.enqueue();
do $$ declare actor uuid;cid uuid:=gen_random_uuid(); begin
 select id into actor from auth.users order by created_at limit 1;
 insert into pg_temp.growth_partner_commissions(id,partner_user_id,referred_shop_id,transaction_date,company_commission_paise,partner_commission_paise,status) values(cid,actor,gen_random_uuid(),current_date,10000,1000,'pending');
 update pg_temp.growth_partner_commissions set status='cleared' where id=cid;
 update pg_temp.growth_partner_commissions set status='reversed' where id=cid;
 update pg_temp.growth_partner_commissions set status='cleared' where id=cid;
 update pg_temp.growth_partner_commissions set status='cleared' where id=cid;
 if (select count(*) from nexora_push_private.outbox where source_id=cid)<>4 then raise exception 'Changed transition suppressed or unchanged write duplicated';end if;
end $$;
select 'PASS: changed transitions enqueue separately, unchanged replay suppressed; no committed record or push' as verification;
rollback;
