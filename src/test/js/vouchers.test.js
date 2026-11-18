import { test } from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { Window } from 'happy-dom';

test('a sample event claims the voucher and the profile shows the saved code', async () => {
  const window = new Window({url:'http://localhost/'});
  globalThis.document = window.document;
  globalThis.CustomEvent = window.CustomEvent;
  document.write(readFileSync('src/main/resources/static/index.html', 'utf8'));
  const user = {id:7, name:'Demo User', email:'demo@example.test', points:0};
  let claims = 0;
  globalThis.fetch = async (url, options) => {
    const json = value => new Response(JSON.stringify(value));
    if (url === '/api/auth/me') return json({authenticated:true, user});
    if (url === '/api/auth/csrf') return json({headerName:'X-CSRF-TOKEN', token:'test-token'});
    if (url === '/api/attendance/me' || url === '/api/attendance/badges/me') return json([]);
    if (url === '/api/vouchers/demo') return json({sponsorName:'Demo sponsor', discountPercent:20, claimed:claims > 0});
    if (url === '/api/vouchers/demo/claim') {
      assert.equal(options.method, 'POST');
      assert.equal(options.headers['X-CSRF-TOKEN'], 'test-token');
      claims++;
      return new Response(null, {status:204});
    }
    if (url === '/api/vouchers/me') {
      return json(claims ? [{sponsorName:'Demo sponsor', promoCode:'BKASHAYOJON20', discountPercent:20}] : []);
    }
    throw new Error(`Unexpected request: ${url}`);
  };
  try {
    const {initAuth} = await import('../../main/resources/static/assets/js/auth.js');
    await import('../../main/resources/static/assets/js/event-detail.js');
    await import('../../main/resources/static/assets/js/profile.js');
    const settle = () => new Promise(resolve => setImmediate(resolve));
    const navigate = (view, query = '') => document.dispatchEvent(new CustomEvent('ayojon:viewchange',
      {detail:{view, params:new URLSearchParams(query)}}));
    initAuth();
    await settle();
    const sample = document.querySelector('#events-view a[href*="demo=1"]');
    navigate('event-detail', sample.getAttribute('href').split('?')[1]);
    await settle();
    const box = document.querySelector('#event-voucher-box');
    const button = box.querySelector('button');
    assert.equal(box.hidden, false);
    assert.equal(button.disabled, false);
    button.click();
    button.click();
    await settle();
    assert.equal(claims, 1);
    assert.equal(button.textContent, '✔ Claimed');
    assert.equal(button.disabled, true);

    navigate('profile');
    await settle();
    assert.match(document.querySelector('#profile-vouchers').textContent, /BKASHAYOJON20 · 20% off/);

    navigate('event-detail', 'demo=1');
    await settle();
    assert.equal(button.textContent, '✔ Claimed');
    assert.equal(button.disabled, true);
  } finally {
    await window.happyDOM.close();
  }
});
