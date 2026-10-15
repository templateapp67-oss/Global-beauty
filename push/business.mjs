import {hash,decrypt,accessToken,identity} from './server.mjs';
const backend='https://zilwgiuiygqqardzutxi.supabase.co/rest/v1/rpc/nexora_notification_bridge';
const publicKey='sb_publishable_Xck8H8PcTCCnEwkvBfiSLw_c973vJkH';
const json=(status,body)=>Response.json(body,{status,headers:{'Cache-Control':'no-store','Vary':'Cookie'}});
export const businessMessage=(kind,status)=>{
 const labels={pending:'pending',cleared:'cleared',paid:'paid',reversed:'reversed',verification_pending:'verification pending',documents_required:'documents required',under_review:'under review',approved:'approved',product_ordered:'ordered',dispatched:'dispatched',delivered:'delivered',rejected:'rejected',appeal_submitted:'appeal submitted',not_eligible:'not eligible'};
 if(kind==='legacy_booking_created')return {title:'New salon booking',body:'A new booking has been saved. Open your salon dashboard for details.',url:'https://fanal-templetes-app.vercel.app/dashboard'};
 if(kind==='legacy_booking_confirmed')return {title:'Booking confirmed',body:'Your appointment is confirmed. Open My bookings for details.',url:'https://fanal-templetes-app.vercel.app/my-bookings'};
 if(kind==='booking_created')return {title:'New salon booking',body:'A new booking has been saved. Open your salon workspace for details.',url:'https://nexora-one-white-label.templateapp67.chatgpt.site/'};
 if(kind==='booking_confirmed')return {title:'Booking confirmed',body:'Your salon appointment is confirmed. Open My bookings for details.',url:'https://nexora-one-white-label.templateapp67.chatgpt.site/?customer=1'};
 if(kind==='reward_updated'&&labels[status])return {title:'Growth Partner reward update',body:'Your reward claim status is now '+labels[status]+'. Programme verification and terms apply.',url:'https://fanal-templetes-app.vercel.app/partner/dashboard'};
 if(kind==='commission_updated'&&['pending','cleared','paid','reversed'].includes(status))return {title:'Growth Partner commission update',body:'A commission record is now '+labels[status]+'. Check your dashboard for the settled details.',url:'https://fanal-templetes-app.vercel.app/partner/dashboard'};
 throw new Error('Unsupported business event');
};
export async function bridge(env,action,data={}){
 if(!env.PUSH_BRIDGE_SECRET)throw new Error('Business notification setup unavailable');
 const r=await fetch(backend,{method:'POST',headers:{apikey:publicKey,'Content-Type':'application/json'},body:JSON.stringify({p_secret:env.PUSH_BRIDGE_SECRET,p_action:action,p_data:data}),signal:AbortSignal.timeout(10000)});
 if(!r.ok)throw new Error(action==='redeem'?'Connection link expired or already used. Open Notifications from your business account again.':'Business notification service unavailable');return r.json();
}
async function dispatch(env){
 const events=await bridge(env,'claim');let accepted=0,skipped=0,retry=0,oauth;
 const deadline=Date.now()+45000;
 await env.DB.batch([env.DB.prepare('DELETE FROM push_devices WHERE updated_at<?').bind(Date.now()-30*86400000),env.DB.prepare('DELETE FROM push_tests WHERE created_at<?').bind(Date.now()-30*86400000),env.DB.prepare('DELETE FROM business_notifications WHERE created_at<?').bind(Date.now()-30*86400000)]);
 for(const event of events){let result='retry';try{
  if(Date.now()>deadline){await bridge(env,'ack',{id:event.id,lease:event.lease,state:'retry'});retry++;continue;}
  if(!event.siteUser){result='no_subscription';skipped++;}
  else {
   const message=businessMessage(event.kind,event.status),now=Date.now();
   await env.DB.prepare('INSERT INTO business_notifications(id,user_id,category,title,body,action_url,state,created_at) VALUES(?,?,?,?,?,?,?,?) ON CONFLICT(id) DO NOTHING').bind(event.id,event.siteUser,event.kind,message.title,message.body,message.url,'pending',event.createdAt??now).run();
   const devices=(await env.DB.prepare('SELECT token_hash,token_cipher FROM push_devices WHERE user_id=? AND updated_at>?').bind(event.siteUser,now-30*86400000).all()).results;
   if(!devices.length){result='no_subscription';skipped++;}
   else {let errors=0,sent=0;
    for(const device of devices){
     if(Date.now()>deadline){errors++;break;}
     const id='event:'+await hash(event.id+':'+device.token_hash);
     const prior=await env.DB.prepare('SELECT state,created_at FROM push_tests WHERE id=?').bind(id).first();
     if(prior&&['accepted','displayed'].includes(prior.state)){sent++;continue;}
     if(prior?.state==='sending'&&prior.created_at>now-120000){errors++;continue;}
     const receipt=crypto.randomUUID()+crypto.randomUUID();const receiptCode=await hash(receipt);
     const reserved=await env.DB.prepare('INSERT INTO push_tests(id,user_id,token_hash,receipt_hash,created_at,state,category,title,body,action_url,event_id) VALUES(?,?,?,?,?,?,?,?,?,?,?) ON CONFLICT(id) DO UPDATE SET state=excluded.state,receipt_hash=excluded.receipt_hash,created_at=excluded.created_at WHERE push_tests.state=? OR (push_tests.state=? AND push_tests.created_at<?) RETURNING id').bind(id,event.siteUser,device.token_hash,await hash(receiptCode),now,'sending',event.kind,message.title,message.body,message.url,event.id,'failed','sending',now-120000).first();
     if(!reserved){errors++;continue;}
     try {
      oauth??=await accessToken(env);const token=await decrypt(device.token_cipher,env.PUSH_TOKEN_KEY);
      const response=await fetch('https://fcm.googleapis.com/v1/projects/nexora-beauty/messages:send',{method:'POST',headers:{Authorization:'Bearer '+oauth,'Content-Type':'application/json'},body:JSON.stringify({message:{token,data:{title:message.title,body:message.body,url:message.url,receipt:receiptCode,scope:await hash(event.siteUser),testId:id},webpush:{headers:{TTL:'600',Urgency:'normal'}}}}),signal:AbortSignal.timeout(10000)});
      if(!response.ok){let e={};try{e=await response.json();}catch{}if(e.error?.details?.some(d=>d.errorCode==='UNREGISTERED')){await env.DB.prepare('DELETE FROM push_devices WHERE token_hash=? AND user_id=?').bind(device.token_hash,event.siteUser).run();await env.DB.prepare('UPDATE push_tests SET state=? WHERE id=? AND state=?').bind('failed',id,'sending').run();continue;}throw new Error('Dispatch rejected');}
      const body=await response.json();if(typeof body.name!=='string')throw new Error('Missing FCM acknowledgement');
      await env.DB.prepare('UPDATE push_tests SET state=? WHERE id=? AND state=?').bind('accepted',id,'sending').run();sent++;
     }catch{errors++;oauth=undefined;await env.DB.prepare('UPDATE push_tests SET state=?,failure_code=? WHERE id=? AND state=?').bind('failed','transport_or_fcm_error',id,'sending').run();}
    }
    result=errors?'retry':sent?'accepted':'no_subscription';if(result==='accepted')accepted++;else if(result==='retry')retry++;else skipped++;
   }
  }
 }catch{retry++;result='retry';}
 if(event.siteUser)await env.DB.prepare("UPDATE business_notifications SET state=? WHERE id=? AND user_id=? AND state<>'displayed'").bind(result,event.id,event.siteUser).run();
 await bridge(env,'ack',{id:event.id,lease:event.lease,state:result});
 }
 return json(200,{processed:events.length,accepted,skipped,retry});
}
export async function handleBusiness(request,env,action){
 if(action==='event-dispatch'){
  if(request.method!=='POST')return json(405,{error:'POST required'});
  const provided=request.headers.get('authorization')??'';
  if(!env.PUSH_BRIDGE_SECRET||await hash(provided)!==await hash('Bearer '+env.PUSH_BRIDGE_SECRET))return json(401,{error:'Unauthorized'});
  return dispatch(env);
 }
 const who=identity(request,env);if(!who.id)return json(401,{error:'Sign in with ChatGPT first'});
 if(action==='event-health'&&request.method==='GET'){
  if(!who.admin)return json(403,{error:'Site administrator required'});
  const health=await bridge(env,'health');
  const delivery=(await env.DB.prepare('SELECT state,count(*) AS count FROM push_tests WHERE category<>? AND created_at>? GROUP BY state').bind('test',Date.now()-86400000).all()).results;
  return json(200,{...health,delivery});
 }
 if(action==='event-status'&&request.method==='GET'){
  const connection=await bridge(env,'connections',{siteUser:who.id});
  const inbox=(await env.DB.prepare('SELECT id,title,body,action_url,state,created_at FROM business_notifications WHERE user_id=? ORDER BY created_at DESC LIMIT 20').bind(who.id).all()).results;
  return json(200,{connections:connection.count,inbox});
 }
 if(request.method!=='POST')return json(405,{error:'POST required'});
 if(request.headers.get('origin')!==new URL(request.url).origin||!request.headers.get('content-type')?.startsWith('application/json'))return json(403,{error:'Same-origin JSON required'});
 const raw=await request.text();if(raw.length>1024)return json(413,{error:'Request too large'});let body;try{body=JSON.parse(raw);}catch{return json(400,{error:'Invalid JSON'});}
 if(!body||body.consent!==true)return json(400,{error:'Explicit confirmation required'});
 if(action==='event-connect'){
  if(typeof body.code!=='string'||!/^[a-f0-9]{64}$/.test(body.code))return json(400,{error:'Invalid connection link'});
  try{return json(200,await bridge(env,'redeem',{code:body.code,siteUser:who.id}));}catch(e){return json(400,{error:e.message});}
 }
 if(action==='event-disconnect')return json(200,await bridge(env,'disconnect',{siteUser:who.id}));
 return json(404,{error:'Unknown action'});
}
