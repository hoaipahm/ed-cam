(async () => {
 const targets = await (await fetch('http://127.0.0.1:9223/json')).json();
 const ws = new WebSocket(targets[0].webSocketDebuggerUrl);
 ws.onopen = () => ws.send(JSON.stringify({id:1,method:'Runtime.evaluate',params:{expression:process.argv[2] || `JSON.stringify({body:document.body.innerHTML,defined:!!customElements.get('sakuga-tabs'),configure:typeof configureTabs})`,returnByValue:true}}));
 ws.onmessage = e => { console.log(e.data); ws.close(); };
})();
