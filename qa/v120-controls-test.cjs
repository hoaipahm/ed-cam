const { chromium } = require('../.tools/browser/node_modules/playwright');
const fs = require('node:fs');
const assert = require('node:assert/strict');
(async () => {
  const browser=await chromium.launch({executablePath:process.env.CHROME,headless:true});
  const results=[];
  try {
    for (const width of [390,1280]) {
      const context=await browser.newContext({viewport:{width,height:1000},hasTouch:true});
      const page=await context.newPage(), errors=[];
      page.on('pageerror',e=>errors.push(e.message));
      await page.addInitScript(() => {
        window.calls=[];
        window.AndroidTabs={ready(){},bounds(){},control(epoch,id,value){calls.push({epoch,id,value});},rest(){},toastBounds(){}};
      });
      await page.goto('http://127.0.0.1:8876/sakuga.html');
      await page.waitForFunction(()=>!!window.configureTabs);
      await page.evaluate(width=> {
        const nodes=[
          ['button','Lưu ảnh','', [16,30,170,60]],
          ['toggle','Giữ bản gốc','false',[230,30,90,100]],
          ['slider','Cường độ','50',[20,150,320,76]],
          ['input','Tên nơi chụp','',[20,255,320,70]],
          ['checkbox','Yêu thích ảnh','false',[20,345,60,60]],
          ['radio','Giao diện','a',[20,420,320,120]],
          ['rating','Đánh giá ảnh','0',[20,560,260,60]],
          ['progress','Đang xử lý','0.33',[20,650,320,230]],
          ['tooltip','Màu gốc không bị ghi đè.','',[280,355,60,60]]
        ].map(([kind,label,value,rect],i)=>({id:i+1,kind,label,value,rect,clip:[rect[0],rect[1],rect[0]+rect[2],rect[1]+rect[3]],options:kind==='radio'?[{value:'a',label:'Film kem'},{value:'b',label:'Đêm tối'}]:undefined}));
        window.testConfig={mode:'none',epoch:7,viewport:width,controls:nodes};
        configureTabs(testConfig);
      },width);
      const client=await context.newCDPSession(page);
      const press=async locator=>{
        const b=await locator.boundingBox(); assert(b);
        await client.send('Input.dispatchTouchEvent',{type:'touchStart',touchPoints:[{x:b.x+b.width/2,y:b.y+b.height/2}]});
        await client.send('Input.dispatchTouchEvent',{type:'touchEnd',touchPoints:[]});
      };
      await press(page.locator('sakuga-button').locator('button'));
      await press(page.locator('sakuga-toggle').locator('button'));
      await page.waitForFunction(()=>calls.some(c=>c.id===2&&c.value==='true'));
      await page.locator('sakuga-input').locator('input').fill('Đà Lạt');
      await page.locator('sakuga-checkbox').locator('label').click();
      await page.locator('sakuga-radio').locator('label').nth(1).click();
      await press(page.locator('sakuga-rating').locator('button').nth(3));
      const slider=page.locator('sakuga-slider').locator('input');
      await slider.focus(); await page.keyboard.press('ArrowRight');
      await page.keyboard.press('End');
      await page.locator('sakuga-tooltip').locator('button').click();
      await page.evaluate(()=>edcamToast('Đã lưu ảnh vào album Ed-Cam.'));
      await page.waitForTimeout(1200);
      const calls=await page.evaluate(()=>window.calls);
      for (const id of [1,2,3,4,5,6,7]) assert(calls.some(c=>c.id===id),`Bridge control ${id}`);
      assert(calls.some(c=>c.id===3&&Number(c.value)>50));
      assert(calls.some(c=>c.id===4&&c.value==='Đà Lạt'));
      assert.equal(await page.evaluate(()=>new FormData(document.querySelector('#controls-host')).get('control-4')),'Đà Lạt');
      assert.equal(await page.evaluate(()=>document.documentElement.scrollWidth),width);
      await page.screenshot({path:`qa/v120-controls-${width}.png`});
      await page.emulateMedia({reducedMotion:'reduce'});
      await page.reload();
      await page.evaluate(width=>configureTabs({mode:'none',epoch:8,viewport:width,controls:[{id:10,kind:'toggle',label:'GPS',value:'false',rect:[20,80,120,100],clip:[20,80,140,180]}]}),width);
      await press(page.locator('sakuga-toggle').locator('button'));
      assert(await page.locator('sakuga-toggle').evaluate(el=>el.checked));
      assert.equal(await page.locator('canvas.sakuga-fx').count(),0);
      assert.deepEqual(errors,[]);
      results.push({width,bridgeControls:7,forms:true,reducedMotion:true,errors});
      await context.close();
    }
    fs.writeFileSync('qa/v120-controls-results.json',JSON.stringify(results,null,2));
    console.log('PASS: gallery controls, native bridge, touch, keyboard, forms and reduced motion at 390/1280.');
  } finally { await browser.close(); }
})().catch(e=>{console.error(e);process.exit(1)});
