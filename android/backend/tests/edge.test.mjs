import {test} from 'node:test';
import assert from 'node:assert/strict';
import {execFileSync} from 'node:child_process';
import {mkdtempSync,readFileSync,rmSync} from 'node:fs';
import {tmpdir} from 'node:os';
import path from 'node:path';
import vm from 'node:vm';
import {webcrypto} from 'node:crypto';
const dir=mkdtempSync(path.join(tmpdir(),'nexora-edge-'));
process.on('exit',()=>rmSync(dir,{recursive:true,force:true}));
function handler(name,fetch){
 const outfile=path.join(dir,name+'.js');
 execFileSync(process.env.ESBUILD_BIN||'node_modules/.bin/esbuild',[`functions/${name}/index.ts`,'--bundle','--platform=neutral','--format=iife','--outfile='+outfile],{stdio:'pipe'});
 let handle;
 vm.runInNewContext(readFileSync(outfile,'utf8'),{Deno:{env:{get:key=>({SUPABASE_URL:'https://project.supabase.co',SUPABASE_ANON_KEY:'public-key',SUPABASE_SERVICE_ROLE_KEY:'server-only',PUSH_DISPATCH_SECRET:'test-dispatch-secret'}[key])},serve:fn=>{handle=fn}},fetch,Request,Response,TextEncoder,Uint8Array,Date,JSON,Error,Number,crypto:webcrypto,btoa,atob,URLSearchParams});
 return handle;
}
const uid='11111111-1111-4111-8111-111111111111',installation='22222222-2222-4222-8222-222222222222';
function post(body,auth='Bearer user-token'){return new Request('https://function.test',{method:'POST',headers:{authorization:auth},body:JSON.stringify(body)});}
test('account function rejects missing/invalid authentication',async()=>{
 let calls=0;const handle=handler('companion-account',async()=>{calls++;return new Response('{}',{status:401})});
 assert.equal((await handle(post({action:'device'},''))).status,401);assert.equal(calls,0);
 assert.equal((await handle(post({action:'device'}))).status,401);assert.equal(calls,1);
});
test('device action binds all writes to verified identity, ignoring client user_id',async()=>{
 const calls=[];const handle=handler('companion-account',async(url,options)=>{calls.push({url,options});return url.endsWith('/auth/v1/user')?Response.json({id:uid}):Response.json([])});
 assert.equal((await handle(post({action:'device',installation_id:installation,token:'a'.repeat(30),user_id:'attacker-target'}))).status,200);
 const write=JSON.parse(calls[1].options.body);assert.equal(write.user_id,uid);assert.equal(write.installation_id,installation);assert.equal(calls[1].options.headers.apikey,'server-only');
});
test('malformed device id cannot reach service-role operations',async()=>{
 let calls=0;const handle=handler('companion-account',async()=>{calls++;return Response.json({id:uid})});
 assert.equal((await handle(post({action:'device',installation_id:'bad&user_id=eq.other',token:null}))).status,400);assert.equal(calls,1);
});
test('account deletion requires recent verified sign-in',async()=>{
 let calls=0;const handle=handler('companion-account',async()=>{calls++;return Response.json({id:uid,last_sign_in_at:'2000-01-01T00:00:00Z'})});
 assert.equal((await handle(post({action:'delete_account'}))).status,403);assert.equal(calls,1);
});
test('push endpoint rejects user JWTs and never reaches FCM',async()=>{
 let calls=0;const handle=handler('send-push',async()=>{calls++;throw Error('must not fetch')});
 assert.equal((await handle(post({user_id:uid,title:'hello',body:'test'}))).status,401);assert.equal(calls,0);
});
test('push validates recipient before reading tokens',async()=>{
 let calls=0;const handle=handler('send-push',async()=>{calls++;throw Error('must not fetch')});
 assert.equal((await handle(post({user_id:'not-a-uuid',title:'x',body:'y'},'Bearer test-dispatch-secret'))).status,400);assert.equal(calls,0);
});
