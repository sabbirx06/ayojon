import { $, $$, escapeHtml, toast, api } from './shared.js';
import { currentUser } from './auth.js';

let currentReviewEvent = null;

function checkAdminAccess() {
  const currentRole = currentUser ? ($('#demo-role')?.value || currentUser.role) : 'USER';
  return Boolean(currentUser && currentRole === 'ADMIN');
}

export async function loadDashboardStats() {
  if (!checkAdminAccess()) return;
  try {
    const stats = await api('/api/admin/stats');
    if ($('#stat-pending-approvals')) $('#stat-pending-approvals').textContent = stats.pendingApprovals;
    if ($('#stat-total-events')) $('#stat-total-events').textContent = stats.totalEvents;
    if ($('#stat-total-users')) $('#stat-total-users').textContent = stats.totalUsers;
    if ($('#stat-open-disputes')) $('#stat-open-disputes').textContent = stats.pendingDisputes;
  } catch {
    // Graceful fallback
  }
}

export async function loadPendingApprovals() {
  const tbody = $('#pending-approval-rows');
  if (!tbody) return;

  if (!checkAdminAccess()) {
    tbody.innerHTML = `<tr><td colspan="4" class="table-empty">Admin access required.</td></tr>`;
    return;
  }

  tbody.innerHTML = `<tr><td colspan="4" class="table-empty">Loading pending event submissions...</td></tr>`;

  try {
    const list = await api('/api/admin/events/pending');

    const tab = $('#approvals-tab');
    if (tab) tab.textContent = `Pending Event Approvals (${list.length})`;

    if (!list || list.length === 0) {
      tbody.innerHTML = `<tr><td colspan="4" class="table-empty">No pending event submissions to review. All caught up!</td></tr>`;
      return;
    }

    tbody.innerHTML = list.map(event => {
      let statusTag = '';
      if (event.latestDecision === 'RESUBMITTED') {
        statusTag = `<span class="status-badge status-resubmitted">⟳ Resubmitted</span>`;
      } else if (event.latestNote && event.latestNote.startsWith('[REVISION]')) {
        statusTag = `<span class="status-badge status-revision">⚠ Awaiting Revision</span>`;
      } else {
        statusTag = `<span class="status-badge status-pending">⏳ New</span>`;
      }

      const submittedDate = event.createdAt ? new Date(event.createdAt).toLocaleDateString('en-US', {
        month: 'short', day: 'numeric', year: 'numeric'
      }) : '—';

      return `
        <tr data-event-row="${event.eventId}">
          <td>
            <div class="table-event-cell">
              <strong>${escapeHtml(event.title)}</strong>
              <small class="muted">${escapeHtml(event.categoryName || '')} · ⌖ ${escapeHtml(event.venueName || '')}</small>
              <div style="margin-top:4px;">${statusTag}</div>
            </div>
          </td>
          <td>${escapeHtml(event.organizerName || 'Organizer')}</td>
          <td>${escapeHtml(submittedDate)}</td>
          <td>
            <button type="button" class="btn btn-sm btn-primary" data-action="review-event" data-id="${event.eventId}">Review ↗</button>
          </td>
        </tr>`;
    }).join('');

    tbody.querySelectorAll('[data-action="review-event"]').forEach(btn => {
      btn.addEventListener('click', () => {
        const id = parseInt(btn.dataset.id, 10);
        const item = list.find(e => e.eventId === id);
        if (item) openReviewModal(item);
      });
    });

  } catch (err) {
    tbody.innerHTML = `<tr><td colspan="4" class="table-empty form-error">Could not load pending events: ${escapeHtml(err.message)}</td></tr>`;
  }
}

export async function loadAllOrganizerEvents() {
  const tbody = $('#all-events-rows');
  if (!tbody) return;

  if (!checkAdminAccess()) {
    tbody.innerHTML = `<tr><td colspan="4" class="table-empty">Admin access required.</td></tr>`;
    return;
  }

  tbody.innerHTML = `<tr><td colspan="4" class="table-empty">Loading all organizer events...</td></tr>`;

  try {
    const list = await api('/api/admin/events');

    const tab = $('#all-events-tab');
    if (tab) tab.textContent = `All Organizer Events (${list.length})`;

    if (!list || list.length === 0) {
      tbody.innerHTML = `<tr><td colspan="4" class="table-empty">No events found across organizers.</td></tr>`;
      return;
    }

    tbody.innerHTML = list.map(event => {
      let statusTag = '';
      if (event.status === 'PUBLISHED') {
        statusTag = `<span class="status-badge status-approved">✓ Published</span>`;
      } else if (event.status === 'CANCELLED') {
        statusTag = `<span class="status-badge status-rejected">✕ Rejected</span>`;
      } else if (event.latestDecision === 'RESUBMITTED') {
        statusTag = `<span class="status-badge status-resubmitted">⟳ Resubmitted</span>`;
      } else if (event.latestNote && event.latestNote.startsWith('[REVISION]')) {
        statusTag = `<span class="status-badge status-revision">⚠ Revision Requested</span>`;
      } else {
        statusTag = `<span class="status-badge status-pending">⏳ Pending Review</span>`;
      }

      return `
        <tr>
          <td>
            <div class="table-event-cell">
              <strong>${escapeHtml(event.title)}</strong>
              <small class="muted">${escapeHtml(event.categoryName || '')} · ⌖ ${escapeHtml(event.venueName || '')}</small>
            </div>
          </td>
          <td>${escapeHtml(event.organizerName || 'Organizer')}</td>
          <td>${statusTag}</td>
          <td>
            <button type="button" class="btn btn-sm btn-outline" data-action="inspect-event" data-id="${event.eventId}">Details ↗</button>
          </td>
        </tr>`;
    }).join('');

    tbody.querySelectorAll('[data-action="inspect-event"]').forEach(btn => {
      btn.addEventListener('click', () => {
        const id = parseInt(btn.dataset.id, 10);
        const item = list.find(e => e.eventId === id);
        if (item) openReviewModal(item);
      });
    });

  } catch (err) {
    tbody.innerHTML = `<tr><td colspan="4" class="table-empty form-error">Could not load all events: ${escapeHtml(err.message)}</td></tr>`;
  }
}

export async function loadUsers() {
  const tbody = $('#admin-user-rows');
  if (!tbody) return;

  if (!checkAdminAccess()) {
    tbody.innerHTML = `<tr><td colspan="4" class="table-empty">Admin access required.</td></tr>`;
    return;
  }

  tbody.innerHTML = `<tr><td colspan="4" class="table-empty">Loading user accounts...</td></tr>`;

  try {
    const list = await api('/api/admin/users');

    const tab = $('#users-tab');
    if (tab) tab.textContent = `Users & Roles (${list.length})`;

    if (!list || list.length === 0) {
      tbody.innerHTML = `<tr><td colspan="4" class="table-empty">No users found.</td></tr>`;
      return;
    }

    tbody.innerHTML = list.map(u => {
      let roleBadge = `<span class="badge">${escapeHtml(u.role)}</span>`;
      if (u.role === 'ADMIN') {
        roleBadge = `<span class="status-badge status-approved">ADMIN</span>`;
      } else if (u.role === 'ORGANIZER') {
        roleBadge = `<span class="status-badge status-resubmitted">ORGANIZER</span>`;
      } else {
        roleBadge = `<span class="status-badge status-pending">ATTENDEE</span>`;
      }

      return `
        <tr>
          <td><strong>${escapeHtml(u.name)}</strong></td>
          <td>${escapeHtml(u.email)}</td>
          <td>${roleBadge}</td>
          <td>${u.points ?? 0} pts</td>
        </tr>`;
    }).join('');
  } catch (err) {
    tbody.innerHTML = `<tr><td colspan="4" class="table-empty form-error">Could not load users: ${escapeHtml(err.message)}</td></tr>`;
  }
}

export async function loadDisputes() {
  const tbody = $('#dispute-report-rows');
  if (!tbody) return;

  if (!checkAdminAccess()) {
    tbody.innerHTML = `<tr><td colspan="5" class="table-empty">Admin access required.</td></tr>`;
    return;
  }

  tbody.innerHTML = `<tr><td colspan="5" class="table-empty">Loading dispute reports...</td></tr>`;

  try {
    const list = await api('/api/admin/disputes');

    const tab = $('#disputes-tab');
    if (tab) tab.textContent = `Dispute Reports (${list.length})`;

    if (!list || list.length === 0) {
      tbody.innerHTML = `<tr><td colspan="5" class="table-empty">No community disputes reported. All clear!</td></tr>`;
      return;
    }

    tbody.innerHTML = list.map(d => {
      let statusTag = '';
      if (d.status === 'RESOLVED') {
        statusTag = `<span class="status-badge status-approved">✓ Resolved</span>`;
      } else if (d.status === 'DISMISSED') {
        statusTag = `<span class="status-badge status-rejected">Dismissed</span>`;
      } else {
        statusTag = `<span class="status-badge status-pending">⏳ ${escapeHtml(d.status)}</span>`;
      }

      const actions = d.status === 'RESOLVED' || d.status === 'DISMISSED'
        ? `<small class="muted">Archived</small>`
        : `<div class="action-buttons-row" style="margin:0;">
             <button type="button" class="btn btn-sm btn-primary" data-dispute-resolve="${d.reportId}">Resolve</button>
             <button type="button" class="btn btn-sm btn-outline" data-dispute-dismiss="${d.reportId}">Dismiss</button>
           </div>`;

      return `
        <tr>
          <td><strong>${escapeHtml(d.eventTitle || `Event #${d.eventId}`)}</strong></td>
          <td>${escapeHtml(d.reporterName || `User #${d.reporterId}`)}</td>
          <td>
            <strong>${escapeHtml(d.reason)}</strong>
            ${d.details ? `<p class="muted small" style="margin:2px 0 0;">${escapeHtml(d.details)}</p>` : ''}
          </td>
          <td>${statusTag}</td>
          <td>${actions}</td>
        </tr>`;
    }).join('');

    tbody.querySelectorAll('[data-dispute-resolve]').forEach(btn => {
      btn.addEventListener('click', async () => {
        const id = btn.dataset.disputeResolve;
        try {
          await api(`/api/admin/disputes/${id}/status`, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ status: 'RESOLVED' })
          });
          toast('Dispute marked as RESOLVED.');
          await loadDisputes();
          await loadDashboardStats();
        } catch (e) {
          toast(e.message || 'Could not update dispute status.');
        }
      });
    });

    tbody.querySelectorAll('[data-dispute-dismiss]').forEach(btn => {
      btn.addEventListener('click', async () => {
        const id = btn.dataset.disputeDismiss;
        try {
          await api(`/api/admin/disputes/${id}/status`, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ status: 'DISMISSED' })
          });
          toast('Dispute marked as DISMISSED.');
          await loadDisputes();
          await loadDashboardStats();
        } catch (e) {
          toast(e.message || 'Could not update dispute status.');
        }
      });
    });

  } catch (err) {
    tbody.innerHTML = `<tr><td colspan="5" class="table-empty form-error">Could not load disputes: ${escapeHtml(err.message)}</td></tr>`;
  }
}

export async function openReviewModal(event) {
  currentReviewEvent = event;
  const modal = $('#admin-review-modal');
  if (!modal) return;

  $('#modal-event-title').textContent = event.title || 'Untitled Event';
  $('#modal-event-organizer').textContent = event.organizerName || 'Unknown';
  $('#modal-event-category').textContent = event.categoryName || 'General';
  $('#modal-event-venue').textContent = event.venueName ? `${event.venueName} (${event.areaName || ''})` : 'TBD';
  $('#modal-event-schedule').textContent = `${formatDateTime(event.startTime)} → ${formatDateTime(event.endTime)}`;
  $('#modal-event-description').textContent = event.description || 'No description provided.';
  
  const ticketLinkEl = $('#modal-event-tickets');
  if (event.ticketUrl) {
    ticketLinkEl.href = event.ticketUrl;
    ticketLinkEl.textContent = event.ticketUrl;
    ticketLinkEl.parentElement.hidden = false;
  } else {
    ticketLinkEl.parentElement.hidden = true;
  }

  // Load review history
  const historyContainer = $('#modal-review-history');
  if (historyContainer) {
    try {
      const history = await api(`/api/admin/events/${event.eventId}/reviews`);
      if (history && history.length > 0) {
        historyContainer.hidden = false;
        historyContainer.innerHTML = `
          <h4>Review History</h4>
          <ul class="review-history-list">
            ${history.map(r => `
              <li>
                <strong>${escapeHtml(r.decision)}</strong> by ${escapeHtml(r.adminName)} 
                <span class="muted small">${formatDateTime(r.verifiedAt)}</span>
                ${r.note ? `<p class="review-history-note">${escapeHtml(r.note.replace(/^\[REVISION\]\s*/, ''))}</p>` : ''}
              </li>
            `).join('')}
          </ul>`;
      } else {
        historyContainer.hidden = true;
      }
    } catch {
      historyContainer.hidden = true;
    }
  }

  // Reset decision action box
  const actionForm = $('#modal-review-form');
  if (actionForm) actionForm.reset();
  $('#review-note-container').hidden = true;
  $('#btn-confirm-action').hidden = true;
  $('#review-action-selection').hidden = false;

  modal.showModal();
}

function formatDateTime(val) {
  if (!val) return 'TBD';
  return new Date(val).toLocaleString('en-US', {
    month: 'short', day: 'numeric', year: 'numeric', hour: 'numeric', minute: '2-digit'
  });
}

export function initAdminView() {
  document.addEventListener('ayojon:viewchange', e => {
    if (e.detail?.view === 'admin') {
      loadDashboardStats();
      loadPendingApprovals();
      loadAllOrganizerEvents();
      loadUsers();
      loadDisputes();
    }
  });

  $('#approvals-tab')?.addEventListener('click', loadPendingApprovals);
  $('#all-events-tab')?.addEventListener('click', loadAllOrganizerEvents);
  $('#users-tab')?.addEventListener('click', loadUsers);
  $('#disputes-tab')?.addEventListener('click', loadDisputes);

  // Wire up review modal buttons
  const modal = $('#admin-review-modal');
  if (!modal) return;

  let selectedDecision = null;

  $('#btn-approve-event')?.addEventListener('click', async () => {
    if (!currentReviewEvent) return;
    if (!confirm(`Are you sure you want to APPROVE "${currentReviewEvent.title}" and publish it?`)) return;

    try {
      await api(`/api/admin/events/${currentReviewEvent.eventId}/review`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ decision: 'APPROVED', note: 'Approved by admin' })
      });
      toast(`Event "${currentReviewEvent.title}" has been approved and published!`);
      modal.close();
      await loadDashboardStats();
      await loadPendingApprovals();
      await loadAllOrganizerEvents();
    } catch (err) {
      toast(err.message || 'Could not approve event.');
    }
  });

  $('#btn-request-revision')?.addEventListener('click', () => {
    selectedDecision = 'REVISION_REQUESTED';
    $('#review-action-selection').hidden = true;
    $('#review-note-container').hidden = false;
    $('#review-note-label').textContent = 'Please specify what the organizer should revise:';
    $('#review-note-input').placeholder = 'e.g. Please provide a more descriptive event schedule and confirm the venue booking.';
    $('#review-note-input').required = true;
    $('#btn-confirm-action').textContent = 'Send Revision Request ↗';
    $('#btn-confirm-action').className = 'btn btn-primary';
    $('#btn-confirm-action').hidden = false;
  });

  $('#btn-reject-event')?.addEventListener('click', () => {
    selectedDecision = 'REJECTED';
    $('#review-action-selection').hidden = true;
    $('#review-note-container').hidden = false;
    $('#review-note-label').textContent = 'Reason for rejection (will be sent to organizer):';
    $('#review-note-input').placeholder = 'e.g. This event does not comply with community guidelines.';
    $('#review-note-input').required = true;
    $('#btn-confirm-action').textContent = 'Confirm Rejection';
    $('#btn-confirm-action').className = 'btn btn-outline';
    $('#btn-confirm-action').hidden = false;
  });

  $('#btn-cancel-action')?.addEventListener('click', () => {
    selectedDecision = null;
    $('#review-action-selection').hidden = false;
    $('#review-note-container').hidden = true;
    $('#btn-confirm-action').hidden = true;
  });

  $('#modal-review-form')?.addEventListener('submit', async e => {
    e.preventDefault();
    if (!currentReviewEvent || !selectedDecision) return;

    const note = $('#review-note-input').value.trim();
    if (!note) {
      toast('Please enter a note.');
      return;
    }

    try {
      await api(`/api/admin/events/${currentReviewEvent.eventId}/review`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ decision: selectedDecision, note })
      });

      if (selectedDecision === 'REVISION_REQUESTED') {
        toast('Revision request sent to organizer.');
      } else {
        toast(`Event "${currentReviewEvent.title}" rejected.`);
      }
      modal.close();
      await loadDashboardStats();
      await loadPendingApprovals();
      await loadAllOrganizerEvents();
    } catch (err) {
      toast(err.message || 'Action could not be completed.');
    }
  });
}
