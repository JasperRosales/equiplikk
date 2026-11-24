import { api } from '../api.js';
import { esc, fmtDateTime, page, empty, handleError, toast, confirmDialog } from '../ui.js';

let lastDeletedIds = [];

export async function renderNotifications(container) {
  container.innerHTML = '<div class="card empty">Loading notifications…</div>';
  try {
    const rows = await api.notifications();
    const category = new URLSearchParams(location.hash.split('?')[1] || '').get('category') || '';
    const categories = [...new Set(rows.map((row) => row.category).filter(Boolean))];
    const filtered = category ? rows.filter((row) => row.category === category) : rows;

    const body = filtered.map((item) => [
      esc(item.title), esc(item.message), esc(item.category || 'General'),
      item.read ? '<span class="badge">Read</span>' : '<span class="badge pending">Unread</span>',
      esc(fmtDateTime(item.createdAt)),
      `<div class="actions">
        ${item.read ? '' : `<button class="btn small secondary" data-read="${item.notificationId}">Mark read</button>`}
        <button class="btn small danger" data-delete="${item.notificationId}">Delete</button>
      </div>`
    ]);

    container.innerHTML = page('Notifications', 'Requests, releases, returns and account updates', `
      <select id="categoryFilter"><option value="">All categories</option>
        ${categories.map((value) => `<option value="${esc(value)}" ${category === value ? 'selected' : ''}>${esc(value.replaceAll('_', ' '))}</option>`).join('')}
      </select>
      <button class="btn small" id="markAll">Mark all read</button>
      <button class="btn small danger" id="deleteAll">Delete all</button>
      ${lastDeletedIds.length ? '<button class="btn small" id="undoAll">Undo delete all</button>' : ''}`,
      table(['Title', 'Message', 'Category', 'State', 'When', 'Actions'], body, 'You have no notifications.'));

    container.querySelector('#categoryFilter').addEventListener('change', (event) => {
      location.hash = `#/notifications${event.target.value ? '?category=' + encodeURIComponent(event.target.value) : ''}`;
    });
    container.querySelector('#markAll').addEventListener('click', async () => {
      try { await api.markAllRead(); toast('All notifications marked read.', 'success'); renderNotifications(container); window.refreshUnread?.(); }
      catch (error) { handleError(error); }
    });
    container.querySelector('#deleteAll').addEventListener('click', async () => {
      if (!await confirmDialog('Delete all notifications? You can undo once.', 'Delete notifications')) return;
      try {
        lastDeletedIds = rows.map((row) => row.notificationId);
        const result = await api.deleteAllNotifications();
        toast(`${result.deleted ?? lastDeletedIds.length} notification(s) deleted.`, 'success');
        renderNotifications(container); window.refreshUnread?.();
      } catch (error) { handleError(error); }
    });
    container.querySelector('#undoAll')?.addEventListener('click', async () => {
      try {
        const result = await api.restoreAllNotifications(lastDeletedIds);
        lastDeletedIds = [];
        toast(`${result.restored ?? 0} notification(s) restored.`, 'success');
        renderNotifications(container); window.refreshUnread?.();
      } catch (error) { handleError(error); }
    });
    container.querySelectorAll('[data-read]').forEach((button) =>
      button.addEventListener('click', async () => {
        try { await api.markRead(Number(button.dataset.read)); renderNotifications(container); window.refreshUnread?.(); }
        catch (error) { handleError(error); }
      }));
    container.querySelectorAll('[data-delete]').forEach((button) =>
      button.addEventListener('click', async () => {
        try {
          await api.deleteNotification(Number(button.dataset.delete));
          toast('Notification deleted.', 'success'); renderNotifications(container); window.refreshUnread?.();
        } catch (error) { handleError(error); }
      }));
  } catch (error) {
    container.innerHTML = page('Notifications', '', '', `<div class="alert error">${esc(error.message)}</div>`);
  }
}
