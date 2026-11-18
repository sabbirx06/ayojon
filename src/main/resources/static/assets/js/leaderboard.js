import { $, api, escapeHtml } from './shared.js';

const rankings = $('#leaderboard-rankings');
const status = $('#leaderboard-status');
let requestVersion = 0;

document.addEventListener('ayojon:viewchange', async ({detail}) => {
  const request = ++requestVersion;
  if (detail.view !== 'leaderboard') return;

  rankings.replaceChildren();
  status.textContent = 'Loading rankings…';
  try {
    const entries = await api('/api/attendance/leaderboard');
    if (request !== requestVersion) return;
    status.textContent = entries.length ? '' : 'No rankings yet.';
    rankings.innerHTML = entries.map((entry, index) => `
      <tr>
        <td>${index + 1}</td>
        <th scope="row">${escapeHtml(entry.name)}</th>
        <td>${Number(entry.points).toLocaleString('en-BD')}</td>
      </tr>`).join('');
  } catch {
    if (request === requestVersion) status.textContent = 'Could not load rankings.';
  }
});
