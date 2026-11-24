import { api } from '../api.js';
import { esc, fmtDate, fmtDateTime, statusBadge, table, page, empty, handleError, toast, openModal, confirmDialog, withBusy } from '../ui.js';
import { openRequestDetail } from './requests.js';

export async function renderUsers(container) {
  container.innerHTML = '<div class="card empty">Loading accounts…</div>';
  try {
    const users = await api.borrowers();
    const body = users.map((user) => [
      esc(user.username), esc(user.fullName), esc(user.email), esc(user.department),
      esc(user.borrowerType || '—'),
      user.deletedAt ? '<span class="badge declined">Deleted</span>' : user.active ? '<span class="badge approved">Active</span>' : '<span class="badge pending">Inactive</span>',
      `<div class="actions">
        <button class="btn small secondary" data-details="${user.userId}">Account details</button>
        ${user.deletedAt
          ? `<button class="btn small" data-restore="${user.userId}">Restore</button>`
          : `<button class="btn small" data-active="${user.userId}" data-value="${!user.active}">${user.active ? 'Deactivate' : 'Activate'}</button>
             <button class="btn small danger" data-delete="${user.userId}">Delete</button>`}
      </div>`
    ]);
    container.innerHTML = page('User Management', 'Borrower accounts and their preserved history',
      '<button class="btn primary" id="createFaculty">＋ Create faculty account</button>',
      table(['Username', 'Name', 'Email', 'Department', 'Type', 'Status', 'Actions'], body, 'No borrower accounts found.'));

    document.getElementById('createFaculty').addEventListener('click', () => {
      const modal = openModal({
        title: 'Create faculty account',
        body: `<form id="facultyForm" class="grid two">
          <label class="field">Username<input name="username" required></label>
          <label class="field">Full name<input name="fullName" required></label>
          <label class="field">Email<input name="email" type="email" required></label>
          <label class="field">Department<input name="department" required></label>
          <label class="field">Temporary password<input name="password" type="password" minlength="8" required></label>
        </form>`,
        footer: '<button class="btn" data-close>Cancel</button><button class="btn primary" id="saveFaculty">Create</button>'
      });
      modal.root.querySelector('[data-close]').addEventListener('click', modal.close);
      modal.root.querySelector('#saveFaculty').addEventListener('click', async (event) => {
        await withBusy(event.currentTarget, async () => {
          try {
            await api.createFaculty(Object.fromEntries(new FormData(modal.root.querySelector('#facultyForm'))));
            modal.close(); toast('Faculty account created.', 'success'); renderUsers(container);
          } catch (error) { handleError(error); }
        });
      });
    });

    container.querySelectorAll('[data-details]').forEach((button) =>
      button.addEventListener('click', async () => {
        try {
          const data = await api.accountDetails(Number(button.dataset.details));
          const rows = (data.requests || []).map((request) => [esc(request.requestCode), esc(fmtDate(request.eventDate)), statusBadge(request.status),
            `<button class="btn small secondary" data-req="${request.requestId}">Open</button>`]);
          const auditRows = (data.history || []).map((event) => [esc(event.eventType), esc(fmtDateTime(event.createdAt)), esc(event.actorName), esc(event.remarks)]);
          const modal = openModal({
            title: 'Account details',
            body: `<h3>Requests</h3>${table(['Code', 'Date', 'Status', ''], rows, 'No requests for this account.')}
              <h3>Approval trail</h3>${table(['Event', 'When', 'Actor', 'Remarks'], auditRows, 'No audit events.')}`,
            footer: '<button class="btn" data-close>Close</button>', wide: true
          });
          modal.root.querySelector('[data-close]').addEventListener('click', modal.close);
          modal.root.querySelectorAll('[data-req]').forEach((node) =>
            node.addEventListener('click', async () => {
              modal.close();
              try { await openRequestDetail(Number(node.dataset.req), 'all', () => renderUsers(container)); }
              catch (error) { handleError(error); }
            }));
        } catch (error) { handleError(error); }
      }));

    container.querySelectorAll('[data-active]').forEach((button) =>
      button.addEventListener('click', async () => {
        try {
          await api.setBorrowerActive(Number(button.dataset.active), button.dataset.value === 'true');
          toast('Account status updated.', 'success'); renderUsers(container);
        } catch (error) { handleError(error); }
      }));
    container.querySelectorAll('[data-restore]').forEach((button) =>
      button.addEventListener('click', async () => {
        try {
          await api.restoreBorrower(Number(button.dataset.restore));
          toast('Account restored.', 'success'); renderUsers(container);
        } catch (error) { handleError(error); }
      }));
    container.querySelectorAll('[data-delete]').forEach((button) =>
      button.addEventListener('click', async () => {
        if (!await confirmDialog('Soft-delete this account? All history remains available to ICT staff.', 'Delete account')) return;
        try {
          await api.deleteBorrower(Number(button.dataset.delete));
          toast('Account deleted.', 'success'); renderUsers(container);
        } catch (error) { handleError(error); }
      }));
  } catch (error) {
    container.innerHTML = page('User Management', '', '', `<div class="alert error">${esc(error.message)}</div>`);
  }
}

export async function renderPasswordRequests(container) {
  container.innerHTML = '<div class="card empty">Loading password requests…</div>';
  try {
    const rows = await api.passwordRequests();
    const body = rows.map((request) => [
      esc(request.username), esc(request.fullName), esc(request.email),
      esc(request.role), esc(fmtDateTime(request.requestedAt)),
      request.registeredEmailMatch ? 'Matched' : 'Not matched',
      request.identityVerified ? 'Verified' : 'Not verified',
      `<div class="actions"><button class="btn small danger" data-review="${request.requestId}" data-approve="false">Reject</button>
       <button class="btn small primary" data-review="${request.requestId}" data-approve="true">Approve</button></div>`
    ]);
    container.innerHTML = page('Password Requests', 'In-app reset requests awaiting ICT review', '',
      table(['Username', 'Name', 'Email', 'Role', 'Requested', 'Email', 'Identity', 'Actions'], body, 'No pending password requests.'));
    container.querySelectorAll('[data-review]').forEach((button) =>
      button.addEventListener('click', async () => {
        const approve = button.dataset.approve === 'true';
        const modal = openModal({
          title: approve ? 'Approve password reset' : 'Reject password reset',
          body: `<form id="resetReviewForm"><label class="field">Note<textarea name="note" maxlength="1000" ${approve ? '' : 'required'}></textarea></label></form>`,
          footer: `<button class="btn" data-close>Cancel</button><button class="btn primary" id="confirmReview">Confirm</button>`
        });
        modal.root.querySelector('[data-close]').addEventListener('click', modal.close);
        modal.root.querySelector('#confirmReview').addEventListener('click', async (event) => {
          await withBusy(event.currentTarget, async () => {
            try {
              const note = modal.root.querySelector('[name=note]').value;
              await api.reviewPasswordRequest(Number(button.dataset.review), approve, note);
              modal.close(); toast('Password request reviewed.', 'success'); renderPasswordRequests(container);
            } catch (error) { handleError(error); }
          });
        });
      }));
  } catch (error) {
    container.innerHTML = page('Password Requests', '', '', `<div class="alert error">${esc(error.message)}</div>`);
  }
}
