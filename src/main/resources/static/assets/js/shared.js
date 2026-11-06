const $ = selector => document.querySelector(selector);
const $$ = selector => [...document.querySelectorAll(selector)];
const escapeHtml = value => String(value ?? '').replace(/[&<>"']/g, character => ({'&':'&amp;', '<':'&lt;', '>':'&gt;', '"':'&quot;', "'":'&#39;'}[character]));
let csrf = null;
let toastTimer;

function toast(message) {
  clearTimeout(toastTimer);
  $('#toast').textContent = message;
  $('#toast').hidden = false;
  toastTimer = setTimeout(() => $('#toast').hidden = true, 5000);
}

async function api(path, options = {}) {
  const headers = {...options.headers};
  if (options.method && options.method !== 'GET') {
    if (!csrf) {
      const response = await fetch('/api/auth/csrf', {cache:'no-store'});
      if (!response.ok) throw new Error('Could not start a secure session. Refresh and try again.');
      csrf = await response.json();
    }
    headers[csrf.headerName] = csrf.token;
  }
  const response = await fetch(path, {...options, headers, cache:'no-store'});
  const body = response.status === 204 ? null : await response.json().catch(() => null);
  if (!response.ok) {
    if (response.status === 403) csrf = null;
    throw new Error(body?.message || (response.status === 403 ? 'Your session has expired. Please try again.' : 'The request could not be completed. Please try again.'));
  }
  return body;
}


export function resetCsrf() { csrf = null; }
export { $, $$, escapeHtml, toast, api };
