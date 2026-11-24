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
    const cards = rows.map((item) => `
      <div class="card">
        <div class="spread"><h3>${esc(item.equipmentName)}</h3>${statusBadge(item.status)}</div>
        <p class="muted">${esc(item.equipmentCode)} · ${esc(item.category)}</p>
        <div class="row"><span class="badge">${esc(item.availableQuantity)} available</span>
        ${item.inUseQuantity ? `<span class="badge">${esc(item.inUseQuantity)} in use</span>` : ''}
        ${item.custodianRole ? `<span class="muted">${esc(item.custodianRole.replace('_', ' '))}</span>` : ''}</div>
        ${state.user?.role === 'BORROWER' ? `<div class="divider"></div><button class="btn primary small" data-request="${item.equipmentId}">Request this item</button>` : ''}
      </div>`).join('');
    container.innerHTML = page('Available Equipment', 'Items currently ready for borrowing', '',
      cards ? `<div class="grid three">${cards}</div>` : empty('No equipment is available right now.'));
    container.querySelectorAll('[data-request]').forEach((button) =>
      button.addEventListener('click', () => router.go(`/request?equipment=${button.dataset.request}`)));
  } catch (error) {
    container.innerHTML = page('Available Equipment', '', '', `<div class="alert error">${esc(error.message)}</div>`);
  }
}

export async function renderInventory(container) {
  container.innerHTML = '<div class="card empty">Loading inventory…</div>';
  try {
    const [rows, counters] = await Promise.all([api.equipment('all'), api.equipmentCounters()]);
    const cards = [
      ['Equipment types', counters.types], ['Total units', counters.totalUnits],
      ['Available units', counters.availableUnits], ['Reserved today', counters.reservedToday],
      ['In use today', counters.inUseToday]
    ].map(([label, value]) => `<div class="card stat-card"><span class="value">${esc(value)}</span><span class="label">${esc(label)}</span></div>`).join('');

    const body = rows.map((item) => [
      esc(item.equipmentCode), esc(item.equipmentName), esc(item.category),
      esc(item.totalQuantity), esc(item.damagedQuantity), esc(item.lostQuantity),
      esc(item.availableQuantity), esc(item.inUseQuantity), statusBadge(item.status),
      `<div class="actions"><button class="btn small secondary" data-edit="${item.equipmentId}">Edit</button>
       <button class="btn small danger" data-delete="${item.equipmentId}">Delete</button>
       <button class="btn small" data-usage="${item.equipmentId}">Usage</button></div>`
    ]);

    container.innerHTML = page('Inventory', 'Equipment types and live derived quantities',
      `<button class="btn primary" id="addEquipment">${icon('plus', 15)} Add equipment</button>`,
      `<div class="grid cards">${cards}</div>
       ${table(['Code', 'Name', 'Category', 'Total', 'Damaged', 'Lost', 'Available', 'In use', 'Status', 'Actions'], body, 'No equipment has been added yet.')}`);

    document.getElementById('addEquipment').addEventListener('click', () => openEquipmentModal(null));
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
    container.querySelectorAll('[data-usage]').forEach((button) =>
      button.addEventListener('click', async () => {
        try {
          const usage = await api.equipmentUsage(Number(button.dataset.usage));
          const modal = openModal({
            title: 'Active borrowers',
            body: usage.length ? table(['Borrower', 'Request', 'Quantity', 'Due'],
              usage.map((row) => [esc(row.borrowerName || row.fullName), esc(row.requestCode),
                esc(row.quantity || row.releasedQuantity), esc(row.eventDate || row.dueDate)])) : empty('No active borrowers for this item.'),
            footer: '<button class="btn" data-close>Close</button>'
          });
          modal.root.querySelector('[data-close]').addEventListener('click', modal.close);
        } catch (error) { handleError(error); }
      }));
  } catch (error) {
    container.innerHTML = page('Inventory', '', '', `<div class="alert error">${esc(error.message)}</div>`);
  }
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
