const {readFileSync} = require('node:fs');
const {resolve} = require('node:path');
const vm = require('node:vm');
const assert = require('node:assert/strict');
const {test} = require('node:test');
const html = readFileSync(resolve(__dirname, '../composeApp/src/webMain/resources/index.html'), 'utf8');
const source = html.slice(html.indexOf('        function openGoogleProcurementRefresh('), html.indexOf('        function openProcurementWorkbookUpload('));

function setup(blocked = false) {
    const calls = [], results = [];
    let listener, timer;
    const popup = {closed:false, location:{}, close() { this.closed = true; }};
    const context = vm.createContext({
        document:{documentElement:{lang:'en'}},
        window:{location:{origin:'https://ua-oms.com'}, open:()=>blocked ? null : popup,
            addEventListener:(_, f)=>listener=f, removeEventListener:()=>{listener=null;}},
        setInterval:f=>{timer=f; return 1;}, clearInterval:()=>{timer=null;},
        showOmsLoading:()=>{}, hideOmsLoading:()=>{}, omsText:x=>x, omsApiUrl:x=>x,
        omsAuthenticatedFetch:async (url, options)=>{
            calls.push({url,options});
            return {ok:true,json:async()=>({url:'https://accounts.google.com/test'}),text:async()=>'\{"id":"job-1","status":"RUNNING"\}'};
        }
    });
    vm.runInContext(source, context);
    context.openGoogleProcurementRefresh((...args)=>results.push(args));
    return {calls,results,popup,receive:()=>listener,tick:()=>timer?.()};
}
const settled = () => new Promise(resolve=>setImmediate(resolve));

test('rejects messages from another origin or popup before starting import', async()=>{
    const app=setup(); await settled();
    assert.equal(app.popup.location.href,'https://accounts.google.com/test');
    const receive=app.receive();
    await receive({origin:'https://evil.test',source:app.popup,data:{type:'oms-google-drive',ticket:'forged'}});
    await receive({origin:'https://ua-oms.com',source:{},data:{type:'oms-google-drive',ticket:'forged'}});
    assert.equal(app.calls.length,1);
    await receive({origin:'https://ua-oms.com',source:app.popup,data:{type:'oms-google-drive',ticket:'valid'}});
    assert.equal(app.calls.length,2);
    assert.equal(app.calls[1].url,'/procurements/refresh/google');
    assert.equal(JSON.parse(app.calls[1].options.body).ticket,'valid');
    assert.equal(app.results.length,1);
    assert.equal(app.results[0][0],'');
    assert.equal(app.receive(),null);
});
test('closing authorization never starts an import', async()=>{
    const app=setup(); await settled(); app.popup.closed=true; app.tick();
    assert.equal(app.calls.length,1);
    assert.match(app.results[0][0],/cancelled/);
    assert.equal(app.receive(),null);
});
test('blocked popup reports an error without contacting the server',()=>{
    const app=setup(true); assert.equal(app.calls.length,0); assert.match(app.results[0][0],/popups/);
});
test('denied Google consent never starts an import',async()=>{
    const app=setup(); await settled();
    await app.receive()({origin:'https://ua-oms.com',source:app.popup,data:{type:'oms-google-drive',error:'Denied'}});
    assert.equal(app.calls.length,1); assert.equal(app.results[0][0],'Denied');
});
