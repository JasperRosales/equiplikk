/**
 * Small motion helpers that mirror src/ui/components/Motion.java and DashboardCard.java:
 *  - press ripple that expands from the pointer position
 *  - whole-number count-up (450ms, ease-out cubic) for dashboard counters
 */
const reduced = () => window.matchMedia?.('(prefers-reduced-motion: reduce)').matches;

export function initMotion() {
  document.addEventListener('pointerdown', (event) => {
    if (reduced() || event.button > 0) return;
    const host = event.target.closest?.('.btn:not(:disabled), .stat-card, .quick-action');
    if (!host) return;
    const rect = host.getBoundingClientRect();
    const size = Math.hypot(Math.max(event.clientX - rect.left, rect.right - event.clientX),
      Math.max(event.clientY - rect.top, rect.bottom - event.clientY)) * 2;
    const dot = document.createElement('span');
    dot.className = 'ripple';
    dot.style.width = dot.style.height = `${size}px`;
    dot.style.left = `${event.clientX - rect.left - size / 2}px`;
    dot.style.top = `${event.clientY - rect.top - size / 2}px`;
    host.appendChild(dot);
    dot.addEventListener('animationend', () => dot.remove(), { once: true });
  }, { passive: true });
}

export function countUp(element, target, duration = 450) {
  const end = Number(target);
  if (!Number.isFinite(end) || reduced() || end === 0) {
    element.textContent = String(target);
    return;
  }
  const start = performance.now();
  const frame = (now) => {
    const t = Math.min(1, (now - start) / duration);
    const eased = 1 - Math.pow(1 - t, 3);
    element.textContent = String(Math.round(end * eased));
    if (t < 1 && element.isConnected) requestAnimationFrame(frame);
    else element.textContent = String(target);
  };
  element.textContent = '0';
  requestAnimationFrame(frame);
}
