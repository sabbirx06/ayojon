import { $, escapeHtml, api } from './shared.js';
import { schedule, priceText, safeUrl } from './event-format.js';

/* --- VIEW: event detail profile (Tasnia, Sprint 1, F6) --- */
// Check-in, bookmark, plan, voucher and reviews on this page belong to other features and stay as they are.
const defaultPoster = $('#event-poster').src;
let latestLoad = 0;

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
  $('#event-category').textContent = event.status === 'CANCELLED' ? `${event.category} · Cancelled` : event.category;
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

  $('#event-detail-feedback').hidden = true;
  $('#event-detail-content').hidden = false;
}

async function load(id) {
  const request = ++latestLoad;
  showFeedback('Loading event…');
  try {
    const event = await api(`/api/events/${id}`);
    if (request === latestLoad) render(event);
  } catch (error) {
    if (request === latestLoad) showFeedback('This event could not be shown.', error.message);
  }
}

// A dead poster link falls back to the default picture instead of a broken image.
$('#event-poster').addEventListener('error', () => {
  if ($('#event-poster').src !== defaultPoster) $('#event-poster').src = defaultPoster;
});

document.addEventListener('ayojon:viewchange', ({detail}) => {
  if (detail.view !== 'event-detail') return;
  const id = detail.params.get('id');
  $('#event-back').href = id ? '#discover' : '#events';
  $('#event-back').textContent = id ? '← Back to Discover' : '← Back to Events';
  if (!/^\d+$/.test(id || '')) {
    showFeedback('Choose an event to see its details.', 'Open an event from the Discover page.');
    return;
  }
  load(id);
});
