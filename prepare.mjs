import fs from 'node:fs';
import vm from 'node:vm';
const script=fs.readFileSync('render-source.js','utf8');
const shell=fs.readFileSync('dist/index.html','utf8').replace(/<link rel="preload"[^>]*>/g,'');
const routes=['home','discover','business','templates','jobs','marketplace','growth-partner','rewards','about','contact','policies','app'];
for(const route of routes){const ctx=vm.createContext({location:{pathname:route==='home'?'/':'/'+route+'/'},window:{},document:{},CustomEvent:class{},URLSearchParams});const content=vm.runInContext(script+"\nlinkJobs(header()+`<main id=\"main\">${(pages[route]||home)()}</main>`+footer())",ctx);let html=shell.replace(/<div id="app">[\s\S]*?<\/div>(?=<dialog)/,'<div id="app">'+content+'</div>');if(route!=='home'){html=html.replace('<title>Nexora | The Global Beauty Growth Network</title>','<title>'+route.replaceAll('-',' ')+' | Nexora</title>');fs.mkdirSync('dist/'+route,{recursive:true});}const firstImage=content.match(/<img[^>]*src="([^"]+)"/);if(firstImage)html=html.replace('</head>','<link rel="preload" as="image" href="'+firstImage[1]+'" fetchpriority="high"></head>');const path=route==='home'?'dist/index.html':'dist/'+route+'/index.html';fs.writeFileSync(path,html);if(!html.includes('id="main"'))throw new Error('Missing content '+route)}
const manifest=JSON.parse(fs.readFileSync('.openai/hosting.json','utf8'));manifest.static={directory:'dist'};fs.writeFileSync('.openai/hosting.json',JSON.stringify(manifest,null,2)+'\n');

fs.writeFileSync('dist/robots.txt','User-agent: *\nAllow: /\nSitemap: https://nexora-beauty-network.templateapp67.chatgpt.site/sitemap.xml\n');
fs.writeFileSync('dist/sitemap.xml','<?xml version="1.0" encoding="UTF-8"?><urlset xmlns="http://www.sitemaps.org/schemas/sitemap/0.9">'+routes.map(r=>'<url><loc>https://nexora-beauty-network.templateapp67.chatgpt.site/'+(r==='home'?'':r+'/')+'</loc></url>').join('')+'</urlset>');
console.log('Prepared '+routes.length+' rendered pages');
