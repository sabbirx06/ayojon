import {test} from 'node:test';
import assert from 'node:assert/strict';
import {api} from '../../main/resources/static/assets/js/shared.js';

test('API errors retain HTTP status when the server omits a message', async t => {
  t.mock.method(globalThis, 'fetch', async () => new Response('{}', {
    status:409, headers:{'Content-Type':'application/json'}
  }));
  await assert.rejects(api('/api/attendance/check-in'), error => {
    assert.equal(error.status, 409);
    assert.match(error.message, /request could not be completed/);
    return true;
  });
});
