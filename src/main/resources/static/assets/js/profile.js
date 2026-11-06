import { $ } from './shared.js';
import { currentUser } from './auth.js';

/* --- VIEW: profile; account identity and points only --- */
function renderProfile() {
  $('#profile-name').textContent = currentUser?.name || 'Your profile';
  $('#profile-email').textContent = currentUser?.email || 'Log in to see your account details.';
  $('#profile-avatar').textContent = (currentUser?.name || 'A').slice(0, 1).toUpperCase();
  $('#profile-points').textContent = currentUser ? Number(currentUser.points || 0).toLocaleString('en-BD') : '—';
  $('#profile-login').hidden = Boolean(currentUser);
}

document.addEventListener('ayojon:sessionchange', renderProfile);
document.addEventListener('ayojon:viewchange', ({detail}) => {
  if (detail.view === 'profile') renderProfile();
});
