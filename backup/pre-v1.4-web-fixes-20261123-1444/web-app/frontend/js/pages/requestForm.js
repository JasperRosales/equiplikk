import { api } from '../api.js';
import { state } from '../state.js';
import { esc, page, toast, handleError, withBusy } from '../ui.js';

function manilaToday() {
  return new Date().toLocaleDateString('en-CA', { timeZone: 'Asia/Manila', year: 'numeric', month: '2-digit', day: '2-digit' });
}

export async function renderRequestForm(container) {
  container.innerHTML = '<div class="card empty">Preparing request form…</div>';
  try {
    const editId = Number(state.params.get('edit') || 0);
    const [equipmentData, venueData, existing] = await Promise.all([
      api.equipment(),
      api.venues(),
      editId ? api.request(editId) : Promise.resolve(null)
    ]);
    const equipment = equipmentData.filter((item) => item.availableQuantity > 0
      || state.user?.role !== 'BORROWER'
      || (existing?.equipmentItems || []).some((row) => row.equipmentId === item.equipmentId));
    const venues = venueData.venues || [];
    const preselect = Number(state.params.get('equipment') || 0);
    const venueOptions = venues.map((venue) => `<option>${esc(venue)}</option>`).join('');
    const equipmentOptions = (selected = preselect) => equipment.map((item) =>
      `<option value="${item.equipmentId}" ${Number(selected) === item.equipmentId ? 'selected' : ''}>${esc(item.equipmentCode)} — ${esc(item.equipmentName)} (${esc(item.availableQuantity)} available)</option>`).join('');

    container.innerHTML = page(
      editId ? 'Edit Request' : 'Request Equipment',
      editId ? 'Changes restart staff review for this request' : 'Submit one request with multiple event dates and departments',
      '', `
      <form id="borrowForm" class="stack">
        <section class="card stack">
          <div class="section-title"><h3>Schedule</h3><button type="button" class="btn small secondary" id="addOccurrence">＋ Add date</button></div>
          <div id="occurrences" class="stack"></div>
        </section>
        <section class="card stack">
          <h3>Event details</h3>
          <div class="grid two">
            <label class="field">Purpose<input name="purpose" value="${esc(existing?.purpose)}" required></label>
            <label class="field">Participants<input name="participants" value="${esc(existing?.participants)}" required></label>
            <label class="field">Person in charge<input name="personInCharge" value="${esc(existing?.personInCharge)}" required></label>
            <label class="field">Position<input name="position" value="${esc(existing?.position)}" required></label>
          </div>
          <label class="field">Borrower remarks<textarea name="borrowerRemarks" maxlength="1000" placeholder="Optional notes for reviewing staff">${esc(existing?.borrowerRemarks)}</textarea></label>
          <div class="inline-check"><input type="checkbox" id="urgent" name="urgent" ${existing?.urgent ? 'checked' : ''}><label for="urgent">This request is urgent</label></div>
          <label class="field ${existing?.urgent ? '' : 'hidden'}" id="urgentReasonField">Reason for urgency<textarea name="urgentReason" maxlength="300">${esc(existing?.urgentReason)}</textarea></label>
        </section>
        <section class="card stack">
          <div class="section-title"><h3>Equipment</h3><button type="button" class="btn small secondary" id="addItem">＋ Add item</button></div>
          <div id="items" class="stack"></div>
        </section>
        <div class="row"><button type="submit" class="btn primary">${editId ? 'Save changes' : 'Submit request'}</button>
          <a class="btn" href="#/my-requests">Cancel</a></div>
      </form>`);

    const occurrences = container.querySelector('#occurrences');
    const items = container.querySelector('#items');

    const addOccurrence = (first = false, values = null) => {
      const node = document.createElement('div');
      node.className = 'occurrence grid two';
      const date = values?.eventDate || manilaToday();
      const venue = values?.venue || '';
      const start = values?.startTime || '';
      const end = values?.endTime || '';
      node.innerHTML = `
        <label class="field">Event date<input name="eventDate" type="date" value="${esc(String(date).slice(0, 10))}" required></label>
        <label class="field">Venue<select name="venue" required><option value="">Choose a venue</option>${venueOptions}</select></label>
        <label class="field">Start time<input name="startTime" type="time" value="${esc(String(start).slice(0, 5))}" required></label>
        <label class="field">End time<input name="endTime" type="time" value="${esc(String(end).slice(0, 5))}" required></label>
        ${first ? '' : '<button type="button" class="btn small danger" data-remove-occurrence>Remove date</button>'}`;
      node.querySelector('[name=venue]').value = venue;
      occurrences.appendChild(node);
      node.querySelector('[data-remove-occurrence]')?.addEventListener('click', () => node.remove());
    };

    const occurrenceValues = existing?.occurrences?.length ? existing.occurrences : [null];
    occurrenceValues.forEach((value, index) => addOccurrence(index === 0, value));

    const addItem = (item = null) => {
      const node = document.createElement('div');
      node.className = 'item-row';
      node.innerHTML = `
        <label class="field">Equipment<select name="equipmentId" required><option value="">Choose equipment</option>${equipmentOptions(item?.equipmentId)}</select></label>
        <label class="field">Quantity<input name="requestedQuantity" type="number" min="1" value="${esc(item?.requestedQuantity || 1)}" required></label>
        <button type="button" class="btn danger" aria-label="Remove item">✕</button>`;
      node.querySelector('button').addEventListener('click', () => node.remove());
      items.appendChild(node);
    };
    (existing?.equipmentItems?.length ? existing.equipmentItems : [null]).forEach(addItem);

    container.querySelector('#addOccurrence').addEventListener('click', () => addOccurrence());
    container.querySelector('#addItem').addEventListener('click', () => addItem());
    container.querySelector('#urgent').addEventListener('change', (event) =>
      container.querySelector('#urgentReasonField').classList.toggle('hidden', !event.target.checked));

    container.querySelector('#borrowForm').addEventListener('submit', async (event) => {
      event.preventDefault();
      const form = event.currentTarget;
      const occurrenceList = [...occurrences.children].map((node) => ({
        eventDate: node.querySelector('[name=eventDate]').value,
        venue: node.querySelector('[name=venue]').value,
        startTime: node.querySelector('[name=startTime]').value,
        endTime: node.querySelector('[name=endTime]').value
      }));
      const first = occurrenceList[0];
      const payload = {
        venue: first.venue,
        eventDate: first.eventDate,
        startTime: first.startTime,
        endTime: first.endTime,
        purpose: form.purpose.value,
        participants: form.participants.value,
        personInCharge: form.personInCharge.value,
        position: form.position.value,
        borrowerRemarks: form.borrowerRemarks.value,
        urgent: form.urgent.checked,
        urgentReason: form.urgent.checked ? form.urgentReason.value : '',
        occurrences: occurrenceList,
        equipmentItems: [...items.children].map((node) => ({
          equipmentId: Number(node.querySelector('[name=equipmentId]').value),
          requestedQuantity: Number(node.querySelector('[name=requestedQuantity]').value)
        }))
      };
      await withBusy(form.querySelector('[type=submit]'), async () => {
        try {
          if (editId) await api.updateRequest(editId, payload);
          else await api.createRequest(payload);
          toast(editId ? 'Request updated. Review starts again.' : 'Request submitted.', 'success');
          location.hash = '#/my-requests';
        } catch (error) { handleError(error); }
      });
    });
  } catch (error) {
    container.innerHTML = page('Request Equipment', '', '', `<div class="alert error">${esc(error.message)}</div>`);
  }
}
