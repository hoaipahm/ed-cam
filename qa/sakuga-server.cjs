const http = require('node:http');
const fs = require('node:fs');
const path = require('node:path');
const root = path.resolve(__dirname, '../app/src/main/assets');
const allowed = new Set(['sakuga.html', 'tabs.js', 'sakuga-host.js', 'sakuga-host.css']);
http.createServer((request, response) => {
  const name = new URL(request.url, 'http://127.0.0.1').pathname.slice(1) || 'sakuga.html';
  if (!allowed.has(name)) { response.writeHead(404); response.end(); return; }
  const type = name.endsWith('.js') ? 'text/javascript' : name.endsWith('.css') ? 'text/css' : 'text/html';
  response.setHeader('Content-Type', `${type}; charset=utf-8`);
  fs.createReadStream(path.join(root, name)).pipe(response);
}).listen(8876, '127.0.0.1', () => process.stdout.write('http://127.0.0.1:8876/sakuga.html\n'));
