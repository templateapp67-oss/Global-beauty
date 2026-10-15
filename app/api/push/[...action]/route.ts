import { env } from 'cloudflare:workers';
import {handleBusiness} from '../../../../push/business.mjs';
import {handlePush} from '../../../../push/server.mjs';
export const dynamic='force-dynamic';
async function route(request:Request){try{const action=new URL(request.url).pathname.split('/').filter(Boolean)[2]??'';return await (action.startsWith('event-')?handleBusiness:handlePush)(request,env,action);}catch{return Response.json({error:'Notification service unavailable; no delivery confirmed'},{status:503,headers:{'Cache-Control':'no-store'}});}}
export const GET=route;
export const POST=route;
