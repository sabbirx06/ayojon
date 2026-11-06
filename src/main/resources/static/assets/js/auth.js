import { $, escapeHtml, toast, api, resetCsrf } from './shared.js';

export let currentUser = null;
export let sessionLoading = true;

/* --- AUTH: retain database-backed signup, CSRF, sessions and logout --- */
function prepareAuth(signup, params) {
  $('#auth-form').reset();
  $('#auth-title').textContent = signup ? 'Good things start here.' : 'Welcome back.';
  $('#auth-subtitle').textContent = signup ? 'A little more discovery. A little more connection.' : 'Your next good memory is waiting.';
  $('#name-field').hidden = !signup;
  $('#name-field input').required = signup;
  $('#name-field input').disabled = !signup;
  $('#role-field').hidden = !signup;
  $('#role-field select').disabled = !signup;
  $('#role-field select').value = params.get('role') === 'organizer' ? 'organizer' : 'user';
  $('#password').type = 'password';
  $('#password').autocomplete = signup ? 'new-password' : 'current-password';
  $('#show-password').textContent = 'Show';
  $('#show-password').setAttribute('aria-label', 'Show password');
  $('#show-password').setAttribute('aria-pressed', 'false');
  $('#auth-feedback').hidden = true;
  $('#auth-submit').textContent = signup ? 'Create account ↗' : 'Log in ↗';
  $('#auth-switch').innerHTML = signup ? 'Already have an account? <a href="#login">Log in</a>' : 'New to Ayojon? <a href="#signup">Create an account</a>';
}

async function login(email, password) {
  await api('/api/auth/login', {method:'POST', headers:{'Content-Type':'application/x-www-form-urlencoded'}, body:new URLSearchParams({email, password})});
  resetCsrf();
  const session = await api('/api/auth/me');
  if (!session.authenticated) throw new Error('Could not restore your account. Please log in again.');
  currentUser = session.user;
}

function updateSessionUI() {
  $('#nav-auth').innerHTML = currentUser
    ? `<a class="avatar nav-avatar" href="#profile" aria-label="Your profile">${escapeHtml(currentUser.name.slice(0,1).toUpperCase())}</a><button class="logout-button" type="button">Log out</button>`
    : '<a href="#login" class="text-link">Log in</a><a href="#signup" class="btn btn-sm btn-primary">Sign up ↗</a>';
  document.dispatchEvent(new CustomEvent('ayojon:sessionchange'));
}

$('#show-password').addEventListener('click', () => {
  const show = $('#password').type === 'password';
  $('#password').type = show ? 'text' : 'password';
  $('#show-password').textContent = show ? 'Hide' : 'Show';
  $('#show-password').setAttribute('aria-label', show ? 'Hide password' : 'Show password');
  $('#show-password').setAttribute('aria-pressed', String(show));
});
$('#auth-form').addEventListener('submit', async event => {
  event.preventDefault();
  const form = event.currentTarget;
  const button = $('#auth-submit');
  if (button.disabled) return;
  button.disabled = true;
  $('#auth-feedback').hidden = true;
  const values = Object.fromEntries(new FormData(form));
  values.email = values.email.trim().toLowerCase();
  try {
    if (location.hash.slice(1).split('?')[0].replace(/-view$/, '') === 'signup') await api('/api/auth/signup', {method:'POST', headers:{'Content-Type':'application/json'}, body:JSON.stringify(values)});
    await login(values.email, values.password);
    form.reset();
    updateSessionUI();
    location.hash = 'discover';
    toast(`Welcome, ${currentUser.name}. You’re signed in.`);
  } catch (error) {
    $('#auth-feedback').textContent = error.message;
    $('#auth-feedback').hidden = false;
    $('#password').value = '';
  } finally { button.disabled = false; }
});
$('#nav-auth').addEventListener('click', async event => {
  const button = event.target.closest('.logout-button');
  if (!button || button.disabled) return;
  button.disabled = true;
  try {
    await api('/api/auth/logout', {method:'POST'});
    currentUser = null;
    resetCsrf();
    updateSessionUI();
    location.hash = 'landing';
    toast('You’re logged out. See you at the next gathering.');
  } catch (error) { toast(error.message); button.disabled = false; }
});

async function loadSession() {
  try {
    const session = await api('/api/auth/me');
    currentUser = session.authenticated ? session.user : null;
    updateSessionUI();
  } catch { toast('Your session could not be loaded. Please refresh before logging in.'); }
  finally {
    sessionLoading = false;
    document.dispatchEvent(new CustomEvent('ayojon:sessionready'));
  }
}


document.addEventListener('ayojon:viewchange', ({detail}) => {
  if (detail.view === 'auth') prepareAuth(detail.name === 'signup', detail.params);
});

export function initAuth() { loadSession(); }
