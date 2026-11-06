/* --- SHELL: routes, navigation, tabs and dialogs only --- */
import { $, $$, toast } from './assets/js/shared.js';
import { currentUser, sessionLoading, initAuth } from './assets/js/auth.js';
import './assets/js/profile.js';

let activeView = '';
let eventsScroll = 0;

$('#theme-toggle').addEventListener('click', () => {
  const forest = document.documentElement.dataset.theme !== 'forest';
  document.documentElement.dataset.theme = forest ? 'forest' : 'slate';
  $('#theme-toggle').setAttribute('aria-pressed', String(forest));
  $('#theme-toggle').title = forest ? 'Switch to charcoal and blue' : 'Switch to green and gold';
  $('meta[name="theme-color"]').content = forest ? '#071510' : '#09090b';
});

/* --- COMPONENT: role previews; visibility only, never authorization --- */
function effectiveRole() {
  return currentUser ? ($('#demo-role').value || currentUser.role) : 'USER';
}

function canView(view) {
  if (['landing', 'auth'].includes(view)) return true;
  if (!currentUser) return false;
  const role = effectiveRole();
  return view === 'admin' ? Boolean(currentUser) && role === 'ADMIN' : view === 'organizer' ? Boolean(currentUser) && ['ORGANIZER', 'ADMIN'].includes(role) : true;
}

function updateRoles() {
  if (!currentUser) closeMenu();
  $$('[data-session-only]').forEach(element => element.hidden = !currentUser);
  $$('[data-guest-only]').forEach(element => element.hidden = Boolean(currentUser));
  $$('.brand').forEach(link => link.href = currentUser ? '#discover' : '#landing');
  const selector = $('#demo-role');
  selector.closest('.role-switcher').hidden = !currentUser;
  selector.disabled = !currentUser;
  if (!currentUser) selector.value = '';
  $$('[data-role]').forEach(link => link.hidden = !canView(link.dataset.nav));
}

$('#demo-role').addEventListener('change', () => {
  updateRoles();
  if (!currentUser) return;
  if (!canView(activeView)) location.hash = 'discover';
  toast($('#demo-role').value ? 'Preview role updated. Your account permissions are unchanged.' : 'Showing your account role.');
});

/* --- NAVIGATION: route aliases preserve existing shared links --- */
const aliases = {home:'landing', explore:'events', plans:'planner', 'my-events':'profile', rewards:'profile', 'how-it-works':'landing', organizers:'organizer'};
const viewTitles = {landing:'Meet Ayojon', discover:'Discover', events:'Events', 'event-detail':'Event details', saved:'Saved', planner:'Planner', leaderboard:'Leaderboard', profile:'Profile', organizer:'Organizer Hub', admin:'Admin Panel', auth:'Your account'};

function readRoute() {
  const [raw = '', query = ''] = location.hash.slice(1).split('?');
  const name = raw.replace(/-view$/, '') || (currentUser ? 'discover' : 'landing');
  return {name:aliases[name] || name, params:new URLSearchParams(query)};
}

function closeMenu() {
  $('#primary-nav').classList.remove('open');
  $('.menu-toggle').setAttribute('aria-expanded', 'false');
  $('.menu-toggle').setAttribute('aria-label', 'Open navigation');
}

function route() {
  const {name, params} = readRoute();
  const view = ['login', 'signup'].includes(name) ? 'auth' : (viewTitles[name] ? name : (currentUser ? 'discover' : 'landing'));
  if (currentUser && ['landing', 'auth'].includes(view)) {
    location.replace('#discover');
    return;
  }
  if (!canView(view)) {
    if (sessionLoading) return;
    if (currentUser) toast('Choose the matching preview role to open this workspace.');
    location.replace(currentUser ? '#discover' : '#signup');
    return;
  }
  const previous = activeView;
  if (previous === 'events') eventsScroll = window.scrollY;
  activeView = view;
  $$('.view-panel').forEach(panel => panel.hidden = panel.id !== `${view}-view`);
  $$('[data-nav]').forEach(link => {
    const active = link.dataset.nav === (view === 'event-detail' ? 'events' : view);
    link.classList.toggle('active', active);
    if (active) link.setAttribute('aria-current', 'page');
    else link.removeAttribute('aria-current');
  });
  document.title = `${viewTitles[view]} — Ayojon`;
  document.dispatchEvent(new CustomEvent('ayojon:viewchange', {detail:{name, view, params}}));
  closeMenu();
  $$('dialog[open]').forEach(dialog => dialog.close());
  window.scrollTo({top:view === 'events' && previous === 'event-detail' ? eventsScroll : 0, behavior:'instant'});
  if (view === 'landing' && ['how-it-works', 'for-organizers'].includes(params.get('section'))) {
    document.getElementById(params.get('section')).scrollIntoView();
  }
  $('#main').focus({preventScroll:true});
}

$('.menu-toggle').addEventListener('click', () => {
  const open = $('#primary-nav').classList.toggle('open');
  $('.menu-toggle').setAttribute('aria-expanded', String(open));
  $('.menu-toggle').setAttribute('aria-label', open ? 'Close navigation' : 'Open navigation');
});
$('#primary-nav').addEventListener('click', event => { if (event.target.closest('a')) closeMenu(); });
document.addEventListener('keydown', event => {
  if (event.key === 'Escape' && $('#primary-nav').classList.contains('open')) {
    closeMenu();
    $('.menu-toggle').focus();
  }
});

/* --- COMPONENT: local tabs, including keyboard navigation --- */
function selectTab(tab) {
  const list = tab.closest('[role=tablist]');
  list.querySelectorAll('[role=tab]').forEach(item => {
    const selected = item === tab;
    item.setAttribute('aria-selected', String(selected));
    item.tabIndex = selected ? 0 : -1;
    document.getElementById(item.getAttribute('aria-controls')).hidden = !selected;
  });
}
$$('[role=tablist]').forEach(list => {
  list.addEventListener('click', event => {
    const tab = event.target.closest('[role=tab]');
    if (tab) selectTab(tab);
  });
  list.addEventListener('keydown', event => {
    const tabs = [...list.querySelectorAll('[role=tab]')];
    const index = tabs.indexOf(document.activeElement);
    if (index < 0 || !['ArrowLeft','ArrowRight','Home','End'].includes(event.key)) return;
    event.preventDefault();
    const next = event.key === 'Home' ? 0 : event.key === 'End' ? tabs.length - 1 : (index + (event.key === 'ArrowRight' ? 1 : -1) + tabs.length) % tabs.length;
    selectTab(tabs[next]);
    tabs[next].focus();
  });
});

/* --- COMPONENT: modal dialogs and feature placeholders --- */
document.addEventListener('click', event => {
  const opener = event.target.closest('[data-dialog]');
  if (opener) document.getElementById(opener.dataset.dialog).showModal();
  const closer = event.target.closest('[data-close-dialog]');
  if (closer) closer.closest('dialog').close();
  if (!event.target.closest('.site-header')) closeMenu();
});
$$('dialog').forEach(dialog => dialog.addEventListener('click', event => {
  if (event.target !== dialog) return;
  const bounds = dialog.getBoundingClientRect();
  if (event.clientX < bounds.left || event.clientX > bounds.right || event.clientY < bounds.top || event.clientY > bounds.bottom) dialog.close();
}));


document.addEventListener('ayojon:sessionchange', updateRoles);
document.addEventListener('ayojon:sessionready', () => {
  if (activeView !== 'auth' || currentUser) route();
});
$('#year').textContent = new Date().getFullYear();
window.addEventListener('hashchange', route);
updateRoles();
route();
initAuth();
