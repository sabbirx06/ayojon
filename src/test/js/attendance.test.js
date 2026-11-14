import {test} from 'node:test';
import assert from 'node:assert/strict';
import {Window} from 'happy-dom';

const tick = async () => { for (let i=0;i<12;i++) await new Promise(resolve => setImmediate(resolve)); };
const choices = [{id:42,title:'Community <music>',venueName:'Town Hall',startTime:'2026-11-12T18:00:00'}];
async function setup(handler) {
  const window = new Window();
  globalThis.document = window.document;
  globalThis.Event = window.Event;
  document.body.innerHTML = '<div id="mount"></div>';
  const calls = [];
  const request = async (path, options) => {
    calls.push([path, options]);
    return handler ? handler(path, options) : path.endsWith('/events') ? choices : path.endsWith('/me') ? [] : {checkedIn:false};
  };
  const {createAttendance} = await import('../../main/resources/static/assets/js/attendance.js');
  let user = {id:7};
  const ui = createAttendance(document.querySelector('#mount'), {request,getUser:()=>user});
  return {ui,calls,logout:()=>{user=null;ui.clear();}, $:s=>document.querySelector(s),window};
}

test('check-in posts selected ID once, disables pending, and refreshes history', async () => {
  let resolvePost;
  let recorded = false;
  const {$,ui,calls} = await setup((path, options) => {
    if (options?.method === 'POST') return new Promise(resolve => { resolvePost = () => {recorded=true;resolve({eventId:42});}; });
    if (path.endsWith('/events')) return choices;
    if (path.endsWith('/me')) return recorded ? [{eventId:42,checkedInAt:'2026-11-12T18:01:00'}] : [];
    return {checkedIn:recorded};
  });
  ui.activate('profile'); await tick();
  $('select').value='42'; $('select').dispatchEvent(new Event('change')); await tick();
  $('[data-check-in]').click();
  assert.equal($('[data-check-in]').disabled,true);
  $('[data-check-in]').click(); await tick();
  assert.equal(calls.filter(([,o])=>o?.method==='POST').length,1);
  assert.deepEqual(JSON.parse(calls.find(([,o])=>o?.method==='POST')[1].body),{eventId:42});
  resolvePost(); await tick();
  assert.equal($('[data-check-in]').textContent,'Checked in');
  assert.match($('[data-feedback]').textContent,/Check-in recorded/);
  assert.match($('[data-history]').textContent,/Community <music>/);
});

test('failed check-in restores a usable retry label', async () => {
 const {$,ui} = await setup((path, options) => {
  if (options?.method === 'POST') throw new Error('Network unavailable');
  return path.endsWith('/events') ? choices : path.endsWith('/me') ? [] : {checkedIn:false};
 });
 await ui.activate('profile');
 $('select').value='42'; $('select').dispatchEvent(new Event('change')); await tick();
 $('[data-check-in]').click(); await tick();
 assert.equal($('[data-check-in]').disabled,false);
 assert.equal($('[data-check-in]').textContent,'Check in');
 assert.match($('[data-feedback]').textContent,/Network unavailable/);
});

test('duplicate check-in marks attendance complete and refreshes history without retrying', async () => {
  let recorded = false;
  const {$,ui,calls} = await setup((path, options) => {
    if (options?.method === 'POST') {
      recorded = true;
      throw Object.assign(new Error('Conflict'), {status:409});
    }
    if (path.endsWith('/events')) return choices;
    if (path.endsWith('/me')) return recorded ? [{eventId:42,checkedInAt:'2026-11-12T18:01:00'}] : [];
    return {checkedIn:recorded};
  });
  await ui.activate('profile');
  $('select').value = '42'; $('select').dispatchEvent(new Event('change')); await tick();
  $('[data-check-in]').click(); await tick();
  assert.equal($('[data-check-in]').disabled, true);
  assert.equal($('[data-check-in]').textContent, 'Checked in');
  assert.match($('[data-feedback]').textContent, /Already checked in/);
  assert.match($('[data-history]').textContent, /Community <music>/);
  assert.equal(calls.filter(([path]) => path.endsWith('/me')).length, 2);
  assert.equal($('select').disabled, false);
  $('[data-check-in]').click(); await tick();
  assert.equal(calls.filter(([,options]) => options?.method === 'POST').length, 1);
});

test('late duplicate response after logout cannot restore attendance content', async () => {
  let rejectPost;
  const {$,ui,logout,calls} = await setup((path, options) => {
    if (options?.method === 'POST') return new Promise((resolve, reject) => { rejectPost = reject; });
    return path.endsWith('/events') ? choices : path.endsWith('/me') ? [] : {checkedIn:false};
  });
  await ui.activate('profile');
  $('select').value = '42'; $('select').dispatchEvent(new Event('change')); await tick();
  $('[data-check-in]').click(); await tick();
  logout();
  rejectPost(Object.assign(new Error('Conflict'), {status:409})); await tick();
  assert.equal($('#mount').textContent, '');
  assert.equal(calls.filter(([path]) => path.endsWith('/me')).length, 1);
});

test('history failure has its own error and event-list failure does not suppress history', async () => {
 let state = await setup(path => {
  if (path.endsWith('/me')) throw new Error('History unavailable');
  return choices;
 });
 await state.ui.activate('profile');
 assert.match(state.$('[data-history]').textContent,/History unavailable/);
 state = await setup(path => {
  if (path.endsWith('/events')) throw new Error('Events unavailable');
  return [{eventId:42,checkedInAt:'2026-11-12T18:01:00'}];
 });
 await state.ui.activate('profile');
 assert.match(state.$('[data-history]').textContent,/Event #42/);
});

test('profile loads real published choices, empty history and status before enabling check-in', async () => {
  const {$,ui,calls} = await setup();
  ui.activate('profile');
  assert.equal($('[data-check-in]').disabled,true);
  await tick();
  assert.match($('[data-history]').textContent,/No check-ins yet/);
  assert.equal($('select option[value="42"]').textContent.includes('Community <music>'),true);
  assert.equal($('select music'),null);
  $('select').value='42';
  $('select').dispatchEvent(new Event('change'));
  assert.equal($('[data-check-in]').disabled,true);
  await tick();
  assert.equal($('[data-check-in]').disabled,false);
  assert.equal(calls.some(([p])=>p==='/api/attendance/events/42/status'),true);
});

test('UTC check-in history converts to BDT across midnight while local event times stay unchanged', async () => {
  const {$,ui} = await setup(path => path.endsWith('/events') ? choices : [
    {eventId:42,checkedInAt:'2026-11-12T18:01:00'},
    {eventId:42,checkedInAt:null}
  ]);
  await ui.activate('profile');
  assert.match($('[data-history]').textContent, /13 Nov 2026, 00:01:00 BDT/);
  assert.match($('[data-history]').textContent, /Time unavailable/);
  assert.match($('select option[value="42"]').textContent, /2026-11-12 18:00:00 BDT/);
});
