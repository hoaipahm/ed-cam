const {spawn}=require('node:child_process');
const path=require('node:path'),fs=require('node:fs'),http=require('node:http');
const look='C:/Users/THE HOAI/.agents/skills/sakuga-vfx/look.mjs';
const chrome='C:/Users/THE HOAI/AppData/Local/ms-playwright/chromium-1255/chrome-win64/chrome.exe';
const assets=path.resolve('app/src/main/assets');
const server=http.createServer((req,res)=>{
 const u=new URL(req.url,'http://127.0.0.1'), name=path.basename(u.pathname);
 if(name==='v120-look.html'){res.setHeader('Content-Type','text/html; charset=utf-8');res.end(fs.readFileSync(path.join(assets,'sakuga.html'),'utf8').replace('</body>','<script src="v120-look-config.js" defer></script></body>'));return;}
 if(name==='v120-look-config.js'){res.setHeader('Content-Type','text/javascript; charset=utf-8');res.end(`const kind=new URLSearchParams(location.search).get('kind');const r=kind==='toggle'?[350,320,160,120]:[290,320,320,100];configureTabs({mode:'none',epoch:1,viewport:900,controls:[{id:1,kind,label:'Chụp ảnh ngay',value:kind==='toggle'?'false':'25',selected:kind==='button',rect:r,clip:[r[0],r[1],r[0]+r[2],r[1]+r[3]]}]});`);return;}
 if(!/^[a-z-]+\.(js|css)$/.test(name)||!fs.existsSync(path.join(assets,name))){res.writeHead(404);res.end();return;}
 res.setHeader('Content-Type',name.endsWith('js')?'text/javascript':'text/css');res.end(fs.readFileSync(path.join(assets,name)));
});
(async()=>{
 await new Promise(r=>server.listen(8878,'127.0.0.1',r));
 try {
  for(const [kind,act] of [
   ['button',`document.querySelector('#controls-host sakuga-button').shadowRoot.querySelector('button').click()`],
   ['toggle',`document.querySelector('#controls-host sakuga-toggle').shadowRoot.querySelector('button').click()`],
   ['slider',`(()=>{const e=document.querySelector('#controls-host sakuga-slider').shadowRoot.querySelector('input');e.value=90;e.dispatchEvent(new Event('input',{bubbles:true}));e.dispatchEvent(new Event('change',{bubbles:true}));})()`]
  ]) await new Promise((resolve,reject)=>{
   const child=spawn(process.execPath,[look,`http://127.0.0.1:8878/v120-look.html?kind=${kind}`,'--select',`#controls-host sakuga-${kind}`,'--act',`0.05:${act}`,'--times','0,0.08,0.16,0.3,0.6,1.2','--cols','3','--pad','100','--out',path.resolve(`qa/v120-frames-${kind}.png`)],{env:{...process.env,CHROME:chrome},stdio:'inherit'});
   child.on('error',reject);child.on('exit',code=>code===0?resolve():reject(Error(`look ${kind}: ${code}`)));
  });
 }finally{server.close();}
})().catch(e=>{console.error(e);process.exitCode=1});
