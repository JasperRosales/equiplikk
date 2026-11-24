import { icon as iconSvg } from './icons.js';

export const state = {
  user: null,
  route: 'dashboard',
  params: new URLSearchParams(),
  unread: 0,
  routeVersion: 0
};

export const ROLE_LABELS = {
  BORROWER: 'Borrower',
  ICT_STAFF: 'System Admin',
  FACILITY_STAFF: 'Facility Staff',
  ADMIN: 'Facility Admin',
  DIRECTOR: 'Campus Director'
};

/** Navigation mirrors src/layout/MainLayoutPart1/Part2 exactly. */
const COMMON = [
  { key: 'notifications', label: 'Notifications', icon: 'notifications', title: 'Notifications' },
  { key: 'settings', label: 'Settings', icon: 'settings', title: 'Settings' }
];

export const NAVIGATION = {
  BORROWER: [
    { key: 'dashboard', label: 'Dashboard', icon: 'dashboard', title: 'Dashboard' },
    { key: 'equipment', label: 'Available Equipment', icon: 'equipment', title: 'Available Equipment' },
    { key: 'request', label: 'Request Equipment', icon: 'requestEquipment', title: 'Request Equipment' },
    { key: 'my-requests', label: 'My Requests', icon: 'myRequests', title: 'My Requests' },
    { key: 'history', label: 'Borrowing Request', icon: 'borrowing', title: 'Borrowing Request' },
    ...COMMON
  ],
  ICT_STAFF: [
    { key: 'dashboard', label: 'Dashboard', icon: 'dashboard', title: 'Dashboard' },
    { key: 'inventory', label: 'Inventory', icon: 'inventory', title: 'Inventory' },
    { key: 'requests', label: 'Requests', icon: 'requests', title: 'Requests' },
    { key: 'schedule', label: 'Schedule & Assignment', icon: 'schedule', title: 'Schedule & Assignment' },
    { key: 'transactions', label: 'Transaction History', icon: 'history', title: 'Transaction History' },
    { key: 'password-requests', label: 'Password Requests', icon: 'password', title: 'Password Requests' },
    { key: 'users', label: 'User Management', icon: 'users', title: 'User Management' },
    ...COMMON
  ],
  FACILITY_STAFF: [
    { key: 'dashboard', label: 'Dashboard', icon: 'dashboard', title: 'Dashboard' },
    { key: 'inventory', label: 'Inventory', icon: 'inventory', title: 'Inventory' },
    { key: 'requests', label: 'Requests', icon: 'requests', title: 'Requests' },
    { key: 'schedule', label: 'Schedule & Assignment', icon: 'schedule', title: 'Schedule & Assignment' },
    { key: 'transactions', label: 'Transaction History', icon: 'history', title: 'Transaction History' },
    ...COMMON
  ],
  ADMIN: [
    { key: 'dashboard', label: 'Dashboard', icon: 'dashboard', title: 'Dashboard' },
    { key: 'inventory', label: 'Inventory', icon: 'inventory', title: 'Inventory' },
    { key: 'borrowing-requests', label: 'Borrowing Requests', icon: 'borrowingRequests', title: 'Borrowing Requests' },
    { key: 'all-requests', label: 'All Requests', icon: 'allRequests', title: 'All Requests' },
    ...COMMON
  ],
  DIRECTOR: [
    { key: 'dashboard', label: 'Dashboard', icon: 'dashboard', title: 'Dashboard' },
    { key: 'approval', label: 'Request Approval', icon: 'approval', title: 'Request Approval' },
    { key: 'transactions', label: 'Transaction History', icon: 'history', title: 'Transaction History' },
    ...COMMON
  ]
};

export function navFor(user) {
  return NAVIGATION[user?.role] || NAVIGATION.BORROWER;
}

export function canSee(user, key) {
  return navFor(user).some((item) => item.key === key);
}

export function setTheme(theme) {
  const root = document.documentElement;
  if (root.dataset.theme && root.dataset.theme !== theme) {
    root.classList.add('theme-anim');
    window.setTimeout(() => root.classList.remove('theme-anim'), 380);
  }
  root.dataset.theme = theme;
  localStorage.setItem('equiplink-theme', theme);
  const dark = theme === 'dark';
  const icon = document.getElementById('themeIcon');
  const label = document.getElementById('themeLabel');
  if (icon) icon.innerHTML = iconSvg(dark ? 'sun' : 'moon', 16);
  if (label) label.textContent = dark ? 'Light mode' : 'Dark mode';
}

export function initTheme() {
  // First visit is always light. Dark mode is enabled only by an explicit user choice.
  const saved = localStorage.getItem('equiplink-theme');
  setTheme(saved === 'dark' ? 'dark' : 'light');
}
