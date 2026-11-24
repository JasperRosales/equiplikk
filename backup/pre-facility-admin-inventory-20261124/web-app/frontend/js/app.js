import { api } from './api.js';
import { state, navFor, canSee, initTheme, setTheme, ROLE_LABELS } from './state.js';
import { router } from './router.js';
import { esc, initials, handleError, toast } from './ui.js';
import { renderDashboard } from './pages/dashboard.js';
import { renderEquipment, renderInventory } from './pages/equipment.js';
import { renderRequestForm } from './pages/requestForm.js';
import { renderRequests } from './pages/requests.js';
import { renderStaffQueue, renderSchedule, renderTransactions } from './pages/staff.js';
import { renderUsers, renderPasswordRequests } from './pages/admin.js';
import { renderNotifications } from './pages/notifications.js';
import { renderSettings } from './pages/settings.js';
import { initSound, unlockSound, notificationSoundAllowed, playNotificationSound, soundNeedsGesture } from './sound.js';
import { initPasswordToggles, icon } from './icons.js';
import { initMotion } from './motion.js';

const $ = (id) => document.getElementById(id);
let unreadTimer = 0;

initTheme();
initSound();
initPasswordToggles();
initMotion();
$('year').textContent = String(new Date().getFullYear());
bindAuth();
bindShell();

api.me().then((data) => {
  state.user = data.user;
  showApp();
}).catch(() => showLogin());

function showLogin(message = '') {
  $('appShell').classList.add('hidden');
  $('loginScreen').classList.remove('hidden');
  const error = $('authError');
  error.textContent = message;
  error.classList.toggle('hidden', !message);
}

function showApp() {
  $('loginScreen').classList.add('hidden');
  $('appShell').classList.remove('hidden');
  $('sidebarRole').textContent = ROLE_LABELS[state.user.role] || state.user.role;
  renderAvatar();
  renderNav();
  router.start(renderRoute);
  refreshUnread();
  clearInterval(unreadTimer);
  unreadTimer = window.setInterval(refreshUnread, 45000);
}

function soundKey() {
  return `equiplink-notification-seen-${state.user?.userId || 0}`;
}

async function refreshUnread() {
  try {
    const [data, latest] = await Promise.all([
      api.unreadCount(),
      api.latestNotificationId().catch(() => ({ id: 0 }))
    ]);
    state.unread = data.count || 0;
    const badge = $('unreadBadge');
    badge.textContent = String(state.unread);
    badge.classList.toggle('hidden', state.unread === 0);
    renderNav();

    // Initialize silently so old notifications never sound on login/refresh.
    const latestId = Number(latest.id || 0);
    const seen = Number(localStorage.getItem(soundKey()) || 0);
    if (!seen && latestId) {
      localStorage.setItem(soundKey(), String(latestId));
    } else if (seen && latestId > seen) {
      localStorage.setItem(soundKey(), String(latestId));
      if (notificationSoundAllowed(state.user)) {
        if (soundNeedsGesture()) {
          toast('Notification sound is waiting for your first click. Click anywhere to enable it.');
        }
        playNotificationSound(state.user.notificationSound);
      }
    }
  } catch (error) {
    // A transient polling failure should not interrupt the user's work.
  }
}
window.refreshUnread = refreshUnread;

function renderAvatar() {
  const holder = $('avatarInitials');
  if (!state.user?.profilePicture) {
    holder.textContent = initials(state.user);
    return;
  }
  holder.innerHTML = `<img src="${api.pictureUrl()}" alt="">`;
}

function renderNav() {
  const nav = $('sidebarNav');
  const items = navFor(state.user);
  nav.innerHTML = `
    <div class="nav-group">Workspace</div>
    ${items.filter((item) => !['notifications', 'settings'].includes(item.key)).map((item) => navButton(item)).join('')}
    <div class="nav-group">Personal</div>
    ${items.filter((item) => ['notifications', 'settings'].includes(item.key)).map((item) => navButton(item)).join('')}`;
  nav.querySelectorAll('[data-route]').forEach((button) =>
    button.addEventListener('click', () => router.go(button.dataset.route)));
}

function navButton(item) {
  const active = state.route === item.key;
  const count = item.key === 'notifications' && state.unread
    ? `<span class="nav-count">${state.unread}</span>` : '';
  return `<button class="nav-item ${active ? 'active' : ''}" data-route="${item.key}">
    <span class="nav-icon">${icon(item.icon, 16)}</span><span>${esc(item.label)}</span>${count}</button>`;
}

async function renderRoute() {
  const allowed = canSee(state.user, state.route)
    || ['history', 'request', 'my-requests'].includes(state.route);
  if (!allowed) {
    router.go('dashboard');
    return;
  }
  const item = navFor(state.user).find((entry) => entry.key === state.route)
    || { title: state.route.replaceAll('-', ' '), label: state.route.replaceAll('-', ' ') };
  $('pageTitle').textContent = item.title || item.label;
  $('pageSubtitle').textContent = ROLE_LABELS[state.user.role] || '';
  $('main').innerHTML = '<div class="card empty">Loading…</div>';
  closeNav();
  $('main').scrollTop = 0;

  try {
    switch (state.route) {
      case 'dashboard': await renderDashboard($('main')); break;
      case 'equipment': await renderEquipment($('main')); break;
      case 'inventory': await renderInventory($('main')); break;
      case 'request': await renderRequestForm($('main')); break;
      case 'my-requests': await renderRequests($('main'), 'active'); break;
      case 'history': await renderRequests($('main'), 'history'); break;
      case 'all-requests': await renderRequests($('main'), 'all'); break;
      case 'borrowing-requests': await renderRequests($('main'), 'borrowing-requests'); break;
      case 'approval': await renderRequests($('main'), 'approval'); break;
      case 'requests': await renderStaffQueue($('main')); break;
      case 'schedule': await renderSchedule($('main')); break;
      case 'transactions': await renderTransactions($('main')); break;
      case 'users': await renderUsers($('main')); break;
      case 'password-requests': await renderPasswordRequests($('main')); break;
      case 'notifications': await renderNotifications($('main')); break;
      case 'settings': await renderSettings($('main')); break;
      default: await renderDashboard($('main'));
    }
    renderNav();
  } catch (error) {
    $('main').innerHTML = `<div class="alert error">${esc(error.message)}</div>`;
    handleError(error);
  }
}

function closeNav() {
  $('sidebar').classList.remove('open');
  $('navBackdrop').classList.add('hidden');
}

function bindShell() {
  $('menuIcon').innerHTML = icon('menu', 18);
  $('notificationIcon').innerHTML = icon('notifications', 18);
  $('menuButton').addEventListener('click', () => {
    $('sidebar').classList.add('open');
    $('navBackdrop').classList.remove('hidden');
  });
  $('navBackdrop').addEventListener('click', closeNav);
  $('themeToggle').addEventListener('click', () => {
    setTheme(document.documentElement.dataset.theme === 'dark' ? 'light' : 'dark');
  });
  $('notificationButton').addEventListener('click', () => router.go('notifications'));
  $('profileButton').addEventListener('click', () => router.go('settings'));
  $('logoutButton').addEventListener('click', async () => {
    try { await api.logout(); } catch (error) { handleError(error); }
    state.user = null;
    clearInterval(unreadTimer);
    if (location.hash) location.hash = '';
    showLogin();
  });
}

function bindAuth() {
  const error = $('authError');
  const showForm = (name) => {
    ['login', 'register', 'reset'].forEach((key) => $(key + 'Form').classList.toggle('hidden', key !== name));
    $('authTitle').textContent = name === 'login' ? 'Welcome back'
      : name === 'register' ? 'Create your account' : 'Reset password';
    $('authSubtitle').textContent = name === 'login' ? 'Sign in to continue to EquipLink.'
      : name === 'register' ? 'Borrower accounts are created here.'
      : 'ICT staff reviews every password reset request.';
    error.classList.add('hidden');
  };
  $('showRegister').addEventListener('click', () => showForm('register'));
  $('showReset').addEventListener('click', () => showForm('reset'));
  document.querySelectorAll('[data-auth-back]').forEach((button) =>
    button.addEventListener('click', () => showForm('login')));

  $('loginForm').addEventListener('submit', async (event) => {
    event.preventDefault();
    unlockSound();
    error.classList.add('hidden');
    try {
      const data = await api.login($('loginUser').value.trim(), $('loginPassword').value);
      state.user = data.user;
      $('loginPassword').value = '';
      showApp();
    } catch (failure) {
      error.textContent = failure.message;
      error.classList.remove('hidden');
    }
  });

  $('registerForm').addEventListener('submit', async (event) => {
    event.preventDefault();
    try {
      await api.register({
        username: $('regUsername').value.trim(), fullName: $('regFullName').value.trim(),
        email: $('regEmail').value.trim(), department: $('regDepartment').value.trim(),
        borrowerType: $('regType').value, password: $('regPassword').value
      });
      $('regPassword').value = '';
      showForm('login');
      toast('Account created. Sign in to continue.', 'success');
    } catch (failure) {
      error.textContent = failure.message;
      error.classList.remove('hidden');
    }
  });

  $('resetForm').addEventListener('submit', async (event) => {
    event.preventDefault();
    try {
      await api.requestPasswordReset($('resetUser').value.trim(), $('resetEmail').value.trim());
      toast('Password request submitted.', 'success');
      showForm('login');
    } catch (failure) {
      error.textContent = failure.message;
      error.classList.remove('hidden');
    }
  });

  $('completeReset').addEventListener('click', async () => {
    try {
      await api.completePasswordReset({
        usernameOrEmail: $('completeUser').value.trim(),
        registeredEmail: $('completeEmail').value.trim(),
        newPassword: $('completePassword').value
      });
      $('completePassword').value = '';
      toast('Password changed. Sign in with your new password.', 'success');
      showForm('login');
    } catch (failure) {
      error.textContent = failure.message;
      error.classList.remove('hidden');
    }
  });
}
