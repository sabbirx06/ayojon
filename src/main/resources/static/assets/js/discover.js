import { $, $$, escapeHtml, api } from './shared.js';
import { shortDate, priceText, safeUrl } from './event-format.js';

/* --- VIEW: Discover search and filters (Tasnia, Sprint 1, F5) --- */
// Tasnia: Sprint 2 trending feed. Nora: Sprint 2 GPS radius. Sabbir: Sprint 3 recommendations.
const form = $('#discover-filters');
let categoriesLoaded = false;
let latestSearch = 0;
let typingTimer;

function selectedCategory() {
  return $('.category-pills button.active')?.dataset.category || '';
}

function selectCategory(button) {
  $$('.category-pills button').forEach(item => {
    item.classList.toggle('active', item === button);
    item.setAttribute('aria-pressed', String(item === button));
  });
}

/** Turns the form into ?q=...&city=... and leaves out empty filters. */
function searchParams() {
  const values = {
    q: form.elements.query.value.trim(),
    categoryId: selectedCategory(),
    city: form.elements.city.value,
    from: form.elements.from.value,
    until: form.elements.until.value,
    price: form.elements.price.value
  };
  return new URLSearchParams(Object.entries(values).filter(([, value]) => value));
}

function showFeedback(title, message) {
  $('#search-results-grid').innerHTML = '';
  $('#search-feedback').innerHTML = `<h2>${escapeHtml(title)}</h2>${message ? `<p>${escapeHtml(message)}</p>` : ''}`;
  $('#search-feedback').hidden = false;
}

function card(event) {
  const poster = safeUrl(event.posterUrl);
  return `
    <article class="event-card">
      <div class="event-image">
        ${poster ? `<img src="${escapeHtml(poster)}" alt="" width="700" height="438" loading="lazy">` : ''}
        <span class="date-chip">${escapeHtml(shortDate(event.startTime))}</span>
      </div>
      <div class="event-card-body">
        <span class="event-category">${escapeHtml(event.category)}</span>
        <h3><a class="card-link" href="#event-detail?id=${event.id}">${escapeHtml(event.title)}</a></h3>
        <p class="event-location">⌖ ${escapeHtml(event.venueName)}, ${escapeHtml(event.area)}</p>
        <div class="event-card-meta">
          <span class="event-price"><strong>${escapeHtml(priceText(event.price))}</strong></span>
          ${event.verified ? '<span class="trust-badge">✧ Verified</span>' : ''}
        </div>
      </div>
    </article>`;
}

async function search() {
  const request = ++latestSearch;
  $('#search-count').textContent = 'Searching…';
  showFeedback('Finding events…');
  try {
    const events = await api(`/api/events?${searchParams()}`);
    if (request !== latestSearch) return; // a newer search has started
    $('#search-count').textContent = `${events.length} event${events.length === 1 ? '' : 's'} found`;
    if (!events.length) {
      showFeedback('No events match these filters.', 'Try another keyword, a different city or wider dates, or clear the filters.');
      return;
    }
    $('#search-feedback').hidden = true;
    $('#search-results-grid').innerHTML = events.map(card).join('');
  } catch (error) {
    if (request !== latestSearch) return;
    $('#search-count').textContent = '';
    showFeedback('Events could not be loaded.', error.message);
  }
}

async function loadCategories() {
  try {
    const categories = await api('/api/events/categories');
    $('.category-pills').insertAdjacentHTML('beforeend', categories.map(category =>
      `<button type="button" data-category="${category.id}" aria-pressed="false">${escapeHtml(category.name)}</button>`).join(''));
    categoriesLoaded = true;
  } catch {
    // Search still works without the category buttons; they are retried the next time Discover opens.
  }
}

// Some organizer poster links are dead; show the plain card background instead of a broken image.
$('#search-results-grid').addEventListener('error', event => {
  if (event.target.tagName === 'IMG') event.target.remove();
}, true);
$('.category-pills').addEventListener('click', event => {
  const button = event.target.closest('button');
  if (!button) return;
  selectCategory(button);
  search();
});
form.elements.query.addEventListener('input', () => {
  clearTimeout(typingTimer);
  typingTimer = setTimeout(search, 350);
});
form.addEventListener('change', event => {
  if (event.target.name !== 'query') search();
});
form.addEventListener('submit', event => {
  event.preventDefault();
  clearTimeout(typingTimer);
  search();
});
form.addEventListener('reset', () => {
  // The browser resets the inputs after this event runs, so search on the next tick.
  setTimeout(() => {
    selectCategory($('.category-pills button[data-category=""]'));
    search();
  });
});

document.addEventListener('ayojon:viewchange', async ({detail}) => {
  if (detail.view !== 'discover') return;
  if (!categoriesLoaded) await loadCategories();
  search();
});
