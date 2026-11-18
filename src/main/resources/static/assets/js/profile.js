import { $ } from './shared.js';
import { currentUser } from './auth.js';
import { createAttendance } from './attendance.js';

const attendance = createAttendance($('#profile-attendance'), {getUser: () => currentUser, historyOnly: true});
let profileActive = false;

/* --- VIEW: profile; account identity and points only --- */
function renderProfile() {
  $('#profile-name').textContent = currentUser?.name || 'Your profile';
  $('#profile-email').textContent = currentUser?.email || 'Log in to see your account details.';
  $('#profile-avatar').textContent = (currentUser?.name || 'A').slice(0, 1).toUpperCase();
  $('#profile-points').textContent = currentUser ? Number(currentUser.points || 0).toLocaleString('en-BD') : '—';
  $('#profile-login').hidden = Boolean(currentUser);
}

document.addEventListener('ayojon:sessionchange', () => {
  renderProfile();
  attendance.clear();
  if (profileActive && currentUser) attendance.activate('profile');
});
document.addEventListener('ayojon:viewchange', ({detail}) => {
  profileActive = detail.view === 'profile';
  if (profileActive) {
    renderProfile();
    attendance.activate('profile');
  } else attendance.clear();
});
