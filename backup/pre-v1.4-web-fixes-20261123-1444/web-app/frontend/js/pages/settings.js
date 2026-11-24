import { api } from '../api.js';
import { state, setTheme, ROLE_LABELS } from '../state.js';
import { esc, page, handleError, toast, withBusy, confirmDialog } from '../ui.js';

export async function renderSettings(container) {
  container.innerHTML = '<div class="card empty">Loading settings…</div>';
  try {
    const [profile, venueData] = await Promise.all([
      api.profile(),
      state.user.role === 'ICT_STAFF' ? api.venues().catch(() => ({ venues: [] })) : Promise.resolve({ venues: [] })
    ]);
    const user = profile.user || state.user;
    const picture = user.profilePicture
      ? `<img src="${api.pictureUrl()}" alt="Profile picture" style="width:72px;height:72px;border-radius:50%;object-fit:cover">`
      : `<div class="avatar-btn" style="width:72px;height:72px;font-size:20px">${esc((user.fullName || 'EL').slice(0, 2).toUpperCase())}</div>`;

    container.innerHTML = page('Settings', 'Profile, appearance and notification preferences', '', `
      <div class="grid two">
        <section class="card stack">
          <h3>Profile</h3>
          <div class="row">${picture}<div><strong>${esc(user.fullName)}</strong><div class="muted">${esc(ROLE_LABELS[user.role] || user.role)}</div></div></div>
          <form id="profileForm" class="stack">
            <label class="field">Username<input name="username" value="${esc(user.username)}" required></label>
            <label class="field">Full name<input name="fullName" value="${esc(user.fullName)}" required></label>
            <label class="field">Department<input name="department" value="${esc(user.department)}"></label>
            <label class="field">Profile picture (PNG)<input name="picture" type="file" accept="image/png"></label>
            <button class="btn primary" type="submit">Save profile</button>
          </form>
        </section>
        <section class="card stack">
          <h3>Appearance</h3>
          <p class="muted">The desktop theme is mirrored here and remembered on this device.</p>
          <div class="row">
            <button class="btn secondary" data-theme="light">Light</button>
            <button class="btn secondary" data-theme="dark">Dark</button>
          </div>
          <div class="divider"></div>
          <h3>Notifications</h3>
          <label class="inline-check"><input type="checkbox" id="notifEnabled" ${user.notificationsEnabled ? 'checked' : ''}> Enable in-app notifications</label>
          <div class="inline-check"><input type="checkbox" id="soundEnabled" ${user.notificationSoundEnabled ? 'checked' : ''}> Play a sound for new notifications</div>
          <label class="field">Sound
            <select id="soundName">
              ${['chime', 'bell', 'tap', 'pop'].map((sound) => `<option ${user.notificationSound === sound ? 'selected' : ''}>${sound}</option>`).join('')}
            </select>
          </label>
          <button class="btn" id="savePrefs">Save preferences</button>
        </section>
      </div>
      ${state.user.role === 'ICT_STAFF' ? `
      <section class="card stack">
        <div class="section-title"><h3>Venue list</h3><span class="muted">Shared with the desktop app through room.md</span></div>
        <form id="venueForm" class="stack">
          <div id="venueInputs" class="grid two">
            ${(venueData.venues || []).map((venue) => `<label class="field">Room<input name="venue" value="${esc(venue)}"></label>`).join('')}
          </div>
          <div class="row"><button type="button" class="btn small" id="addVenue">＋ Add room</button>
            <button type="submit" class="btn primary">Save venues</button></div>
        </form>
      </section>` : ''}`);

    container.querySelectorAll('[data-theme]').forEach((button) =>
      button.addEventListener('click', () => {
        setTheme(button.dataset.theme);
        toast(`${button.dataset.theme === 'dark' ? 'Dark' : 'Light'} mode enabled.`, 'success');
      }));

    container.querySelector('#profileForm').addEventListener('submit', async (event) => {
      event.preventDefault();
      const form = event.currentTarget;
      await withBusy(form.querySelector('[type=submit]'), async () => {
        try {
          const payload = {
            username: form.username.value, fullName: form.fullName.value,
            department: form.department.value, profilePictureBase64: null
          };
          const file = form.picture.files[0];
          if (file) payload.profilePictureBase64 = await readFileAsDataUrl(file);
          await api.updateProfile(payload);
          state.user = { ...state.user, ...payload, profilePictureBase64: undefined };
          toast('Profile saved.', 'success');
          renderSettings(container);
        } catch (error) { handleError(error); }
      });
    });

    container.querySelector('#savePrefs').addEventListener('click', async () => {
      try {
        await api.updateNotificationPref(container.querySelector('#notifEnabled').checked);
        await api.updateSoundPref(container.querySelector('#soundEnabled').checked, container.querySelector('#soundName').value);
        state.user = {
          ...state.user,
          notificationsEnabled: container.querySelector('#notifEnabled').checked,
          notificationSoundEnabled: container.querySelector('#soundEnabled').checked,
          notificationSound: container.querySelector('#soundName').value
        };
        toast('Preferences saved.', 'success');
      } catch (error) { handleError(error); }
    });

    container.querySelector('#addVenue')?.addEventListener('click', () => {
      const node = document.createElement('label');
      node.className = 'field';
      node.innerHTML = 'Room<input name="venue" placeholder="Room name">';
      container.querySelector('#venueInputs').appendChild(node);
    });
    container.querySelector('#venueForm')?.addEventListener('submit', async (event) => {
      event.preventDefault();
      const venues = [...event.currentTarget.querySelectorAll('[name=venue]')]
        .map((input) => input.value.trim()).filter(Boolean);
      try {
        await api.saveVenues(venues);
        toast('Venue list saved.', 'success');
        renderSettings(container);
      } catch (error) { handleError(error); }
    });
  } catch (error) {
    container.innerHTML = page('Settings', '', '', `<div class="alert error">${esc(error.message)}</div>`);
  }
}

function readFileAsDataUrl(file) {
  return new Promise((resolve, reject) => {
    const reader = new FileReader();
    reader.onload = () => resolve(reader.result);
    reader.onerror = () => reject(new Error('Could not read that image.'));
    reader.readAsDataURL(file);
  });
}
