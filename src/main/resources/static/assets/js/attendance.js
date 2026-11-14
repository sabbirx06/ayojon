import {api, escapeHtml} from './shared.js';

// One mounted panel; epochs discard responses from a previous route/session.
export function createAttendance(root, {request = api, getUser}) {
  let epoch = 0;
  let selection = 0;
  let selected = null;
  let checked = null;
  let submitting = false;
  let events = [];
  let historyVersion = 0;
  const $ = selector => root.querySelector(selector);
  const current = token => token === epoch && Boolean(getUser());
  const date = value => value ? String(value).replace('T', ' ') : 'Time unavailable';

  function clear() {
    epoch++;
    selection++;
    selected = null;
    checked = null;
    submitting = false;
    root.replaceChildren();
  }

  async function activate(view, routeId) {
    clear();
    if (!getUser()) return;
    const token = epoch;
    root.innerHTML = `<section class="card content-section" aria-label="Attendance check-in">
      <h2>Your attendance</h2>
      <p>Choose a published event to check in. Times shown in Bangladesh Standard Time.</p>
      <label>Event<select data-event disabled><option value="">Loading events…</option></select></label>
      <p data-event-info></p>
      <button type="button" class="btn btn-primary" data-check-in disabled>Check in</button>
      <p data-feedback role="status" aria-live="polite">Loading events…</p>
      <h3>Check-in history</h3>
      <div data-history aria-live="polite">Loading history…</div>
    </section>`;
    try {
      const result = await request('/api/attendance/events');
      if (!current(token)) return;
      events = result;
      $('[data-event]').innerHTML = '<option value="">Choose an event</option>' + events.map(event =>
        `<option value="${event.id}">${escapeHtml(event.title)} — ${escapeHtml(event.venueName)} · ${escapeHtml(date(event.startTime))}</option>`).join('');
      $('[data-event]').disabled = false;
      $('[data-feedback]').textContent = events.length ? 'Choose an event to see your check-in status.' : 'No published events available.';
      $('[data-event]').addEventListener('change', () => select(events.find(event => String(event.id) === $('[data-event]').value), token));
      $('[data-check-in]').addEventListener('click', () => checkIn(token));
    } catch (error) {
      if (current(token)) $('[data-feedback]').textContent = error.message;
    }
    if (current(token)) await loadHistory(token);
  }

  async function loadHistory(token) {
    const version = ++historyVersion;
    try {
      const history = await request('/api/attendance/me');
      if (!current(token) || version !== historyVersion) return;
      $('[data-history]').innerHTML = history.length ? history.map(row => {
        const event = events.find(item => item.id === row.eventId);
        return `<p>${escapeHtml(event?.title || `Event #${row.eventId}`)} · ${escapeHtml(date(row.checkedInAt))}</p>`;
      }).join('') : '<p>No check-ins yet.</p>';
    } catch (error) {
      if (current(token) && version === historyVersion) $('[data-history]').textContent = error.message || 'History could not be loaded. Reopen Profile to retry.';
    }
  }

  async function checkIn(token) {
    if (!current(token) || !selected || checked !== false || submitting) return;
    const event = selected;
    submitting = true;
    $('[data-check-in]').disabled = true;
    $('[data-event]').disabled = true;
    $('[data-check-in]').textContent = 'Checking in…';
    try {
      await request('/api/attendance/check-in', {method:'POST', headers:{'Content-Type':'application/json'}, body:JSON.stringify({eventId:event.id})});
      if (!current(token)) return;
      checked = true;
      $('[data-check-in]').textContent = 'Checked in';
      $('[data-feedback]').textContent = 'Check-in recorded.';
      await loadHistory(token);
    } catch (error) {
      if (!current(token)) return;
      if (error.status === 409) {
        checked = true;
        $('[data-feedback]').textContent = 'Already checked in.';
        await loadHistory(token);
      } else $('[data-feedback]').textContent = error.message;
    } finally {
      if (current(token)) {
        submitting = false;
        $('[data-event]').disabled = false;
        $('[data-check-in]').disabled = checked !== false;
        $('[data-check-in]').textContent = checked === true ? 'Checked in' : 'Check in';
      }
    }
  }

  async function select(event, token) {
    const version = ++selection;
    selected = event || null;
    checked = null;
    $('[data-check-in]').disabled = true;
    if (!event) return;
    $('[data-feedback]').textContent = 'Loading check-in status…';
    try {
      const status = await request(`/api/attendance/events/${event.id}/status`);
      if (!current(token) || version !== selection) return;
      checked = status.checkedIn;
      $('[data-check-in]').disabled = checked;
      $('[data-check-in]').textContent = checked ? 'Checked in' : 'Check in';
      $('[data-feedback]').textContent = checked ? 'Already checked in.' : 'Ready to check in.';
    } catch (error) {
      if (current(token) && version === selection) $('[data-feedback]').textContent = error.message;
    }
  }
  return {activate, clear};
}
