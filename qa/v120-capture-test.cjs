const {execFileSync}=require('node:child_process');
const fs=require('node:fs');
const assert=require('node:assert/strict');
const adb=require('node:path').resolve('.tools/android-sdk/platform-tools/adb.exe');
function device(...args){return execFileSync(adb,args,{encoding:'utf8'});}
function shot(name){device('shell','screencap','-p','/data/local/tmp/v120.png'); device('pull','/data/local/tmp/v120.png',`qa/v120-${name}.png`);}
function nativeUI(){device('shell','uiautomator','dump','/data/local/tmp/v120-ui.xml');return device('shell','cat','/data/local/tmp/v120-ui.xml');}
function bounds(node){return node.match(/bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"/).slice(1).map(Number);}
function tapText(text){const n=(nativeUI().match(/<node\b[^>]*>/g)||[]).find(n=>n.includes(`text="${text}"`) && n.includes('class="android.widget.TextView"'));assert(n,`Native text ${text}`);const b=bounds(n);device('shell','input','tap',String(Math.round((b[0]+b[2])/2)),String(Math.round((b[1]+b[3])/2)));}
const delay=ms=>new Promise(r=>setTimeout(r,ms));
async function connect(target){
 const ws=new WebSocket(target.webSocketDebuggerUrl);await new Promise(r=>ws.addEventListener('open',r,{once:true}));
 let id=0;const pending=new Map();
 ws.addEventListener('message',e=>{const m=JSON.parse(e.data);if(m.id){pending.get(m.id)?.(m);pending.delete(m.id);}});
 const send=(method,params={})=>new Promise((resolve,reject)=>{const n=++id;pending.set(n,m=>m.error?reject(Error(JSON.stringify(m.error))):resolve(m.result));ws.send(JSON.stringify({id:n,method,params}));});
 const evaluate=async expression=>{const r=await send('Runtime.evaluate',{expression,returnByValue:true});if(r.exceptionDetails)throw Error(JSON.stringify(r.exceptionDetails));return r.result.value;};
 const tap=async expression=>{
   const r=await evaluate(`(()=>{const e=${expression},r=e.getBoundingClientRect();return {x:r.x+r.width/2,y:r.y+r.height/2,w:innerWidth};})()`);
   const nodes=(nativeUI().match(/<node\b[^>]*>/g)||[]).filter(n=>n.includes('class="android.webkit.WebView"'));
   const b=nodes.length?bounds(nodes.at(-1)):[0,0,720,1280];
   const scale=(b[2]-b[0])/r.w;
   device('shell','input','tap',String(Math.round(b[0]+r.x*scale)),String(Math.round(b[1]+r.y*scale)));
   await delay(800);
 };
 return {ws,send,evaluate,tap};
}
(async()=>{
 device('shell','am','force-stop','com.edcam');device('shell','am','start','-n','com.edcam/.MainActivity');
 let match;for(let n=0;n<40&&!match;n++){match=device('shell','cat','/proc/net/unix').match(/@(webview_devtools_remote_\d+)/);if(!match)await delay(250);}
 device('forward','tcp:9223',`localabstract:${match[1]}`);
 const targets=()=>fetch('http://127.0.0.1:9223/json').then(r=>r.json());
 const original=(await targets())[0];const main=await connect(original);let confirmation;
 const list=()=>device('shell','run-as','com.edcam','ls','files/photos').split(/\s+/).filter(n=>n.endsWith('.json'));
 try {
  await delay(2500);const before=list();
  await main.tap(`document.querySelector('sakuga-button[label="Flash tắt"]').shadowRoot.querySelector('button')`);
  await main.tap(`document.querySelector('sakuga-button[label="Chụp ảnh ngay"]').shadowRoot.querySelector('button')`);
  let created;for(let n=0;n<20&&!created;n++){await delay(500);created=list().find(n=>!before.includes(n));}
  assert(created,'Real emulator camera capture creates metadata');
  const meta=JSON.parse(device('shell','run-as','com.edcam','cat',`files/photos/${created}`));
  assert.equal(meta.source,'camera');assert.equal(meta.styleId,62);assert.equal(meta.strength,.5);assert(meta.hasOriginal);
  shot('captured');
  await main.tap(`document.querySelector('sakuga-button[label="Album ảnh"]').shadowRoot.querySelector('button')`);
  await delay(2500);device('shell','input','tap','180','970');await delay(1500);
  device('shell','input','swipe','350','920','350','400','600');await delay(900);
  assert(await main.evaluate('!!document.querySelector("sakuga-rating")'));
  await main.tap(`document.querySelector('sakuga-rating').shadowRoot.querySelectorAll('button')[3]`);
  assert(device('shell','run-as','com.edcam','cat','shared_prefs/edcam.xml').includes(`name="rating-${meta.id}" value="4"`));
  shot('photo-rating');
  device('shell','input','swipe','350','950','350','350','600');await delay(900);
  await main.tap(`document.querySelector('sakuga-button[label="Xóa khỏi album Ed-Cam"]').shadowRoot.querySelector('button')`);
  await delay(1500);const views=await targets();confirmation=await connect(views.find(v=>v.id!==original.id));
  assert.equal(await confirmation.evaluate('document.querySelectorAll("sakuga-button").length'),2);
  shot('delete-confirm');
  await confirmation.tap(`document.querySelector('sakuga-button[label="Xóa ảnh"]').shadowRoot.querySelector('button')`);
  await delay(1500);assert(!list().includes(created));
  assert.deepEqual(list().sort(),before.sort(),'Only the photo made in this test was removed');
  fs.writeFileSync('qa/v120-capture-results.json',JSON.stringify({capture:true,metadata:meta,privateRating:true,sakugaConfirmation:true,deleteOnlyTestPhoto:true},null,2));
  console.log('PASS: real emulator capture, style metadata, private rating and Sakuga delete confirmation.');
 }finally{main.ws.close();confirmation?.ws.close();}
})().catch(e=>{console.error(e);process.exit(1)});
