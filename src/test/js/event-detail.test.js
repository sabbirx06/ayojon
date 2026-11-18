import {test, beforeEach} from 'node:test';
import assert from 'node:assert/strict';
import {Window} from 'happy-dom';

// event-detail.js reads the page when it loads, so one page and one module are shared by all tests.
const window = new Window({url:'http://localhost/'});
globalThis.document = window.document;
globalThis.CustomEvent = window.CustomEvent;
document.body.innerHTML = `
  <img id="event-poster" src="http://localhost/default.jpg">
  <a id="event-back"></a>
  <div id="event-detail-feedback"></div>
  <div id="event-detail-content" hidden>
    <span id="event-category"></span><h1 id="event-title"></h1>
    <span id="event-organizer"></span><span id="event-verified"></span>
    <p id="event-description"></p><p id="event-schedule"></p>
    <p id="event-venue"></p><p id="event-address"></p><p id="event-price"></p>
    <a id="event-ticket-link"></a><p id="event-ticket-note"></p>
  </div>`;

// Each request waits until the test answers it, so the test controls the order responses arrive in.
let requests = [];
globalThis.fetch = (url, options = {}) => new Promise((resolve, reject) => {
  const request = {url, signal:options.signal, answer(status, body) {
    resolve({ok:status < 400, status, json:async () => body});
  }};
  options.signal?.addEventListener('abort', () => reject(Object.assign(new Error('aborted'), {name:'AbortError'})));
  requests.push(request);
});
await import('../../main/resources/static/assets/js/event-detail.js');

const tick = async () => { for (let i = 0; i < 10; i++) await new Promise(resolve => setImmediate(resolve)); };
const $ = selector => document.querySelector(selector);
const shown = () => $('#event-detail-content').hidden ? $('#event-detail-feedback').textContent : $('#event-title').textContent;
const event = (id, title) => ({id, title, description:'About', category:'Music', area:'Gulshan',
  venueName:'Hall', venueAddress:'Road 1, Dhaka', startTime:'2026-11-20T15:00:00', endTime:'2026-11-20T19:00:00',
  price:0, posterUrl:null, ticketUrl:null, verified:true, organizerName:'Org'});

// The router sets the page title, then announces the new view (see route() in app.js).
function navigate(view, query = '', title = view) {
  document.title = `${title} — Ayojon`;
  document.dispatchEvent(new CustomEvent('ayojon:viewchange',
    {detail:{name:view, view, params:new URLSearchParams(query)}}));
}

beforeEach(() => { requests = []; navigate('discover', '', 'Discover'); });

test('shows the published event that was opened', async () => {
  navigate('event-detail', 'id=3', 'Event details');
  assert.equal(requests[0].url, '/api/events/3');
  requests[0].answer(200, event(3, 'Jazz Night')); await tick();
  assert.equal(shown(), 'Jazz Night');
  assert.equal(document.title, 'Jazz Night — Ayojon');
});

test('a late response after leaving the page changes neither that page nor its title', async () => {
  navigate('event-detail', 'id=3', 'Event details');
  const slow = requests[0];
  navigate('profile', '', 'Profile');
  assert.equal(slow.signal.aborted, true, 'leaving the page cancels the request');
  const before = document.body.innerHTML;
  slow.answer(200, event(3, 'Late Event')); await tick();
  assert.equal(document.title, 'Profile — Ayojon');
  assert.equal(document.body.innerHTML, before, 'nothing on the page changed');
});

test('opening a missing id cancels the earlier event, which can no longer replace the message', async () => {
  navigate('event-detail', 'id=3', 'Event details');
  const slow = requests[0];
  navigate('event-detail', '', 'Event details');
  assert.equal(slow.signal.aborted, true);
  slow.answer(200, event(3, 'Jazz Night')); await tick();
  assert.match(shown(), /Choose an event to see its details/);
  assert.equal(document.title, 'Event details — Ayojon');
});

test('an invalid id also cancels the earlier event', async () => {
  navigate('event-detail', 'id=3', 'Event details');
  const slow = requests[0];
  navigate('event-detail', 'id=abc', 'Event details');
  slow.answer(200, event(3, 'Jazz Night')); await tick();
  assert.match(shown(), /Choose an event to see its details/);
  assert.equal(requests.length, 1, 'no request is sent for an invalid id');
});

test('when two events are opened quickly, only the newer one is shown, whatever order they answer in', async () => {
  navigate('event-detail', 'id=3', 'Event details');
  navigate('event-detail', 'id=4', 'Event details');
  const [older, newer] = requests;
  newer.answer(200, event(4, 'Food Fest')); await tick();
  older.answer(200, event(3, 'Jazz Night')); await tick();
  assert.equal(shown(), 'Food Fest');
  assert.equal(document.title, 'Food Fest — Ayojon');
});

test('an unpublished or unknown event shows the server message', async () => {
  navigate('event-detail', 'id=7', 'Event details');
  requests[0].answer(404, {message:'This event could not be found.'}); await tick();
  assert.match(shown(), /This event could not be found\./);
});
