import {env,checked,json,service,uuid} from "../_shared/http.ts";
// FCM HTTP v1 uses a short-lived service-account OAuth access token. No admin
// endpoint or messaging credential is exposed to the Android client.
const b64=(bytes:Uint8Array)=>btoa(String.fromCharCode(...bytes)).replaceAll("+","-").replaceAll("/","_").replace(/=+$/,"");
async function fcmAccessToken():Promise<{token:string;project:string}> {
 const account=JSON.parse(env("FIREBASE_SERVICE_ACCOUNT_JSON"));
 const now=Math.floor(Date.now()/1000);
 const encoded=(value:unknown)=>b64(new TextEncoder().encode(JSON.stringify(value)));
 const input=encoded({alg:"RS256",typ:"JWT"})+"."+encoded({iss:account.client_email,scope:"https://www.googleapis.com/auth/firebase.messaging",aud:"https://oauth2.googleapis.com/token",iat:now,exp:now+3600});
 const pem=account.private_key.replace(/-----[^-]+-----/g,"").replace(/\s/g,"");
 const key=await crypto.subtle.importKey("pkcs8",Uint8Array.from(atob(pem),c=>c.charCodeAt(0)),{name:"RSASSA-PKCS1-v1_5",hash:"SHA-256"},false,["sign"]);
 const signature=await crypto.subtle.sign("RSASSA-PKCS1-v1_5",key,new TextEncoder().encode(input));
 const response=await fetch("https://oauth2.googleapis.com/token",{method:"POST",headers:{"content-type":"application/x-www-form-urlencoded"},body:new URLSearchParams({grant_type:"urn:ietf:params:oauth:grant-type:jwt-bearer",assertion:input+"."+b64(new Uint8Array(signature))})});
 if(!response.ok)throw new Error("FCM authentication failed");
 return {token:(await response.json()).access_token,project:account.project_id};
}
async function equalSecret(a:string,b:string):Promise<boolean> {
 const hash=async(v:string)=>new Uint8Array(await crypto.subtle.digest("SHA-256",new TextEncoder().encode(v)));
 const [x,y]=await Promise.all([hash(a),hash(b)]);let diff=0;for(let i=0;i<x.length;i++)diff|=x[i]^y[i];return diff===0;
}
Deno.serve(async request=>{
 if(request.method!=="POST")return json(405,{error:"POST required"});
 try {
  if(!await equalSecret(request.headers.get("authorization")??"",`Bearer ${env("PUSH_DISPATCH_SECRET")}`))return json(401,{error:"Unauthorized"});
  const raw=await request.text();if(raw.length>2048)return json(413,{error:"Request too large"});
  const {user_id,title,body}=JSON.parse(raw);
  if(!uuid(user_id)||typeof title!=="string"||title.length>100||typeof body!=="string"||body.length>300)return json(400,{error:"Invalid message"});
  const rows=await checked(await service(`/rest/v1/nexora_mobile_devices?user_id=eq.${user_id}&select=installation_id,token&limit=100`)) as {installation_id:string;token:string}[];
  if(!rows.length)return json(200,{sent:0,failed:0});
  const oauth=await fcmAccessToken();let sent=0,failed=0;
  for(const row of rows) {
   const response=await fetch(`https://fcm.googleapis.com/v1/projects/${oauth.project}/messages:send`,{method:"POST",headers:{authorization:`Bearer ${oauth.token}`,"content-type":"application/json"},body:JSON.stringify({message:{token:row.token,data:{user_id,title,body},android:{priority:"normal",ttl:"3600s"}}})});
   if(response.ok)sent++;else { failed++;const error=await response.json();const dead=response.status===404 || error.error?.details?.some((d:{errorCode?:string})=>d.errorCode==="UNREGISTERED");if(dead)await checked(await service(`/rest/v1/nexora_mobile_devices?user_id=eq.${user_id}&installation_id=eq.${row.installation_id}`,"DELETE")); }
  }
  return json(200,{sent,failed});
 } catch {return json(500,{error:"Push dispatch failed"});}
});
