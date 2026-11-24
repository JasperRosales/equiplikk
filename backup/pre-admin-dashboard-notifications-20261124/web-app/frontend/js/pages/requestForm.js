import { api } from '../api.js';
import { state } from '../state.js';
import { esc, page, toast, handleError, withBusy, timeInput, readTimeInput } from '../ui.js';
import { icon } from '../icons.js';

const STUDENT_DATE_MESSAGE = 'Students can only request one schedule date per borrowing request. Please create a separate request for another date.';

function manilaToday() {
  return new Date().toLocaleDateString('en-CA', { timeZone: 'Asia/Manila', year: 'numeric', month: '2-digit', day: '2-digit' });
}

function scheduleRules() {
  const type = String(state.user?.borrowerType || '').trim().toUpperCase();
  if (type === 'STUDENT') {
    return { max: 1, hint: STUDENT_DATE_MESSAGE, error: STUDENT_DATE_MESSAGE };
  }
  if (type === 'FACULTY') {
    return {
      max: 2,
      hint: 'Faculty can include up to two different schedule dates in one request.',
      error: 'Faculty can request at most two schedule dates per borrowing request. Please create a separate request for additional dates.'
    };
  }
  return {
    max: 2,
    hint: 'You can include up to two different schedule dates in one request.',
    error: 'You can request at most two schedule dates per borrowing request. Please create a separate request for additional dates.'
  };
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
    const rules = scheduleRules();
    const venueOptions = venues.map((venue) => `<option value="${esc(venue)}">${esc(venue)}</option>`).join('');
    const equipmentOptions = (selected = preselect) => equipment.map((item) =>
      `<option value="${item.equipmentId}" ${Number(selected) === item.equipmentId ? 'selected' : ''}>${esc(item.equipmentCode)} — ${esc(item.equipmentName)} (${esc(item.availableQuantity)} available)</option>`).join('');

    container.innerHTML = page(
      editId ? 'Edit Request' : 'Request Equipment',
      editId ? 'Changes restart staff review for this request' : 'Submit one request with the schedule dates allowed for your account',
      '', `
      <form id="borrowForm" class="stack">
        <datalist id="equipLinkVenueOptions">${venueOptions}</datalist>
        <section class="card stack">
          <div class="section-title"><h3>Schedule</h3><button type="button" class="btn small secondary" id="addOccurrence">${icon('plus', 14)} Add date</button></div>
          <p class="hint" id="scheduleHint">${esc(rules.hint)} Choose a listed venue or type a custom one.</p>
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
          <div class="section-title"><h3>Equipment</h3><button type="button" class="btn small secondary" id="addItem">${icon('plus', 14)} Add item</button></div>
          <div id="items" class="stack"></div>
        </section>
        <div class="row"><button type="submit" class="btn primary">${editId ? 'Save changes' : 'Submit request'}</button>
          <a class="btn" href="#/my-requests">Cancel</a></div>
      </form>`);

    const occurrences = container.querySelector('#occurrences');
    const items = container.querySelector('#items');
    const addDateButton = container.querySelector('#addOccurrence');

    const syncDateLimit = () => {
      const full = occurrences.children.length >= rules.max;
      addDateButton.disabled = full;
      addDateButton.title = full ? rules.error : '';
    };

    const addOccurrence = (first = false, values = null) => {
      const node = document.createElement('div');
      node.className = 'occurrence grid two';
      const date = values?.eventDate || manilaToday();
      const venue = values?.venue || '';
      const start = values?.startTime || '';
      const end = values?.endTime || '';
      node.innerHTML = `
        <label class="field">Event date<input name="eventDate" type="date" value="${esc(String(date).slice(0, 10))}" required></label>
        <label class="field">Venue<input name="venue" list="equipLinkVenueOptions" maxlength="150" autocomplete="off" placeholder="Choose or type a venue" value="${esc(venue)}" required></label>
        <label class="field">Start time${timeInput('startTime', String(start).slice(0, 5))}</label>
        <label class="field">End time${timeInput('endTime', String(end).slice(0, 5))}</label>
        ${first ? '' : '<button type="button" class="btn small danger" data-remove-occurrence>Remove date</button>'}`;
      occurrences.appendChild(node);
      node.querySelector('[data-remove-occurrence]')?.addEventListener('click', () => {
        node.remove();
        syncDateLimit();
      });
      syncDateLimit();
    };

    const occurrenceValues = existing?.occurrences?.length ? existing.occurrences : [null];
    occurrenceValues.forEach((value, index) => addOccurrence(index === 0, value));
    syncDateLimit();

    const addItem = (item = null) => {
      const node = document.createElement('div');
      node.className = 'item-row';
      node.innerHTML = `
        <label class="field">Equipment<select name="equipmentId" required><option value="">Choose equipment</option>${equipmentOptions(item?.equipmentId)}</select></label>
        <label class="field">Quantity<input name="requestedQuantity" type="number" min="1" value="${esc(item?.requestedQuantity || 1)}" required></label>
        <button type="button" class="btn danger" aria-label="Remove item">${icon('x', 14)}</button>`;
      node.querySelector('button').addEventListener('click', () => node.remove());
      items.appendChild(node);
    };
    (existing?.equipmentItems?.length ? existing.equipmentItems : [null]).forEach(addItem);

    addDateButton.addEventListener('click', () => {
      if (occurrences.children.length >= rules.max) {
        toast(rules.error, 'error');
        return;
      }
      addOccurrence();
    });
    container.querySelector('#addItem').addEventListener('click', () => addItem());
    container.querySelector('#urgent').addEventListener('change', (event) =>
      container.querySelector('#urgentReasonField').classList.toggle('hidden', !event.target.checked));

    container.querySelector('#borrowForm').addEventListener('submit', async (event) => {
      event.preventDefault();
      const form = event.currentTarget;
      await withBusy(form.querySelector('[type=submit]'), async () => {
        try {
          const occurrenceList = [...occurrences.children].map((node) => ({
            eventDate: node.querySelector('[name=eventDate]').value,
            venue: node.querySelector('[name=venue]').value.trim(),
            startTime: readTimeInput(node, 'startTime'),
            endTime: readTimeInput(node, 'endTime')
          }));
          if (!occurrenceList.length) throw new Error('Add at least one schedule date.');
          if (rules.max === 1 && occurrenceList.length !== 1) throw new Error(STUDENT_DATE_MESSAGE);
          if (occurrenceList.some((row) => !row.eventDate)) throw new Error('Choose an event date for every schedule.');
          if (occurrenceList.some((row) => !row.venue)) throw new Error('Enter or choose a venue for every schedule.');
          if (occurrenceList.some((row) => row.venue.length > 150)) {
            throw new Error('Venue names must be 150 characters or fewer.');
          }
          const dates = occurrenceList.map((row) => row.eventDate);
          if (new Set(dates).size !== dates.length) {
            throw new Error('Each schedule date must be unique. Please remove the duplicate date.');
          }
          if (dates.length > rules.max) throw new Error(rules.error);
          if (occurrenceList.some((row) => row.startTime >= row.endTime)) {
            throw new Error('End time must be after start time.');
          }
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
