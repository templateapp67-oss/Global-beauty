export const json = (status: number, value: unknown) => new Response(JSON.stringify(value), {status, headers:{"content-type":"application/json"}});
export function env(name: string): string { const value=Deno.env.get(name); if(!value) throw new Error("Missing server configuration"); return value; }
export async function service(path:string, method="GET", body?:unknown):Promise<Response> {
  return await fetch(env("SUPABASE_URL")+path,{method,headers:{apikey:env("SUPABASE_SERVICE_ROLE_KEY"),Authorization:`Bearer ${env("SUPABASE_SERVICE_ROLE_KEY")}`,"Content-Type":"application/json",Prefer:"return=representation,resolution=merge-duplicates"},body:body===undefined?undefined:JSON.stringify(body)});
}
export async function checked(response:Response):Promise<unknown> { if(!response.ok)throw new Error("Server operation failed"); return response.status===204?null:await response.json(); }
export async function authenticated(request:Request):Promise<{id:string;last_sign_in_at?:string}> {
  const bearer=request.headers.get("authorization")??"";
  if(!/^Bearer \S+$/i.test(bearer))throw new Error("Unauthorized");
  const response=await fetch(env("SUPABASE_URL")+"/auth/v1/user",{headers:{apikey:env("SUPABASE_ANON_KEY"),authorization:bearer}});
  if(!response.ok)throw new Error("Unauthorized");
  const user=await response.json();
  if(typeof user.id!=="string" || user.is_anonymous)throw new Error("Unauthorized");
  return user;
}
export const uuid=(value:unknown):value is string=>typeof value==="string" && /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i.test(value);
