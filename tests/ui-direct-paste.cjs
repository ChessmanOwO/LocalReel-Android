// Node test of the actual app.js event handlers with a small DOM/bridge fixture.
// This does not replace the Android WebView and live-network instrumentation tests.
const assert=require('node:assert/strict');
const fs=require('node:fs');
const path=require('node:path');
const vm=require('node:vm');
const source=fs.readFileSync(path.join(__dirname,'../app/src/main/assets/app.js'),'utf8');
let assertions=0;
function check(value,message){assert.ok(value,message);assertions++;}
function fixture(){
 const timers=new Map(),listeners={},calls=[];let nextTimer=1,doc;
 class Element{
  constructor(){this.hidden=false;this.dataset={};this.value='';this.checked=false;this.disabled=false;this.scrollTop=0;this.isConnected=true;this.attrs={};}
  focus(){doc.activeElement=this;}
  setAttribute(k,v){this.attrs[k]=v;}
  removeAttribute(k){delete this.attrs[k];}
  setSelectionRange(){}
 }
 const overlay=new Element();overlay.hidden=true;
 const toast=new Element(),nav=new Element(),topbar=new Element(),heading=new Element();
 let field=null,ask=null,error=null,buttons=[];
 const content=new Element();
 Object.defineProperty(content,'innerHTML',{set(value){
  this.html=value;
  if(value.includes('id="url"')){
   field=new Element();field.id='url';ask=new Element();ask.id='ask';ask.checked=/id="ask"[^>]*checked/.test(value);error=new Element();error.hidden=true;
   buttons=['paste','submit'].map(action=>{const b=new Element();b.dataset.action=action;return b;});
  }else{field=null;ask=null;error=null;buttons=[];}
 }});
 content.contains=e=>e===field||buttons.includes(e);
 content.querySelector=q=>q==='textarea'?field:q==='#ask'?ask:q==='#input-error'?error:q==='h2'?heading:null;
 content.querySelectorAll=q=>buttons;
 const phone=new Element();
 phone.querySelector=q=>q==='.nav'?nav:q==='.topbar'?topbar:q==='.toast'?toast:q==='.overlay'?overlay:q==='.overlay:not([hidden])'?overlay.hidden?null:overlay:null;
 doc={activeElement:null,querySelector:q=>q==='.phone'?phone:q==='.content'?content:null,addEventListener:(name,fn)=>listeners[name]=fn,body:{classList:{toggle(){}}}};
 const sandbox={document:doc,URL,console,LocalReel:{submit:(text,quality)=>calls.push({text,quality}),paste:()=>calls.push({paste:true}),ready(){},action(){},theme(){},limits(){}},setTimeout(fn){const id=nextTimer++;timers.set(id,fn);return id;},clearTimeout(id){timers.delete(id);}};
 sandbox.window=sandbox;vm.createContext(sandbox);vm.runInContext(source,sandbox);
 return {ctx:sandbox,calls,get field(){return field;},get ask(){return ask;},get error(){return error;},get buttons(){return buttons;},event:(name,target)=>listeners[name]({target}),flush(){const pending=[...timers.values()];timers.clear();for(const fn of pending)fn();},click(action){const b=buttons.find(b=>b.dataset.action===action)||new Element();b.dataset.action=action;b.closest=()=>b;listeners.click({target:b});},html:()=>content.html};
}
const url='https://youtu.be/jNQXAC9IVRw';
{
 const f=fixture();check(!f.ask.checked,'quality defaults to automatic best');
 f.ctx.prefill(url);f.flush();check(f.calls.length===0,'Android share prefill does not start an unsolicited download');
 f.ctx.prefill(url,true);f.flush();check(f.calls.length===1&&f.calls[0].text===url&&f.calls[0].quality===false,'native paste callback immediately submits the valid URL');
 f.ctx.prefill(url,true);f.click('submit');f.flush();check(f.calls.length===1,'paste/button double taps are locked while awaiting native acknowledgement');
 f.ctx.inputError('try again');f.click('submit');check(f.calls.length===2,'native submission errors release the lock for retry');
}
{
 const f=fixture();f.event('paste',f.field);f.field.value=url;f.flush();check(f.calls.length===1,'system keyboard paste reads the post-default textarea value');
}
{
 const f=fixture();f.event('paste',f.field);f.event('paste',f.field);f.field.value=url;f.flush();check(f.calls.length===1,'multiple queued paste events are coalesced');
}
{
 const f=fixture();f.field.value=url;f.event('input',f.field);f.flush();check(f.calls.length===0,'typing alone never triggers an automatic download');
 f.click('submit');check(f.calls.length===1,'typed URL retains an explicit submit button');
}
{
 const f=fixture();f.ask.checked=true;f.event('change',f.ask);f.ctx.prefill(url,true);f.flush();check(f.calls[0].quality===true,'explicit quality selection is retained when pasting');
}
for(const bad of ['not a URL','https://youtube.com.attacker.test/watch?v=x','http://youtu.be/jNQXAC9IVRw','https://user:pass@youtube.com/watch?v=x']){
 const f=fixture();f.ctx.prefill(bad,true);f.flush();check(f.calls.length===0&&!f.error.hidden,'invalid automatic paste shows an inline error without invoking the downloader');
}
{
 const f=fixture();f.ctx.prefill(url,true);f.flush();f.ctx.submitted('new-job');f.flush();check(f.calls.length===1,'native acknowledgement navigates without resubmitting');
 check(!f.html().includes('Mở bài gốc')&&!source.includes("action('source'"),'source-page buttons have been removed');
}
console.log(`PASS: ${assertions} direct-paste UI assertions (Node; Android runtime tested separately).`);
