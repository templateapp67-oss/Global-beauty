import {firebaseConfig} from './config.mjs';
const encoder=new TextEncoder();
const b64=b=>btoa(String.fromCharCode(...new Uint8Array(b))).replaceAll('+','-').replaceAll('/','_').replace(/=+$/,'');
const unb64=s=>Uint8Array.from(atob(s.replaceAll('-','+').replaceAll('_','/')),c=>c.charCodeAt(0));
export const hash=async s=>b64(await crypto.subtle.digest('SHA-256',encoder.encode(s)));
export async function encrypt(token,key){const iv=crypto.getRandomValues(new Uint8Array(12));const k=await crypto.subtle.importKey('raw',unb64(key),'AES-GCM',false,['encrypt']);return b64(iv)+'.'+b64(await crypto.subtle.encrypt({name:'AES-GCM',iv},k,encoder.encode(token)));}
export async function decrypt(cipher,key){const [iv,data]=cipher.split('.');const k=await crypto.subtle.importKey('raw',unb64(key),'AES-GCM',false,['decrypt']);return new TextDecoder().decode(await crypto.subtle.decrypt({name:'AES-GCM',iv:unb64(iv)},k,unb64(data)));}
const json=(status,data)=>Response.json(data,{status,headers:{'Cache-Control':'no-store','X-Content-Type-Options':'nosniff','Vary':'Cookie'}});
export function identity(request,env){const id=request.headers.get('oai-authenticated-user-id');const email=request.headers.get('oai-authenticated-user-email');return {id,admin:!!id&&!!email&&(env.PUSH_ADMIN_EMAILS??'').split(',').map(x=>x.trim().toLowerCase()).includes(email.toLowerCase())};}
export function readiness(env){const blocked=[];if(!env.DB)blocked.push('Token database unavailable');if(!env.PUSH_TOKEN_KEY)blocked.push('Token encryption key missing');if(!env.FCM_VAPID_PUBLIC_KEY)blocked.push('Firebase Web Push public VAPID key missing');if(!env.FIREBASE_SERVICE_ACCOUNT_JSON)blocked.push('Firebase server credential missing');return blocked;}
export async function accessToken(env){const account=JSON.parse(env.FIREBASE_SERVICE_ACCOUNT_JSON);if(account.project_id!==firebaseConfig.projectId)throw new Error('project');const now=Math.floor(Date.now()/1000);const enc=o=>b64(encoder.encode(JSON.stringify(o)));const unsigned=enc({alg:'RS256',typ:'JWT'})+'.'+enc({iss:account.client_email,scope:'https://www.googleapis.com/auth/firebase.messaging',aud:'https://oauth2.googleapis.com/token',iat:now,exp:now+3600});const pem=account.private_key.replace(/-----[^-]+-----/g,'').replace(/\s/g,'');const k=await crypto.subtle.importKey('pkcs8',unb64(pem),{name:'RSASSA-PKCS1-v1_5',hash:'SHA-256'},false,['sign']);const assertion=unsigned+'.'+b64(await crypto.subtle.sign('RSASSA-PKCS1-v1_5',k,encoder.encode(unsigned)));const r=await fetch('https://oauth2.googleapis.com/token',{method:'POST',headers:{'Content-Type':'application/x-www-form-urlencoded'},body:new URLSearchParams({grant_type:'urn:ietf:params:oauth:grant-type:jwt-bearer',assertion}),signal:AbortSignal.timeout(10000)});if(!r.ok)throw new Error('oauth');const data=await r.json();if(typeof data.access_token!=='string')throw new Error('oauth');return data.access_token;}
export async function handlePush(request,env,action){
 const url=new URL(request.url),who=identity(request,env),now=Date.now();
 if(request.method==='GET'&&action==='status'){
  const blocked=readiness(env);let devices=0,tests=[];
  if(who.id&&env.DB){devices=(await env.DB.prepare('SELECT count(*) AS n FROM push_devices WHERE user_id=? AND updated_at>?').bind(who.id,now-30*86400000).first()).n;if(who.admin)tests=(await env.DB.prepare("SELECT id,state,created_at,received_at FROM push_tests WHERE category='test' AND user_id=? ORDER BY created_at DESC LIMIT 5").bind(who.id).all()).results;}
  return json(200,{signedIn:!!who.id,admin:who.admin,scope:who.id?await hash(who.id):null,firebaseConfig,vapidKey:env.FCM_VAPID_PUBLIC_KEY??null,registrationReady:!!env.DB&&!!env.PUSH_TOKEN_KEY&&!!env.FCM_VAPID_PUBLIC_KEY,dispatchReady:blocked.length===0,blocked,devices,tests});
 }
 if(request.method!=='POST')return json(405,{error:'POST required'});
 if(((action!=='receipt'||request.headers.has('origin'))&&request.headers.get('origin')!==url.origin)||!request.headers.get('content-type')?.startsWith('application/json'))return json(403,{error:'Same-origin JSON request required'});
 const raw=await request.text();if(raw.length>4096)return json(413,{error:'Request too large'});let data;try{data=JSON.parse(raw);}catch{return json(400,{error:'Invalid JSON'});}
 if(!data||typeof data!=='object'||Array.isArray(data))return json(400,{error:'Invalid request'});
 // Receipt capability exists only in the real FCM payload; no session is needed in a background worker.
 if(action==='receipt'){
  if(typeof data.receipt!=='string'||!/^[A-Za-z0-9_-]{43}$/.test(data.receipt)||!env.DB)return json(400,{error:'Invalid receipt'});
  const row=await env.DB.prepare('UPDATE push_tests SET state=?,received_at=? WHERE receipt_hash=? AND created_at>? AND state IN (?,?) RETURNING id,event_id,token_hash,user_id').bind('displayed',now,await hash(data.receipt),now-600000,'sending','accepted').first();if(row)await env.DB.prepare('UPDATE push_devices SET updated_at=? WHERE token_hash=? AND user_id=?').bind(now,row.token_hash,row.user_id).run();if(row?.event_id)await env.DB.prepare('UPDATE business_notifications SET state=?,received_at=? WHERE id=?').bind('displayed',now,row.event_id).run();return row?json(200,{received:true}):json(404,{error:'Receipt expired or unknown'});
 }
 if(!who.id)return json(401,{error:'Sign in with ChatGPT to manage this Site’s notifications'});
 if(action==='register'){
  if(!env.DB||!env.PUSH_TOKEN_KEY||!env.FCM_VAPID_PUBLIC_KEY)return json(503,{error:'Web Push setup incomplete'});
  if(data.consent!==true||typeof data.token!=='string'||!/^[A-Za-z0-9_:\-]{40,4096}$/.test(data.token))return json(400,{error:'Valid token and explicit consent required'});
  if(data.previousDeviceId!==undefined&&data.previousDeviceId!==null&&(typeof data.previousDeviceId!=='string'||!/^[A-Za-z0-9_-]{43}$/.test(data.previousDeviceId)))return json(400,{error:'Invalid previous device'});
  const tokenHash=await hash(data.token),cipher=await encrypt(data.token,env.PUSH_TOKEN_KEY);
  await env.DB.batch([
   env.DB.prepare('DELETE FROM push_devices WHERE user_id=? AND token_hash=? AND token_hash<>?').bind(who.id,data.previousDeviceId??'',tokenHash),
   env.DB.prepare('DELETE FROM push_devices WHERE updated_at<?').bind(now-30*86400000),
   env.DB.prepare('INSERT INTO push_devices(token_hash,user_id,token_cipher,updated_at) VALUES(?,?,?,?) ON CONFLICT(token_hash) DO UPDATE SET user_id=excluded.user_id,token_cipher=excluded.token_cipher,updated_at=excluded.updated_at').bind(tokenHash,who.id,cipher,now),
   env.DB.prepare('DELETE FROM push_devices WHERE user_id=? AND token_hash NOT IN (SELECT token_hash FROM push_devices WHERE user_id=? ORDER BY updated_at DESC LIMIT 10)').bind(who.id,who.id),
  ]);return json(200,{registered:true,deviceId:tokenHash,scope:await hash(who.id)});
 }
 if(action==='unregister'){
  if(!env.DB)return json(503,{error:'Token database unavailable'});
  if(typeof data.deviceId!=='string'||!/^[A-Za-z0-9_-]{43}$/.test(data.deviceId))return json(400,{error:'Invalid device'});
  await env.DB.prepare('DELETE FROM push_devices WHERE user_id=? AND token_hash=?').bind(who.id,data.deviceId).run();return json(200,{removed:true});
 }
 if(action==='test'){
  if(!who.admin)return json(403,{error:'Site administrator required'});
  const blocked=readiness(env);if(blocked.length)return json(503,{error:'Firebase setup incomplete',blocked});
  if(typeof data.deviceId!=='string'||!/^[A-Za-z0-9_-]{43}$/.test(data.deviceId))return json(400,{error:'Enable notifications on this browser first'});
  const row=await env.DB.prepare('SELECT token_cipher FROM push_devices WHERE user_id=? AND token_hash=? AND updated_at>?').bind(who.id,data.deviceId,now-30*86400000).first();if(!row)return json(404,{error:'No registered device for this signed-in administrator'});
  const id=crypto.randomUUID(),receipt=b64(crypto.getRandomValues(new Uint8Array(32)));
  // Atomic rate limit: concurrent clicks cannot bypass the 30-second window.
  const inserted=await env.DB.prepare('INSERT INTO push_tests(id,user_id,token_hash,receipt_hash,created_at,state) SELECT ?,?,?,?,?,? WHERE NOT EXISTS (SELECT 1 FROM push_tests WHERE user_id=? AND created_at>?) RETURNING id').bind(id,who.id,data.deviceId,await hash(receipt),now,'sending',who.id,now-30000).first();if(!inserted)return json(429,{error:'Wait 30 seconds before another test'});
  await env.DB.prepare('DELETE FROM push_tests WHERE category=\'test\' AND created_at<?').bind(now-7*86400000).run();
  try{
   const oauth=await accessToken(env),token=await decrypt(row.token_cipher,env.PUSH_TOKEN_KEY);
   const response=await fetch('https://fcm.googleapis.com/v1/projects/nexora-beauty/messages:send',{method:'POST',headers:{Authorization:'Bearer '+oauth,'Content-Type':'application/json'},body:JSON.stringify({message:{token,data:{title:'Nexora test notification',body:'Real FCM test from Nexora Beauty Network.',receipt,scope:await hash(who.id),testId:id},webpush:{headers:{TTL:'600',Urgency:'normal'}}}}),signal:AbortSignal.timeout(10000)});
   if(!response.ok){let err={};try{err=await response.json();}catch{}if(err.error?.details?.some(d=>d.errorCode==='UNREGISTERED'))await env.DB.prepare('DELETE FROM push_devices WHERE token_hash=? AND user_id=?').bind(data.deviceId,who.id).run();throw new Error('fcm');}
   const responseBody=await response.json();if(typeof responseBody.name!=='string')throw new Error('fcm');
   await env.DB.prepare('UPDATE push_tests SET state=? WHERE id=? AND state=?').bind('accepted',id,'sending').run();
   return json(200,{id,state:'accepted',message:'FCM accepted the message. Phone delivery is not yet confirmed; refresh for a display receipt.'});
  }catch{await env.DB.prepare('UPDATE push_tests SET state=? WHERE id=? AND state=?').bind('failed',id,'sending').run();return json(502,{error:'Firebase dispatch failed. Check server credentials, IAM permission and FCM API. No delivery confirmed.',id});}
 }
 return json(404,{error:'Unknown action'});
}
