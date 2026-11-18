import { $, api, escapeHtml } from './shared.js';
import { currentUser } from './auth.js';
import { createAttendance } from './attendance.js';

const attendance = createAttendance($('#profile-attendance'), {getUser: () => currentUser, historyOnly: true});
let profileActive = false;

const badgeGrid = $('#profile-badges-grid');
const badgeStatus = $('#profile-badges-status');
let rewardsRequest = 0;

async function loadRewards() {
  const request = ++rewardsRequest;
  const user = currentUser;
  badgeGrid.replaceChildren();
  badgeStatus.textContent = user ? 'Loading badges…' : 'Log in to see your badges.';
  if (!user) return;

  const [session, badges] = await Promise.allSettled([
    api('/api/auth/me'),
    api('/api/attendance/badges/me')
  ]);
  if (request !== rewardsRequest || currentUser !== user) return;

  if (session.status === 'fulfilled' && session.value?.user?.id === user.id) {
    $('#profile-points').textContent = Number(session.value.user.points || 0).toLocaleString('en-BD');
  }
  if (badges.status === 'rejected' || !Array.isArray(badges.value)) {
    badgeStatus.textContent = 'Could not load your badges.';
    return;
  }
  badgeStatus.textContent = badges.value.length ? '' : 'No badges earned yet.';
  badgeGrid.innerHTML = badges.value.map(badge => `
    <article class="card badge-card">
      <h3>${escapeHtml(badge.name)}</h3>
      <p>${escapeHtml(badge.description)}</p>
    </article>`).join('');
}
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
  if (profileActive) {
    loadRewards();
    if (currentUser) attendance.activate('profile');
  }
});
document.addEventListener('ayojon:viewchange', ({detail}) => {
  profileActive = detail.view === 'profile';
  if (profileActive) {
    renderProfile();
    attendance.activate('profile');
    loadRewards();
  } else {
    ++rewardsRequest;
    attendance.clear();
  }
});
