/* --- HELPERS: shared by the Discover results and the event detail page --- */

// The API sends Dhaka local times such as "2026-11-20T15:00:00". Reading them as UTC and
// formatting them in UTC shows the same clock time on every computer, whatever its time zone.
const asDate = value => new Date(`${value}Z`);
const format = (value, options) => asDate(value).toLocaleString('en-US', {timeZone:'UTC', ...options});
const time = value => format(value, {hour:'numeric', minute:'2-digit'});

/** "NOV 20 · 3:00 PM" for the date chip on a card. */
export function shortDate(value) {
  return `${format(value, {month:'short', day:'numeric'}).toUpperCase()} · ${time(value)}`;
}

/** "Fri, Nov 20, 2026 · 3:00 PM – Sat, Nov 21 · 11:00 PM"; same-day events show only the end time. */
export function schedule(start, end) {
  const startText = `${format(start, {weekday:'short', month:'short', day:'numeric', year:'numeric'})} · ${time(start)}`;
  const sameDay = start.slice(0, 10) === end.slice(0, 10);
  const endText = sameDay ? time(end) : `${format(end, {weekday:'short', month:'short', day:'numeric'})} · ${time(end)}`;
  return `${startText} – ${endText}`;
}

/** "Free" or "৳1,200". */
export function priceText(price) {
  return Number(price || 0) === 0 ? 'Free' : `৳${Number(price).toLocaleString('en-BD')}`;
}

/** Links come from organizers, so only http(s) links are used. */
export function safeUrl(url) {
  try {
    const parsed = new URL(url);
    return ['http:', 'https:'].includes(parsed.protocol) ? parsed.href : null;
  } catch {
    return null;
  }
}
