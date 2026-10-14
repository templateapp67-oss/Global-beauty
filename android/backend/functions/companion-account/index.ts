import {authenticated,checked,json,service,uuid} from "../_shared/http.ts";
Deno.serve(async request=>{
 if(request.method!=="POST")return json(405,{error:"POST required"});
 try {
  const user=await authenticated(request);
  if(Number(request.headers.get("content-length")??0)>8192)return json(413,{error:"Request too large"});
  const raw=await request.text();if(raw.length>8192)return json(413,{error:"Request too large"});
  const body=JSON.parse(raw);
  if(body.action==="device") {
   if(!uuid(body.installation_id))return json(400,{error:"Invalid installation"});
   if(body.token===null) {
    await checked(await service(`/rest/v1/nexora_mobile_devices?user_id=eq.${user.id}&installation_id=eq.${body.installation_id}`,"DELETE"));
   } else {
    if(typeof body.token!=="string"||body.token.length<20||body.token.length>4096)return json(400,{error:"Invalid token"});
    await checked(await service("/rest/v1/nexora_mobile_devices?on_conflict=user_id,installation_id","POST",{user_id:user.id,installation_id:body.installation_id,token:body.token,updated_at:new Date().toISOString()}));
   }
   return json(200,{ok:true});
  }
  if(body.action==="delete_companion_data") {
   // Reauthentication required for account deletion; user-editable metadata is never used.
   if(!user.last_sign_in_at || Date.now()-Date.parse(user.last_sign_in_at)>10*60*1000)return json(403,{error:"Sign out and sign in again before deleting your account"});
   for(const bucket of ["nexora-mobile-photos","nexora-mobile-documents"]) {
    for(let batch=0;batch<100;batch++) {
     const rows=await checked(await service(`/storage/v1/object/list/${bucket}`,"POST",{prefix:user.id,limit:100,offset:0})) as {name:string}[];
     if(!rows.length)break;
     await checked(await service(`/storage/v1/object/${bucket}`,"DELETE",{prefixes:rows.map(row=>`${user.id}/${row.name}`)}));
     if(batch===99)throw new Error("Deletion incomplete; retry required");
    }
   }
   // Shared Studioflow Auth identity and business records must remain intact.
   await checked(await service(`/rest/v1/nexora_mobile_devices?user_id=eq.${user.id}`,"DELETE"));
   await checked(await service(`/rest/v1/nexora_mobile_profiles?user_id=eq.${user.id}`,"DELETE"));
   return json(200,{ok:true});
  }
  return json(400,{error:"Unsupported action"});
 } catch(error) { return json(error instanceof Error&&error.message==="Unauthorized"?401:500,{error:"Could not complete account action"}); }
});
