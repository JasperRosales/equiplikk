import { api } from '../api.js';
import { state } from '../state.js';
import { router } from '../router.js';
import {
  esc, table, page, empty, handleError, openModal, confirmDialog,
  statusBadge, withBusy, toast
} from '../ui.js';
import { icon } from '../icons.js';

export async function renderEquipment(container) {
  container.innerHTML = '<div class="card empty">Loading equipment…</div>';
  try {
    const rows = await api.equipment();
    const categories = unique(rows.map((item) => item.category));
    const custodians = unique(rows.map((item) => item.custodianRole));
    const filters = equipmentFilterBar('available', rows, { categories, custodians });

    container.innerHTML = page('Available Equipment', 'Items currently ready for borrowing', '',
      `<div class="card stack">
        ${filters}
        <div id="availableGrid" class="grid three"></div>
        <p class="hint" id="availableCount"></p>
      </div>`);

    const paint = () => {
      const values = readEquipmentFilters('available');
      const visible = rows.filter((item) => matchesEquipment(item, values));
      container.querySelector('#availableGrid').innerHTML = visible.length
        ? visible.map(equipmentCard).join('')
        : empty('No equipment matches these filters.');
      container.querySelector('#availableCount').textContent =
        `Showing ${visible.length} of ${rows.length} item type${rows.length === 1 ? '' : 's'}.`;
      container.querySelectorAll('[data-request]').forEach((button) =>
        button.addEventListener('click', () => router.go(`/request?equipment=${button.dataset.request}`)));
    };
    bindEquipmentFilters(container, 'available', paint);
    paint();
  } catch (error) {
    container.innerHTML = page('Available Equipment', '', '', `<div class="alert error">${esc(error.message)}</div>`);
  }
}

export async function renderInventory(container) {
  container.innerHTML = '<div class="card empty">Loading inventory…</div>';
  try {
    const readOnly = isFacilityAdmin();
    const [rows, liveCounters] = await Promise.all([
      api.equipment(),
      api.equipmentCounters().catch(() => null)
    ]);
    const counters = liveCounters || derivedCounters(rows);
    const cards = [
      ['Equipment types', counters.types], ['Total units', counters.totalUnits],
      ['Available units', counters.availableUnits], ['Reserved today', counters.reservedToday],
      ['In use today', counters.inUseToday]
    ].map(([label, value]) => `<div class="card stat-card"><span class="value">${esc(value ?? '—')}</span><span class="label">${esc(label)}</span></div>`).join('');

    const categories = unique(rows.map((item) => item.category));
    const custodians = unique(rows.map((item) => item.custodianRole));
    const statuses = unique(rows.map((item) => item.status));
    const filters = equipmentFilterBar('inventory', rows, { categories, custodians, statuses, quantities: true });
    const headerActions = readOnly
      ? '<button class="btn small" id="refreshInventory">Refresh</button>'
      : `<button class="btn primary" id="addEquipment">${icon('plus', 15)} Add equipment</button>`;
    const subtitle = readOnly
      ? 'Read-only monitoring of both equipment inventories'
      : 'Equipment types and live derived quantities';

    container.innerHTML = page('Inventory', subtitle, headerActions,
      `<div class="grid cards">${cards}</div>
       <div class="card stack">
         ${filters}
         <div id="inventoryTable"></div>
         <p class="hint" id="inventoryCount"></p>
       </div>`);

    const paint = () => {
      const values = readEquipmentFilters('inventory');
      const visible = rows.filter((item) => matchesEquipment(item, values));
      const target = container.querySelector('#inventoryTable');
      if (readOnly) {
        target.innerHTML = visible.length
          ? `<div class="inventory-monitor-grid">${visible.map(inventoryMonitorCard).join('')}</div>`
          : empty('No equipment matches these filters.');
      } else {
        const body = visible.map((item) => [
          esc(item.equipmentCode), esc(item.equipmentName), esc(item.category),
          esc(item.custodianRole || '—'), esc(item.totalQuantity), esc(item.availableQuantity),
          esc(item.inUseQuantity), esc(item.damagedQuantity), esc(item.lostQuantity),
          statusBadge(item.status),
          `<div class="actions"><button class="btn small secondary" data-edit="${item.equipmentId}">Edit</button>
           <button class="btn small danger" data-delete="${item.equipmentId}">Delete</button>
           <button class="btn small" data-usage="${item.equipmentId}">Usage</button></div>`
        ]);
        target.innerHTML = table(
          ['Code', 'Name', 'Category', 'Custodian', 'Total', 'Available', 'In use', 'Damaged', 'Lost', 'Status', 'Actions'],
          body, 'No equipment matches these filters.');
      }
      container.querySelector('#inventoryCount').textContent =
        `Showing ${visible.length} of ${rows.length} equipment type${rows.length === 1 ? '' : 's'}.`;
      bindInventoryActions(container, rows, readOnly);
    };

    bindEquipmentFilters(container, 'inventory', paint);
    if (readOnly) {
      container.querySelector('#refreshInventory').addEventListener('click', () => renderInventory(container));
    } else {
      container.querySelector('#addEquipment').addEventListener('click', () => openEquipmentModal(null));
    }
    paint();
  } catch (error) {
    container.innerHTML = page('Inventory', '', '', `<div class="alert error">${esc(error.message)}</div>`);
  }
}

function equipmentCard(item) {
  const badges = [
    `<span class="badge">${esc(item.availableQuantity)} available</span>`,
    Number(item.inUseQuantity) ? `<span class="badge">${esc(item.inUseQuantity)} in use</span>` : '',
    Number(item.damagedQuantity) ? `<span class="badge">${esc(item.damagedQuantity)} damaged</span>` : '',
    Number(item.lostQuantity) ? `<span class="badge">${esc(item.lostQuantity)} lost</span>` : ''
  ].filter(Boolean).join('');
  return `
    <div class="card equipment-card">
      <div class="spread"><h3>${esc(item.equipmentName)}</h3>${statusBadge(item.status)}</div>
      <p class="muted">${esc(item.equipmentCode)} · ${esc(item.category)}</p>
      <div class="row stock-line">
        ${badges}
        ${item.custodianRole ? `<span class="muted">${esc(item.custodianRole.replaceAll('_', ' '))}</span>` : ''}
        <span class="muted">${esc(item.totalQuantity)} total</span>
      </div>
      ${state.user?.role === 'BORROWER' ? `<div class="divider"></div><button class="btn primary small" data-request="${item.equipmentId}">Request this item</button>` : ''}
    </div>`;
}

function isFacilityAdmin() {
  return String(state.user?.role || '').toUpperCase() === 'ADMIN';
}

function dash(value) {
  return value === null || value === undefined || value === '' ? '—' : value;
}

function displayCustodian(value) {
  return value ? String(value).replaceAll('_', ' ') : '—';
}

/** Read-only monitoring card: labels left, values right, Usage as the only action. */
function inventoryMonitorCard(item) {
  const rows = [
    ['CODE', esc(dash(item.equipmentCode))],
    ['NAME', esc(dash(item.equipmentName))],
    ['CATEGORY', esc(dash(item.category))],
    ['CUSTODIAN', esc(displayCustodian(item.custodianRole))],
    ['TOTAL', esc(dash(item.totalQuantity))],
    ['AVAILABLE', esc(dash(item.availableQuantity))],
    ['IN USE', esc(dash(item.inUseQuantity))],
    ['DAMAGED', esc(dash(item.damagedQuantity))],
    ['LOST', esc(dash(item.lostQuantity))],
    ['STATUS', statusBadge(item.status)],
    ['ACTIONS', `<button class="btn small" data-usage="${esc(item.equipmentId)}">Usage</button>`]
  ];
  return `
    <div class="card inventory-monitor-card">
      <dl class="inventory-spec">
        ${rows.map(([label, value]) => `<div class="inventory-spec-row"><dt>${label}</dt><dd>${value}</dd></div>`).join('')}
      </dl>
    </div>`;
}

function usageDetail(label, value) {
  return `<div class="usage-detail"><span class="label">${esc(label)}</span><span>${esc(dash(value))}</span></div>`;
}

function usageDateTime(value) {
  if (!value) return '—';
  return String(value).replace('T', ' · ');
}

function usageSchedule(row) {
  if (!row.eventDate && !row.startTime && !row.endTime) return '—';
  const times = [row.startTime, row.endTime].filter(Boolean)
    .map((time) => String(time).slice(0, 5)).join(' – ');
  return `${dash(row.eventDate)}${times ? ' · ' + times : ''}`;
}

/** One released hand-over; no action controls are rendered. */
function usageRecordCard(row) {
  const request = `${esc(dash(row.requestCode))}${row.requestId ? ' · #' + esc(row.requestId) : ''}`;
  const identity = [row.borrowerType, row.department].filter(Boolean)
    .map((value) => String(value).replaceAll('_', ' ')).join(' · ') || '—';
  const returned = Number(row.returnedQuantity) || 0;
  const missing = Number(row.missingQuantity) || 0;
  const actualReturn = returned || missing
    ? `${returned} returned${missing ? ` · ${missing} missing` : ''}`
    : 'Not yet recorded';
  return `
    <div class="card usage-record">
      <div class="spread">
        <div>
          <h3>${esc(dash(row.borrowerName || row.fullName))}</h3>
          <p class="muted">Request ${request} · ${esc(identity)}</p>
        </div>
        ${statusBadge(row.groupStatus)}
      </div>
      <div class="usage-details">
        ${usageDetail('Venue', row.venue)}
        ${usageDetail('Expected return', usageSchedule(row))}
        ${usageDetail('Released', usageDateTime(row.releasedAt))}
        ${usageDetail('Quantity currently in use', row.inUseQuantity)}
        ${usageDetail('Actual return', actualReturn)}
        ${usageDetail('Released quantity', row.releasedQuantity)}
        ${usageDetail('Returned quantity', row.returnedQuantity)}
        ${usageDetail('Missing quantity', row.missingQuantity)}
      </div>
    </div>`;
}

function openUsageModal(item, usage, readOnly) {
  if (!readOnly) {
    // Staff keep the existing compact usage table and permissions.
    const modal = openModal({
      title: 'Active borrowers',
      body: usage.length ? table(['Borrower', 'Request', 'Quantity', 'Due'],
        usage.map((row) => [esc(row.borrowerName || row.fullName), esc(row.requestCode),
          esc(row.quantity || row.releasedQuantity), esc(row.eventDate || row.dueDate)])) : empty('No active borrowers for this item.'),
      footer: '<button class="btn" data-close>Close</button>'
    });
    modal.root.querySelector('[data-close]').addEventListener('click', modal.close);
    return;
  }

  const modal = openModal({
    title: `Equipment usage · ${item.equipmentName || 'Equipment'}`,
    wide: true,
    body: `
      <div class="usage-summary">
        <span class="value">${esc(usage.length)}</span>
        <span class="label">active hand-over${usage.length === 1 ? '' : 's'} · ${esc(dash(item.inUseQuantity))} unit(s) in use</span>
      </div>
      ${usage.length
        ? `<div class="usage-records">${usage.map(usageRecordCard).join('')}</div>`
        : empty('No active borrowers for this equipment.')}`,
    footer: '<button class="btn" data-close>Close</button>'
  });
  modal.root.querySelectorAll('[data-close]').forEach((button) =>
    button.addEventListener('click', modal.close));
}

function equipmentFilterBar(prefix, rows, options = {}) {
  const optionTags = (values) => values.map((value) =>
    `<option value="${esc(value)}">${esc(String(value).replaceAll('_', ' '))}</option>`).join('');
  return `
    <div class="filters card-filters" id="${prefix}Filters">
      <label class="field">Search
        <input id="${prefix}Search" type="search" placeholder="Code, name, category or custodian">
      </label>
      <label class="field">Category
        <select id="${prefix}Category"><option value="">All categories</option>${optionTags(options.categories || [])}</select>
      </label>
      <label class="field">Availability
        <select id="${prefix}Availability">
          <option value="">Any availability</option>
          <option value="available">In stock</option>
          <option value="unavailable">Out of stock</option>
        </select>
      </label>
      ${(options.custodians || []).length ? `<label class="field">Custodian
        <select id="${prefix}Custodian"><option value="">All custodians</option>${optionTags(options.custodians)}</select>
      </label>` : ''}
      ${(options.statuses || []).length ? `<label class="field">Status
        <select id="${prefix}Status"><option value="">All statuses</option>${optionTags(options.statuses)}</select>
      </label>` : ''}
      ${options.quantities ? `<label class="field">Quantity
        <select id="${prefix}Quantity">
          <option value="">Any quantity</option>
          <option value="low">Low stock (1–2)</option>
          <option value="out">None available</option>
        </select>
      </label>` : ''}
      <button type="button" class="btn small" id="${prefix}Clear">Clear filters</button>
    </div>`;
}

function bindEquipmentFilters(container, prefix, paint) {
  const inputs = container.querySelectorAll(`#${prefix}Filters input, #${prefix}Filters select`);
  inputs.forEach((input) => input.addEventListener(input.tagName === 'INPUT' ? 'input' : 'change', paint));
  container.querySelector(`#${prefix}Clear`).addEventListener('click', () => {
    inputs.forEach((input) => { input.value = ''; });
    paint();
  });
}

function readEquipmentFilters(prefix) {
  const value = (id) => document.getElementById(`${prefix}${id}`)?.value || '';
  return {
    search: value('Search').trim().toLowerCase(),
    category: value('Category'),
    availability: value('Availability'),
    custodian: value('Custodian'),
    status: value('Status'),
    quantity: value('Quantity')
  };
}

function matchesEquipment(item, filters) {
  const haystack = [item.equipmentCode, item.equipmentName, item.category, item.custodianRole]
    .filter(Boolean).join(' ').toLowerCase();
  if (filters.search && !haystack.includes(filters.search)) return false;
  if (filters.category && item.category !== filters.category) return false;
  if (filters.custodian && item.custodianRole !== filters.custodian) return false;
  if (filters.status && item.status !== filters.status) return false;
  const available = Number(item.availableQuantity) || 0;
  if (filters.availability === 'available' && available <= 0) return false;
  if (filters.availability === 'unavailable' && available > 0) return false;
  if (filters.quantity === 'low' && (available < 1 || available > 2)) return false;
  if (filters.quantity === 'out' && available !== 0) return false;
  return true;
}

function bindInventoryActions(container, rows, readOnly) {
  if (!readOnly) {
    container.querySelectorAll('[data-edit]').forEach((button) =>
      button.addEventListener('click', () =>
        openEquipmentModal(rows.find((row) => row.equipmentId === Number(button.dataset.edit)))));
    container.querySelectorAll('[data-delete]').forEach((button) =>
      button.addEventListener('click', async () => {
        if (!await confirmDialog('Delete this equipment type? Historical requests stay intact.', 'Delete equipment')) return;
        try {
          await api.deleteEquipment(Number(button.dataset.delete));
          toast('Equipment deleted.', 'success');
          renderInventory(container);
        } catch (error) { handleError(error); }
      }));
  }
  container.querySelectorAll('[data-usage]').forEach((button) =>
    button.addEventListener('click', async () => {
      try {
        const usage = await api.equipmentUsage(Number(button.dataset.usage));
        const item = rows.find((row) => row.equipmentId === Number(button.dataset.usage)) || {};
        openUsageModal(item, usage, readOnly);
      } catch (error) { handleError(error); }
    }));
}

function derivedCounters(rows) {
  const sum = (key, predicate = () => true) => rows.reduce(
    (total, item) => total + (predicate(item) ? Number(item[key] || 0) : 0), 0);
  return {
    types: rows.length,
    totalUnits: sum('totalQuantity'),
    availableUnits: sum('availableQuantity', (item) => item.status === 'AVAILABLE'),
    reservedToday: '—',
    inUseToday: sum('inUseQuantity')
  };
}

function unique(values) {
  return [...new Set(values.filter((value) => value !== null && value !== undefined && value !== ''))].sort();
}

function openEquipmentModal(item) {
  const modal = openModal({
    title: item ? 'Edit equipment' : 'Add equipment',
    body: `<form id="equipmentForm" class="grid two">
      <label class="field">Code<input name="code" value="${esc(item?.equipmentCode)}" required></label>
      <label class="field">Name<input name="name" value="${esc(item?.equipmentName)}" required></label>
      <label class="field">Category<input name="category" value="${esc(item?.category)}" required></label>
      <label class="field">Total quantity<input name="totalQuantity" type="number" min="0" value="${esc(item?.totalQuantity ?? 1)}" required></label>
      <label class="field">Status
        <select name="status">
          <option ${item?.status === 'AVAILABLE' ? 'selected' : ''}>AVAILABLE</option>
          <option ${item?.status === 'DAMAGED' ? 'selected' : ''}>DAMAGED</option>
          <option ${item?.status === 'LOST' ? 'selected' : ''}>LOST</option>
        </select>
      </label>
    </form>`,
    footer: `<button class="btn" data-close>Cancel</button><button class="btn primary" id="saveEquipment">Save</button>`
  });
  modal.root.querySelector('[data-close]').addEventListener('click', modal.close);
  modal.root.querySelector('#saveEquipment').addEventListener('click', async (event) => {
    const form = modal.root.querySelector('#equipmentForm');
    const data = Object.fromEntries(new FormData(form));
    data.totalQuantity = Number(data.totalQuantity);
    await withBusy(event.currentTarget, async () => {
      try {
        await api.saveEquipment(data, item?.equipmentId);
        modal.close();
        toast('Equipment saved.', 'success');
        renderInventory(document.getElementById('main'));
      } catch (error) { handleError(error); }
    });
  });
}
