import { $, $$, escapeHtml, toast, api } from './shared.js';
import { currentUser } from './auth.js';

let editingEventId = null;

export async function loadOrganizerListings() {
  const container = $('#organizer-listings');
  if (!container) return;

  const currentRole = currentUser ? ($('#demo-role')?.value || currentUser.role) : 'USER';
  if (!currentUser || currentRole !== 'ORGANIZER') {
    container.innerHTML = `
      <div class="empty-state">
        <span aria-hidden="true">🔒</span>
        <h3>Organizer access required</h3>
        <p>Please log in with an Organizer account to view your submissions.</p>
      </div>`;
    return;
  }

  container.innerHTML = `<div class="empty-state"><p>Loading your events...</p></div>`;

  try {
    const list = await api('/api/events/mine');
    const badgeCount = $('#submitted-title')?.nextElementSibling;
    if (badgeCount) badgeCount.textContent = `${list.length} listing${list.length === 1 ? '' : 's'}`;

    if (!list || list.length === 0) {
      container.innerHTML = `
        <div class="empty-state">
          <span aria-hidden="true">↗</span>
          <h3>A little idea can bring people together.</h3>
          <p>You haven't submitted any events yet. Use the form on the left to submit your first gathering for review.</p>
        </div>`;
      return;
    }

    container.innerHTML = `
      <div class="organizer-event-cards">
        ${list.map(event => renderOrganizerCard(event)).join('')}
      </div>`;

    container.querySelectorAll('[data-edit-id]').forEach(btn => {
      btn.addEventListener('click', () => {
        const id = parseInt(btn.dataset.editId, 10);
        const item = list.find(e => e.eventId === id);
        if (item) populateFormForEdit(item);
      });
    });
  } catch (err) {
    container.innerHTML = `
      <div class="empty-state">
        <p class="form-error">Could not load listings: ${escapeHtml(err.message)}</p>
        <button class="btn btn-outline" id="retry-organizer-listings">Retry</button>
      </div>`;
    $('#retry-organizer-listings')?.addEventListener('click', loadOrganizerListings);
  }
}

function renderOrganizerCard(event) {
  let statusBadge = '';
  let revisionBox = '';
  let editBtn = '';

  const isRevision = event.status === 'DRAFT' && event.latestNote && event.latestNote.startsWith('[REVISION]');
  const isResubmitted = event.status === 'DRAFT' && event.latestDecision === 'RESUBMITTED';
  const isPending = event.status === 'DRAFT' && !isRevision && !isResubmitted;
  const isApproved = event.status === 'PUBLISHED';
  const isRejected = event.status === 'CANCELLED';

  if (isApproved) {
    statusBadge = `<span class="status-badge status-approved">✓ Published</span>`;
  } else if (isRejected) {
    statusBadge = `<span class="status-badge status-rejected">✕ Rejected</span>`;
    if (event.latestNote) {
      revisionBox = `
        <div class="review-feedback-box feedback-rejected">
          <strong>Admin feedback:</strong>
          <p>${escapeHtml(event.latestNote)}</p>
        </div>`;
    }
  } else if (isRevision) {
    const feedback = event.latestNote.replace(/^\[REVISION\]\s*/, '');
    statusBadge = `<span class="status-badge status-revision">⚠ Revision Requested</span>`;
    revisionBox = `
      <div class="review-feedback-box feedback-revision">
        <strong>Admin requested revisions:</strong>
        <p>${escapeHtml(feedback)}</p>
      </div>`;
    editBtn = `<button type="button" class="btn btn-sm btn-outline" data-edit-id="${event.eventId}">Edit & Resubmit ↗</button>`;
  } else if (isResubmitted) {
    statusBadge = `<span class="status-badge status-resubmitted">⟳ Resubmitted · Pending Review</span>`;
  } else {
    statusBadge = `<span class="status-badge status-pending">⏳ Pending Review</span>`;
  }

  const startDate = event.startTime ? new Date(event.startTime).toLocaleString('en-US', {
    month: 'short', day: 'numeric', year: 'numeric', hour: 'numeric', minute: '2-digit'
  }) : 'TBD';

  return `
    <article class="organizer-listing-card card">
      <div class="listing-card-header">
        <div>
          <span class="eyebrow">${escapeHtml(event.categoryName || 'Event')} · ${escapeHtml(event.areaName || '')}</span>
          <h3 class="listing-card-title">${escapeHtml(event.title)}</h3>
        </div>
        <div>${statusBadge}</div>
      </div>
      <p class="muted small">⌖ ${escapeHtml(event.venueName)} — ${escapeHtml(event.startTime ? startDate : '')}</p>
      ${event.posterUrl ? `<img src="${escapeHtml(event.posterUrl)}" alt="Event poster" loading="lazy" style="max-width:100%;border-radius:8px">` : ''}
      ${revisionBox}
      ${editBtn ? `<div class="listing-card-actions">${editBtn}</div>` : ''}
    </article>`;
}

function populateFormForEdit(event) {
  editingEventId = event.eventId;
  const form = $('#event-submission-form');
  if (!form) return;

  form.elements.title.value = event.title || '';
  if (event.startTime) {
    form.elements.startTime.value = event.startTime.slice(0, 16);
  }
  if (event.endTime) {
    form.elements.endTime.value = event.endTime.slice(0, 16);
  }
  form.elements.venue.value = event.venueName || '';
  form.elements.address.value = event.venueName || '';
  if (form.elements.area) form.elements.area.value = event.areaName || '';
  if (form.elements.category) form.elements.category.value = event.categoryName || 'Music';
  form.elements.ticketUrl.value = event.ticketUrl || '';
  if (form.elements.posterUrl) form.elements.posterUrl.value = event.posterUrl || '';   // ADDED (poster)
  form.elements.description.value = event.description || '';

  const editBanner = $('#organizer-edit-mode');
  if (editBanner) {
    editBanner.hidden = false;
    $('#editing-event-name').textContent = event.title;
  }
  const submitBtn = $('#event-submit-button');
  if (submitBtn) submitBtn.textContent = 'Resubmit for Review ↗';

  form.scrollIntoView({ behavior: 'smooth' });
}

export function cancelEdit() {
  editingEventId = null;
  const form = $('#event-submission-form');
  if (form) form.reset();
  const editBanner = $('#organizer-edit-mode');
  if (editBanner) editBanner.hidden = true;
  const submitBtn = $('#event-submit-button');
  if (submitBtn) submitBtn.textContent = 'Submit for approval ↗';
}

export function initOrganizerView() {
  const form = $('#event-submission-form');
  if (!form) return;

  const fieldset = form.querySelector('fieldset');
  if (fieldset) fieldset.disabled = false;

  form.addEventListener('submit', async event => {
    event.preventDefault();
    const submitBtn = $('#event-submit-button');
    if (submitBtn) submitBtn.disabled = true;

    try {
      const data = {
        title: form.elements.title?.value?.trim(),
        startTime: form.elements.startTime?.value,
        endTime: form.elements.endTime?.value,
        venueName: form.elements.venue?.value?.trim(),
        venueAddress: form.elements.address?.value?.trim() || form.elements.venue?.value?.trim(),
        areaName: form.elements.area?.value?.trim() || 'Dhaka',
        categoryName: form.elements.category?.value || 'Music',
        latitude: parseFloat(form.elements.latitude?.value) || 23.8103,
        longitude: parseFloat(form.elements.longitude?.value) || 90.4125,
        ticketUrl: form.elements.ticketUrl?.value?.trim() || null,
        posterUrl: form.elements.posterUrl?.value?.trim() || null,   // ADDED (poster)
        description: form.elements.description?.value?.trim() || ''
      };

      if (!data.title) throw new Error('Please provide an event title.');
      if (!data.startTime || !data.endTime) throw new Error('Please select both start and end dates/times.');
      if (!data.venueName) throw new Error('Please provide a venue name.');

      if (editingEventId) {
        await api(`/api/events/${editingEventId}`, {
          method: 'PUT',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify(data)
        });
        toast('Event updated and resubmitted for admin review!');
        cancelEdit();
      } else {
        await api('/api/events', {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify(data)
        });
        toast('Event submitted successfully for review!');
        form.reset();
      }

      await loadOrganizerListings();
    } catch (err) {
      toast(err.message || 'Could not submit event.');
    } finally {
      if (submitBtn) submitBtn.disabled = false;
    }
  });

  $('#cancel-edit-btn')?.addEventListener('click', cancelEdit);

  document.addEventListener('ayojon:viewchange', e => {
    if (e.detail?.view === 'organizer') {
      loadOrganizerListings();
    }
  });
}