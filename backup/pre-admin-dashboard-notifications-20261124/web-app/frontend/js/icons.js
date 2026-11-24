/**
 * Monochrome SVG icons matching the Java2D Lucide-style icons in desktop 1.4.0.
 * All paths use the same 24x24 viewBox and 1.9px rounded stroke as LucideIcon.java.
 */
const PATHS = {
  DASHBOARD: '<rect x="3" y="3" width="7" height="7" rx="1.5"/><rect x="14" y="3" width="7" height="7" rx="1.5"/><rect x="3" y="14" width="7" height="7" rx="1.5"/><rect x="14" y="14" width="7" height="7" rx="1.5"/>',
  EQUIPMENT: '<rect x="3" y="7" width="18" height="14" rx="2"/><path d="M3 12h18M12 7v14"/><path d="M12 3 3 7l9 4 9-4-9-4Z"/><circle cx="12" cy="12" r="1.3" fill="currentColor" stroke="none"/>',
  REQUEST_EQUIPMENT: '<rect x="5" y="4" width="14" height="18" rx="2"/><rect x="9" y="2" width="6" height="4" rx="1"/><path d="M12 11v6M9 14h6"/>',
  MY_REQUESTS: '<rect x="5" y="4" width="14" height="18" rx="2"/><rect x="9" y="2" width="6" height="4" rx="1"/><path d="M9 11h6M9 15h6M9 19h4"/>',
  BORROWING: '<circle cx="12" cy="12" r="9"/><path d="M12 7.5V12l3 2"/><path d="M3.5 9V4h5"/><path d="m3.5 4 3 3"/>',
  NOTIFICATIONS: '<path d="M6 17H3.5L6 13V9"/><path d="M6 9a6 6 0 0 1 12 0"/><path d="M18 9v4l2.5 4H6"/><path d="M10 20.5h4"/>',
  SETTINGS: '<circle cx="12" cy="12" r="3.5"/><path d="M12 2v3M12 19v3M4.9 4.9 7 7M17 17l2.1 2.1M2 12h3M19 12h3M4.9 19.1 7 17M17 7l2.1-2.1"/>',
  INVENTORY: '<rect x="3" y="7" width="18" height="14" rx="2"/><path d="M3 12h18M12 7v14"/><path d="M12 3 3 7l9 4 9-4-9-4Z"/>',
  REQUESTS: '<path d="M3 12h4l2 3h6l2-3h4"/><path d="M21 12v6l-2 3H5l-2-3v-6"/>',
  SCHEDULE: '<rect x="3" y="5" width="18" height="16" rx="2"/><path d="M3 10h18M8 3v4M16 3v4"/><path d="m9 15.5 2.5 2.5 4.5-5"/>',
  HISTORY: '<circle cx="12" cy="12" r="9"/><path d="M12 7.5V12l3 2"/><path d="M3.5 9V4h5"/><path d="m3.5 4 3 3"/>',
  PASSWORD: '<circle cx="8" cy="15" r="4"/><path d="m10.5 13 9.5-10"/><path d="m17 6 3 3M14 9l3 3"/>',
  USERS: '<circle cx="9" cy="8" r="4"/><path d="M2 21v-2l4-4h6l4 4v2"/><circle cx="18" cy="8" r="3"/><path d="M18 13c2 0 4 1 4 3"/>',
  BORROWING_REQUESTS: '<rect x="5" y="4" width="14" height="18" rx="2"/><rect x="9" y="2" width="6" height="4" rx="1"/><path d="m8.5 14 2.5 2.5 4.5-5.5"/>',
  APPROVAL: '<circle cx="12" cy="12" r="9"/><path d="m8 12 3 3 5-6"/>',
  ALL_REQUESTS: '<path d="M9 6h12M9 12h12M9 18h12"/><circle cx="5" cy="6" r="1.5"/><circle cx="5" cy="12" r="1.5"/><circle cx="5" cy="18" r="1.5"/>',
  PLUS: '<path d="M12 5v14M5 12h14"/>',
  X: '<path d="M18 6 6 18M6 6l12 12"/>',
  MENU: '<path d="M4 6h16M4 12h16M4 18h16"/>',
  MOON: '<path d="M20 14.5A8.5 8.5 0 0 1 9.5 4 8.5 8.5 0 1 0 20 14.5Z"/>',
  SUN: '<circle cx="12" cy="12" r="4"/><path d="M12 2v2M12 20v2M4.9 4.9l1.4 1.4M17.7 17.7l1.4 1.4M2 12h2M20 12h2M4.9 19.1l1.4-1.4M17.7 6.3l1.4-1.4"/>'
};

export function icon(name, size = 16) {
  const paths = PATHS[String(name || '').toUpperCase()] || PATHS.ALL_REQUESTS;
  return `<svg class="icon" width="${size}" height="${size}" viewBox="0 0 24 24" fill="none" `
    + `stroke="currentColor" stroke-width="1.9" stroke-linecap="round" stroke-linejoin="round" `
    + `aria-hidden="true" focusable="false">${paths}</svg>`;
}

/** Vector equivalent of src/ui/components/EyeIcon.java. */
export function eyeIcon(crossed = false, size = 18) {
  const eye = '<path d="M1.4 12 Q12 1.4 22.6 12 Q12 22.6 1.4 12Z"/>'
    + '<circle cx="12" cy="12" r="3.6" fill="currentColor" stroke="none"/>';
  const slash = crossed ? '<path d="M3.4 20.6 L20.6 3.4"/>' : '';
  return svg(eye + slash, size);
}

function svg(paths, size) {
  return `<svg class="icon" width="${size}" height="${size}" viewBox="0 0 24 24" fill="none" `
    + `stroke="currentColor" stroke-width="1.9" stroke-linecap="round" stroke-linejoin="round" `
    + `aria-hidden="true" focusable="false">${paths}</svg>`;
}

export function syncPasswordIcons(root = document) {
  root.querySelectorAll('[data-password-toggle]').forEach((button) => {
    if (button.dataset.iconReady) return;
    const input = button.closest('.password-field')?.querySelector('input');
    const hidden = !input || input.type === 'password';
    button.innerHTML = eyeIcon(hidden);
    button.setAttribute('aria-label', hidden ? 'Show password' : 'Hide password');
    button.dataset.iconReady = 'true';
  });
}

export function initPasswordToggles() {
  document.addEventListener('click', (event) => {
    const button = event.target.closest('[data-password-toggle]');
    if (!button) return;
    event.preventDefault();
    const field = button.closest('.password-field');
    const input = field?.querySelector('input');
    if (!input) return;
    const revealing = input.type === 'password';
    input.type = revealing ? 'text' : 'password';
    button.innerHTML = eyeIcon(!revealing);
    button.setAttribute('aria-label', revealing ? 'Hide password' : 'Show password');
    input.focus({ preventScroll: true });
  });
  syncPasswordIcons(document);
}
