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
 let match; for(let n=0;n<40&&!match;n++){match=device('shell','cat','/proc/net/unix').match(/@(webview_devtools_remote_\d+)/);if(!match) await delay(250);} assert(match,'WebView socket');const socket=match[1];device('forward','tcp:9223',`localabstract:${socket}`);
 const targets=()=>fetch('http://127.0.0.1:9223/json').then(r=>r.json());
 let main=await connect((await targets())[0]), dialog;
 try {
   await delay(800);shot('camera');
   console.log(await main.evaluate('JSON.stringify([...document.querySelectorAll(".control-slot")].map(el=>({kind:el.className,text:el.textContent})))'));
   await main.tap(`document.querySelector('#camera-tabs').shadowRoot.querySelectorAll('button')[0]`);
   await delay(1000);
   await main.tap(`document.querySelector('sakuga-input').shadowRoot.querySelector('input')`);
   await main.send('Input.insertText',{text:'Cyanotype'});await delay(1200);
   shot('styles-search');assert(nativeUI().includes('Cyanotype'));
   device('shell','input','keyevent','4');await delay(300);tapText('Cyanotype');await delay(3500);shot('cyanotype');
   await main.tap(`document.querySelector('#camera-tabs').shadowRoot.querySelectorAll('button')[2]`);
   await delay(2400);
   const views=await targets();console.log('WebViews:',views.length);assert(views.length===2);
   dialog=await connect(views.find(t=>t.id!== (views.find(t=>t.webSocketDebuggerUrl===main.ws.url)?.id))||views[0]);
   // Identify the dialog by its slider rather than depending on endpoint order.
   if(!await dialog.evaluate('!!document.querySelector("sakuga-slider")')) {dialog.ws.close();dialog=await connect(views.find(t=>t.webSocketDebuggerUrl!==dialog.ws.url));}
   assert(await dialog.evaluate('!!document.querySelector("sakuga-slider")'));
   await dialog.tap(`document.querySelector('sakuga-slider').shadowRoot.querySelector('input')`);
   await dialog.send('Input.dispatchKeyEvent',{type:'keyDown',key:'Home',code:'Home',windowsVirtualKeyCode:36});
   await dialog.send('Input.dispatchKeyEvent',{type:'keyUp',key:'Home',code:'Home',windowsVirtualKeyCode:36});
   await delay(700);shot('intensity');
   await dialog.tap(`document.querySelector('sakuga-button').shadowRoot.querySelector('button')`);
   await delay(800);
   await main.tap(`document.querySelector('sakuga-button[label="Cài đặt"]').shadowRoot.querySelector('button')`);
   await delay(900);shot('settings');
   assert.equal(await main.evaluate('document.querySelector("sakuga-radio").shadowRoot.querySelectorAll("label").length'),5);
   device('shell','input','swipe','550','930','550','350','700');
   await delay(1300); shot('settings-scrolled');
   assert(await main.evaluate('document.querySelectorAll("sakuga-toggle").length>=1'));
   const pref=device('shell','run-as','com.edcam','cat','shared_prefs/edcam.xml');
   assert(pref.includes('name="style" value="62"'));
   assert(pref.includes('name="strength" value="0.5"'));
   fs.writeFileSync('qa/v120-device-results.json',JSON.stringify({camera:true,search:true,nativeSelection:true,intensity:true,settings:true},null,2));
   console.log('PASS: Android touch camera, search bridge, native style selection, intensity dialog and settings.');
 } finally {main.ws.close();dialog?.ws.close();}
})().catch(e=>{console.error(e);process.exit(1)});
