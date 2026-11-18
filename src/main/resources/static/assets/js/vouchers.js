import { $, api, escapeHtml } from './shared.js';

let offerRequest = 0;
let rewardsRequest = 0;

export function hideVoucher() {
  ++offerRequest;
  const box = $('#event-voucher-box');
  if (box) box.hidden = true;
}

export async function showVoucher({demo = false} = {}) {
  const box = $('#event-voucher-box');
  if (!box) return;
  const request = ++offerRequest;
  const button = box.querySelector('button');
  const status = box.querySelector('[role="status"]');
  const description = box.querySelector('[data-voucher-description]');
  if (demo) $('#event-detail-feedback').after(box);
  else $('#event-action-sidebar').insertBefore(box, $('#event-demo-note'));
  box.hidden = false;
  button.disabled = true;
  button.textContent = 'Claim Voucher';
  button.onclick = null;
  description.textContent = '';
  status.textContent = 'Loading voucher…';

  try {
    const offer = await api('/api/vouchers/demo');
    if (request !== offerRequest) return;
    description.textContent = `${offer.sponsorName} · ${offer.discountPercent}% off · Demo offer`;
    status.textContent = offer.claimed ? 'Find your code under Your rewards.' : '';
    button.textContent = offer.claimed ? '✔ Claimed' : 'Claim Voucher';
    button.disabled = offer.claimed;
    if (offer.claimed) return;

    button.onclick = async () => {
      button.disabled = true;
      button.textContent = 'Claiming…';
      status.textContent = '';
      try {
        await api('/api/vouchers/demo/claim', {method:'POST'});
        document.dispatchEvent(new CustomEvent('ayojon:voucherclaimed'));
        if (request !== offerRequest) return;
        button.textContent = '✔ Claimed';
        status.textContent = 'Find your code under Your rewards.';
      } catch (error) {
        if (request !== offerRequest) return;
        button.textContent = 'Claim Voucher';
        button.disabled = error.status === 401 || error.status === 404;
        status.textContent = error.status === 404 ? 'This voucher is no longer available.' : error.message;
      }
    };
  } catch (error) {
    if (request !== offerRequest) return;
    status.textContent = error.status === 404 ? 'No voucher is available.' : error.message;
  }
}

export async function showVoucherRewards(user) {
  const request = ++rewardsRequest;
  const rewards = $('#profile-vouchers');
  if (!rewards) return;
  rewards.textContent = user ? 'Loading rewards…' : '';
  if (!user) return;

  try {
    const vouchers = await api('/api/vouchers/me');
    if (request !== rewardsRequest) return;
    rewards.innerHTML = vouchers.length ? `<ul>${vouchers.map(voucher => `
      <li><code>${escapeHtml(voucher.promoCode)}</code> · ${Number(voucher.discountPercent)}% off · ${escapeHtml(voucher.sponsorName)}</li>
    `).join('')}</ul>` : 'No vouchers claimed yet.';
  } catch {
    if (request === rewardsRequest) rewards.textContent = 'Could not load your vouchers.';
  }
}
