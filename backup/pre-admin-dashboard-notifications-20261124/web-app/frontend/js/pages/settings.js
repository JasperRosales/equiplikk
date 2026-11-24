import { api } from '../api.js';
import { state, setTheme, ROLE_LABELS } from '../state.js';
import { esc, page, handleError, toast, withBusy } from '../ui.js';
import { soundNames, normalizeSound, unlockSound, playNotificationSound } from '../sound.js';
import { icon, syncPasswordIcons } from '../icons.js';

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
          <p class="muted">Choose a comfortable look for EquipLink.</p>
          <div class="row" id="themeButtons">
            <button class="btn" data-theme="light">Light mode</button>
            <button class="btn" data-theme="dark">Dark mode</button>
          </div>
          <div class="divider"></div>
          <h3>Notifications</h3>
          <label class="inline-check"><input type="checkbox" id="notifEnabled" ${user.notificationsEnabled ? 'checked' : ''}> Enable in-app notifications</label>
          <div class="inline-check"><input type="checkbox" id="soundEnabled" ${user.notificationSoundEnabled ? 'checked' : ''}> Play a sound for new notifications</div>
          <label class="field">Sound
            <select id="soundName">
              ${soundNames().map((sound) => `<option value="${sound}" ${normalizeSound(user.notificationSound) === sound ? 'selected' : ''}>${sound}</option>`).join('')}
            </select>
          </label>
          <button class="btn" id="savePrefs">Save preferences</button>
          <button class="btn small" id="previewSound">Preview sound</button>
        </section>
      </div>
      <div class="grid two">
        <section class="card stack">
          <div><h3>Security settings</h3><p class="muted">Keep your account credentials up to date.</p></div>
          <form id="passwordForm" class="stack">
            <label class="field">Current password<span class="password-field"><input name="current" type="password" autocomplete="current-password" required><button type="button" class="password-toggle" data-password-toggle aria-label="Show password"></button></span></label>
            <label class="field">New password<span class="password-field"><input name="next" type="password" minlength="8" autocomplete="new-password" required><button type="button" class="password-toggle" data-password-toggle aria-label="Show password"></button></span></label>
            <label class="field">Confirm new password<span class="password-field"><input name="confirm" type="password" minlength="8" autocomplete="new-password" required><button type="button" class="password-toggle" data-password-toggle aria-label="Show password"></button></span></label>
            <button class="btn primary" type="submit">Change password</button>
          </form>
        </section>
        <section class="card stack">
          <div><h3>System information</h3><p class="muted">About this campus equipment borrowing system.</p></div>
          <dl class="kv">
            <dt>Application name</dt><dd>EquipLink</dd>
            <dt>Interface</dt><dd>Web app (same database and rules as the desktop app)</dd>
            <dt>Database</dt><dd>PostgreSQL</dd>
            <dt>About EquipLink</dt><dd>A single place to request and manage campus equipment.</dd>
            <dt>Help &amp; support</dt><dd>Contact your campus system administrator for assistance.</dd>
          </dl>
        </section>
      </div>
      ${state.user.role === 'ICT_STAFF' ? `
      <section class="card stack">
        <div class="section-title"><h3>Venue list</h3><span class="muted">Shared with the desktop app through room.md</span></div>
        <form id="venueForm" class="stack">
          <div id="venueInputs" class="grid two">
            ${(venueData.venues || []).map((venue) => `<label class="field">Room<input name="venue" value="${esc(venue)}"></label>`).join('')}
          </div>
          <div class="row"><button type="button" class="btn small" id="addVenue">${icon('plus', 14)} Add room</button>
            <button type="submit" class="btn primary">Save venues</button></div>
        </form>
      </section>` : ''}`);

    const paintTheme = () => container.querySelectorAll('[data-theme]').forEach((button) =>
      button.classList.toggle('primary', button.dataset.theme === document.documentElement.dataset.theme));
    paintTheme();
    syncPasswordIcons(container);
    container.querySelector('#passwordForm').addEventListener('submit', async (event) => {
      event.preventDefault();
      const form = event.currentTarget;
      if (form.next.value !== form.confirm.value) {
        toast('The new passwords do not match.', 'error');
        return;
      }
      await withBusy(form.querySelector('[type=submit]'), async () => {
        try {
          await api.changePassword(form.current.value, form.next.value);
          form.reset();
          toast('Password changed.', 'success');
        } catch (error) { handleError(error); }
      });
    });

    container.querySelectorAll('[data-theme]').forEach((button) =>
      button.addEventListener('click', () => {
        setTheme(button.dataset.theme);
        paintTheme();
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

    container.querySelector('#previewSound').addEventListener('click', () => {
      unlockSound();
      const name = container.querySelector('#soundName').value;
      if (!playNotificationSound(name)) toast('Click again after the browser enables audio.', 'error');
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
