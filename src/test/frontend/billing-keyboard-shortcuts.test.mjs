import test from 'node:test';
import assert from 'node:assert/strict';
import {installBillingKeyboardShortcuts as install} from '../../main/frontend/billing-keyboard-shortcuts.js';

const element = (tagName, extra = {}) => ({tagName, isConnected: true, getAttribute: () => null, ...extra});
function fixture() {
  const doc = new EventTarget(), host = new EventTarget();host.isConnected = true;
  doc.body=element('BODY');doc.documentElement=element('HTML');doc.defaultView=new EventTarget();
  let clock=100, openings=0, modal=false, dropdown=false, changed=()=>{};
  let sequence=0;const timers=new Map();
  doc.querySelector=selector=>selector.includes('dialog') ? modal : dropdown;
  host.addEventListener('cc-pos-open-payment',()=>openings++);
  const options={windowMillis:450,document:doc,now:()=>clock,
    schedule:(callback,ms)=>{timers.set(++sequence,{callback,at:clock+ms});return sequence;},cancel:id=>timers.delete(id),
    observe:callback=>{changed=callback;return {observe(){},disconnect(){}};}};
  const api=install(host,options);
  const emit=(type,node,extra={})=>{const event=new Event(type,{cancelable:true});Object.assign(event,{key:'Enter',code:'Enter',repeat:false,composedPath:()=>node===doc.body?[node,doc]:[node,host,doc],...extra});(type==='keydown'||type==='keyup'?doc.defaultView:doc).dispatchEvent(event);return event;};
  const press=(node,extra={})=>{const event=emit('keydown',node,extra);emit('keyup',node,extra);return event;};
  const advance=ms=>{clock+=ms;for(const [id,t] of [...timers])if(t.at<=clock){timers.delete(id);t.callback();}};
  return {doc,host,api,options,emit,press,advance,element,changed:()=>changed(),openings:()=>openings,
    modal:value=>{modal=value;changed();},dropdown:value=>{dropdown=value;changed();}};
}

for(const control of ['DIV','VAADIN-GRID','TR','TD','SPAN','LABEL','VAADIN-ICON','H2','BODY','VAADIN-GRID-CELL-CONTENT']) {
  test(`mouse then double Enter on ${control}`,()=>{const f=fixture(),node=control==='BODY'?f.doc.body:element(control);f.emit('pointerdown',node);f.emit('focusin',node);f.press(node);assert.equal(f.openings(),0);f.advance(100);f.press(node);assert.equal(f.openings(),1);});
}
test('normal and numpad Enter use the same action; pending suppresses triple/repeated pairs',()=>{const f=fixture(),n=element('DIV');f.press(n,{key:'Enter',code:'NumpadEnter'});f.advance(440);f.press(n,{key:'Enter',code:'NumpadEnter'});assert.equal(f.press(n).defaultPrevented,true);f.press(n);assert.equal(f.openings(),1);});
test('expired Enter requires a fresh pair',()=>{const f=fixture(),n=element('DIV');f.press(n);f.advance(451);f.press(n);assert.equal(f.openings(),0);f.advance(50);f.press(n);assert.equal(f.openings(),1);});
test('the configured 450ms boundary is inclusive',()=>{const f=fixture(),n=element('DIV');f.press(n);f.advance(450);f.press(n);assert.equal(f.openings(),1);});
test('held key, repeat and missing release do not create a physical second press',()=>{const f=fixture(),n=element('DIV');f.emit('keydown',n);f.emit('keydown',n,{repeat:true});f.emit('keydown',n);assert.equal(f.openings(),0);f.emit('keyup',n);f.press(n);assert.equal(f.openings(),1);});
for(const control of ['INPUT','TEXTAREA','SELECT','VAADIN-BIG-DECIMAL-FIELD','VAADIN-TEXT-FIELD','VAADIN-INTEGER-FIELD']) {
  test(`editable ${control} retains Enter and resets a previous eligible press`,()=>{const f=fixture(),n=element(control),plain=element('DIV');f.press(plain);assert.equal(f.press(n).defaultPrevented,false);f.press(plain);assert.equal(f.openings(),0);f.press(plain);assert.equal(f.openings(),1);});
}
test('contenteditable retains Enter',()=>{const f=fixture(),n=element('DIV',{isContentEditable:true});f.press(n);f.press(n);assert.equal(f.openings(),0);});
test('read-only input does not disable checkout',()=>{const f=fixture(),n=element('INPUT',{readOnly:true});f.press(n);f.press(n);assert.equal(f.openings(),1);});
test('autocomplete selection is never the first Enter of checkout, then idle search works',()=>{const f=fixture(),n=element('VAADIN-COMBO-BOX',{opened:true,filter:'coca',classList:{contains:()=>true}});f.press(n);n.opened=false;n.filter='';f.changed();f.press(n);assert.equal(f.openings(),0);f.press(n);assert.equal(f.openings(),1);});
test('ordinary combo retains Enter even when closed',()=>{const f=fixture(),n=element('VAADIN-COMBO-BOX',{opened:false,classList:{contains:()=>false}});f.press(n);f.press(n);assert.equal(f.openings(),0);});
test('dropdown/modal clear a pending first Enter and restore a fresh pair after closing',()=>{for(const kind of ['modal','dropdown']){const f=fixture(),n=element('DIV');f.press(n);f[kind](true);f.press(n);assert.equal(f.openings(),0);f[kind](false);f.press(n);assert.equal(f.openings(),0);f.press(n);assert.equal(f.openings(),1);}});
test('cancel checkout and reset permits reopening with another independent pair',()=>{const f=fixture(),n=element('DIV');f.press(n);f.press(n);f.modal(true);f.api.reset();f.modal(false);f.press(n);f.press(n);assert.equal(f.openings(),2);});
for(const interaction of ['pointerdown','focusin','input','change','wheel'])test(`${interaction} between enters invalidates the pair`,()=>{const f=fixture(),n=element('DIV');f.press(n);f.emit(interaction,n);f.press(n);assert.equal(f.openings(),0);f.press(n);assert.equal(f.openings(),1);});
test('focused button: double Enter performs checkout and zero native button actions',()=>{const f=fixture();let clicks=0;const n=element('VAADIN-BUTTON',{click:()=>clicks++});f.doc.activeElement=n;assert.equal(f.press(n).defaultPrevented,true);f.advance(100);f.press(n);f.advance(500);assert.equal(clicks,0);assert.equal(f.openings(),1);});
test('focused button: single Enter still activates once after the recognition window',()=>{const f=fixture();let clicks=0;const n=element('BUTTON',{click:()=>clicks++});f.doc.activeElement=n;f.press(n);f.advance(451);assert.equal(clicks,1);assert.equal(f.openings(),0);});
test('ARIA button preserves single Enter activation',()=>{const f=fixture();let clicks=0;const n=element('DIV',{getAttribute:name=>name==='role'?'button':null,click:()=>clicks++});f.doc.activeElement=n;f.press(n);f.advance(451);assert.equal(clicks,1);});
test('ARIA readonly textbox is an eligible noneditable context',()=>{const f=fixture();const n=element('DIV',{getAttribute:name=>name==='role'?'textbox':name==='aria-readonly'?'true':null});f.press(n);f.press(n);assert.equal(f.openings(),1);});
test('Vaadin shadow button activates its focused outer host only once',()=>{const f=fixture();let clicks=0;const outer=element('VAADIN-BUTTON',{click:()=>clicks++}),inner=element('BUTTON'),icon=element('VAADIN-ICON');f.doc.activeElement=outer;f.press(icon,{composedPath:()=>[icon,inner,outer,f.host,f.doc]});f.advance(451);assert.equal(clicks,1);});
test('outside route and modifier/IME events do not trigger checkout',()=>{const f=fixture(),n=element('DIV');f.press(n,{composedPath:()=>[n,f.doc]});f.press(n,{isComposing:true});f.press(n,{ctrlKey:true});assert.equal(f.openings(),0);});
test('remount replaces the previous listener; dispose and detached route stop all activity',()=>{const f=fixture(),n=element('DIV');const second=install(f.host,f.options);f.press(n);f.press(n);assert.equal(f.openings(),1);second.dispose();f.press(n);f.press(n);assert.equal(f.openings(),1);install(f.host,f.options);f.host.isConnected=false;f.changed();f.press(n);f.press(n);assert.equal(f.openings(),1);});
