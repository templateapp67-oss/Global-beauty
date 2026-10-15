-- Credentials live in Vault; this file intentionally contains no secret value.
create extension if not exists pg_net with schema extensions;
create function nexora_push_private.kick() returns bigint language plpgsql security definer set search_path='' as $$
declare secret text;request_id bigint;
begin
 if not exists(select 1 from nexora_push_private.outbox where state in ('pending','retry','processing') and next_at<=now() and (lease_until is null or lease_until<now())) then return null;end if;
 select decrypted_secret into secret from vault.decrypted_secrets where name='nexora_business_push_bridge';
 if secret is null then return null;end if;
 select net.http_post(url:='https://nexora-beauty-network.templateapp67.chatgpt.site/api/push/event-dispatch',headers:=jsonb_build_object('Content-Type','application/json','Authorization','Bearer '||secret),body:='{}'::jsonb,timeout_milliseconds:=120000) into request_id;
 return request_id;
end $$;
revoke all on function nexora_push_private.kick() from public,anon,authenticated;
select cron.schedule('nexora-beauty-business-push','* * * * *','select nexora_push_private.kick();');
