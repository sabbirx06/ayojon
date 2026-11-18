import { $, escapeHtml, api } from './shared.js';
import { schedule, priceText, safeUrl } from './event-format.js';
import { hideVoucher, showVoucher } from './vouchers.js';

/* --- VIEW: event detail profile (Tasnia, Sprint 1, F6) --- */
const defaultPoster = $('#event-poster').src;
let currentEvent = null;
let demoVoucher = false;

async function updateAttendanceCount(eventId) {
  try {
    const countEl = $('#event-attendance-count');
    if (!countEl) return;
    const res = await api(`/api/attendance/events/${eventId}/count`);
    const count = res?.attendanceCount || 0;
    countEl.textContent = count === 1 ? '1 attendee checked in' : `${count} attendees checked in`;
  } catch {
    const countEl = $('#event-attendance-count');
    if (countEl) countEl.textContent = '';
  }
}

async function setupCheckIn(event) {
  currentEvent = event;
  const btn = $('#event-check-in');
  const feedback = $('#event-check-in-feedback');
  if (!btn) return;
  if (feedback) feedback.textContent = '';
  btn.onclick = null;

  updateAttendanceCount(event.id);

  try {
    const status = await api(`/api/attendance/events/${event.id}/status`);
    if (currentEvent !== event) return;
    if (status?.checkedIn) {
      btn.disabled = true;
      btn.textContent = '✔ Checked In';
      if (feedback) feedback.textContent = 'You have checked in to this event.';
      return;
    }
  } catch {
    // Not logged in or status endpoint unavailable
  }

  if (currentEvent !== event) return;
  btn.disabled = false;
  btn.textContent = '✔ Check In to Event';
  btn.onclick = async () => {
    btn.disabled = true;
    btn.textContent = 'Checking in…';
    if (feedback) feedback.textContent = '';
    try {
      await api('/api/attendance/check-in', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ eventId: event.id })
      });
      btn.textContent = '✔ Checked In';
      if (feedback) feedback.textContent = 'Check-in recorded!';
      updateAttendanceCount(event.id);
      document.dispatchEvent(new CustomEvent('ayojon:sessionchange'));
    } catch (err) {
      if (err.status === 401 || (err.message && err.message.toLowerCase().includes('unauthorized'))) {
        window.location.hash = '#login';
        return;
      }
      if (err.message && err.message.includes('Already checked in')) {
        btn.textContent = '✔ Checked In';
        if (feedback) feedback.textContent = 'You have already checked in.';
      } else {
        btn.disabled = false;
        btn.textContent = '✔ Check In to Event';
        if (feedback) feedback.textContent = err.message || 'Check-in failed. Please try again.';
      }
    }
  };
}

// Only the most recent load may change the page. Leaving the page, or opening a missing or
// invalid id, cancels the pending load, so a late response can never replace the newer screen
// or overwrite another page's title.
let latestLoad = 0;
let pending = null;

function cancelPendingLoad() {
  latestLoad++;
  pending?.abort();
  pending = null;
}

function showFeedback(title, message) {
  $('#event-detail-content').hidden = true;
  $('#event-detail-feedback').innerHTML = `<h2>${escapeHtml(title)}</h2>${message ? `<p>${escapeHtml(message)}</p>` : ''}
    <a class="btn btn-outline" href="#discover">Find events ↗</a>`;
  $('#event-detail-feedback').hidden = false;
}

function render(event) {
  document.title = `${event.title} — Ayojon`;
  $('#event-poster').src = safeUrl(event.posterUrl) || defaultPoster;
  $('#event-poster').alt = `Poster for ${event.title}`;
  $('#event-category').textContent = event.category;
  $('#event-title').textContent = event.title;
  $('#event-organizer').textContent = `Organized by ${event.organizerName}`;
  $('#event-verified').textContent = event.verified ? '✧ Verified' : 'Not verified yet';
  $('#event-description').textContent = event.description || 'The organizer has not added a description yet.';
  $('#event-schedule').textContent = schedule(event.startTime, event.endTime);
  $('#event-venue').textContent = event.venueName;
  $('#event-address').textContent = event.venueAddress;
  $('#event-price').innerHTML = `<strong>${escapeHtml(priceText(event.price))}</strong>`;

  const ticketUrl = safeUrl(event.ticketUrl);
  $('#event-ticket-link').hidden = !ticketUrl;
  if (ticketUrl) $('#event-ticket-link').href = ticketUrl;
  $('#event-ticket-note').textContent = ticketUrl
    ? 'Tickets are sold on the organizer’s official page.'
    : 'The organizer has not added a ticket link yet.';

  setupCheckIn(event);
  showVoucher();

  $('#event-detail-feedback').hidden = true;
  $('#event-detail-content').hidden = false;
}

document.addEventListener('ayojon:sessionchange', () => {
  if (currentEvent && !$('#event-detail-content').hidden) {
    setupCheckIn(currentEvent);
    showVoucher();
  } else if (demoVoucher) {
    showVoucher({demo:true});
  }
});

async function load(id) {
  cancelPendingLoad();
  const request = latestLoad;
  const controller = pending = new AbortController();
  showFeedback('Loading event…');
  try {
    const event = await api(`/api/events/${id}`, {signal:controller.signal});
    if (request === latestLoad) render(event);
  } catch (error) {
    if (request === latestLoad && !controller.signal.aborted) showFeedback('This event could not be shown.', error.message);
  } finally {
    if (pending === controller) pending = null;
  }
}

// A dead poster link falls back to the default picture instead of a broken image.
$('#event-poster').addEventListener('error', () => {
  if ($('#event-poster').src !== defaultPoster) $('#event-poster').src = defaultPoster;
});

document.addEventListener('ayojon:viewchange', ({detail}) => {
  cancelPendingLoad(); // any navigation, including leaving this page, makes an older load stale
  currentEvent = null;
  demoVoucher = false;
  hideVoucher();
  if (detail.view !== 'event-detail') return;
  const id = detail.params.get('id');
  $('#event-back').href = id ? '#discover' : '#events';
  $('#event-back').textContent = id ? '← Back to Discover' : '← Back to Events';
  if (!id && detail.params.get('demo') === '1') {
    demoVoucher = true;
    showFeedback('Demo event preview', 'Try claiming a sample sponsor voucher.');
    showVoucher({demo:true});
    return;
  }
  if (!/^\d+$/.test(id || '')) {
    showFeedback('Choose an event to see its details.', 'Open an event from the Discover page.');
    return;
  }
  load(id);
});
