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
  { key: 'notifications', label: 'Notifications', icon: '🔔', title: 'Notifications' },
  { key: 'settings', label: 'Settings', icon: '⚙', title: 'Settings' }
];

export const NAVIGATION = {
  BORROWER: [
    { key: 'dashboard', label: 'Dashboard', icon: '▦', title: 'Dashboard' },
    { key: 'equipment', label: 'Available Equipment', icon: '▣', title: 'Available Equipment' },
    { key: 'request', label: 'Request Equipment', icon: '＋', title: 'Request Equipment' },
    { key: 'my-requests', label: 'My Requests', icon: '▤', title: 'My Requests' },
    { key: 'history', label: 'Borrowing Request', icon: '◷', title: 'Borrowing Request' },
    ...COMMON
  ],
  ICT_STAFF: [
    { key: 'dashboard', label: 'Dashboard', icon: '▦', title: 'Dashboard' },
    { key: 'inventory', label: 'Inventory', icon: '▣', title: 'Inventory' },
    { key: 'requests', label: 'Requests', icon: '▤', title: 'Requests' },
    { key: 'schedule', label: 'Schedule & Assignment', icon: '▦', title: 'Schedule & Assignment' },
    { key: 'transactions', label: 'Transaction History', icon: '◷', title: 'Transaction History' },
    { key: 'password-requests', label: 'Password Requests', icon: '⚿', title: 'Password Requests' },
    { key: 'users', label: 'User Management', icon: '♙', title: 'User Management' },
    ...COMMON
  ],
  FACILITY_STAFF: [
    { key: 'dashboard', label: 'Dashboard', icon: '▦', title: 'Dashboard' },
    { key: 'inventory', label: 'Inventory', icon: '▣', title: 'Inventory' },
    { key: 'requests', label: 'Requests', icon: '▤', title: 'Requests' },
    { key: 'schedule', label: 'Schedule & Assignment', icon: '▦', title: 'Schedule & Assignment' },
    { key: 'transactions', label: 'Transaction History', icon: '◷', title: 'Transaction History' },
    ...COMMON
  ],
  ADMIN: [
    { key: 'dashboard', label: 'Dashboard', icon: '▦', title: 'Dashboard' },
    { key: 'inventory', label: 'Inventory', icon: '▣', title: 'Inventory' },
    { key: 'borrowing-requests', label: 'Borrowing Requests', icon: '✔', title: 'Borrowing Requests' },
    { key: 'all-requests', label: 'All Requests', icon: '▤', title: 'All Requests' },
    { key: 'transactions', label: 'Transaction History', icon: '◷', title: 'Transaction History' },
    ...COMMON
  ],
  DIRECTOR: [
    { key: 'dashboard', label: 'Dashboard', icon: '▦', title: 'Dashboard' },
    { key: 'approval', label: 'Request Approval', icon: '✔', title: 'Request Approval' },
    { key: 'transactions', label: 'Transaction History', icon: '◷', title: 'Transaction History' },
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
  document.documentElement.dataset.theme = theme;
  localStorage.setItem('equiplink-theme', theme);
  const dark = theme === 'dark';
  const icon = document.getElementById('themeIcon');
  const label = document.getElementById('themeLabel');
  if (icon) icon.textContent = dark ? '☀' : '☾';
  if (label) label.textContent = dark ? 'Light mode' : 'Dark mode';
}

export function initTheme() {
  const saved = localStorage.getItem('equiplink-theme');
  const preferred = saved || (matchMedia('(prefers-color-scheme: dark)').matches ? 'dark' : 'light');
  setTheme(preferred);
}
