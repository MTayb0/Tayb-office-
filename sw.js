const C='tayb-office-v5',F=['./','index.html','manifest.json','icon-192.png','icon-512.png'];
self.addEventListener('install',e=>{e.waitUntil(caches.open(C).then(c=>c.addAll(F)).then(()=>self.skipWaiting()))});
self.addEventListener('activate',e=>{e.waitUntil(caches.keys().then(k=>Promise.all(k.filter(x=>x!==C&&x!=='tayb-share').map(x=>caches.delete(x)))).then(()=>self.clients.claim()))});
self.addEventListener('fetch',e=>{const r=e.request,u=new URL(r.url);
 if(r.method==='POST'&&u.pathname.endsWith('/share')){e.respondWith((async()=>{try{const fd=await r.formData(),f=fd.getAll('file')[0];
  if(f){const c=await caches.open('tayb-share');await c.put('shared-file',new Response(f,{headers:{'x-name':encodeURIComponent(f.name||'file'),'content-type':f.type||'application/octet-stream'}}))}}catch(err){}
  return Response.redirect(new URL('index.html?shared=1',self.registration.scope).href,303)})());return}
 if(r.method!=='GET')return;
 e.respondWith(caches.match(r,{ignoreSearch:true}).then(m=>{const n=fetch(r).then(x=>{if(x&&x.ok)caches.open(C).then(c=>c.put(r,x.clone()));return x}).catch(()=>m);return m||n}))});
