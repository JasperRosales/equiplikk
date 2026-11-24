/**
 * Browser equivalent of src.util.NotificationSound. The frequencies, note lengths,
 * attack and gain mirror the desktop synthesizer so both interfaces sound the same.
 * No audio file is needed, and playback is best-effort like the desktop version.
 */
const PROFILES = {
  Chime: { frequencies: [783.99, 1046.5], noteMillis: 210 },
  Bell: { frequencies: [1318.5, 1046.5], noteMillis: 260 },
  Soft: { frequencies: [523.3, 659.3], noteMillis: 220 },
  Alert: { frequencies: [880, 880, 1174.7], noteMillis: 150 }
};

let context = null;
let unlocked = false;
let pending = null;
let lastPlayedAt = 0;

export function normalizeSound(name) {
  const key = String(name || '').trim();
  return PROFILES[key] ? key : 'Chime';
}

export function soundNames() {
  return Object.keys(PROFILES);
}

/** Browsers require a user gesture before audio. Call this from login or the first click/key. */
export function unlockSound() {
  if (unlocked) {
    context?.resume().catch(() => {});
    return;
  }
  try {
    const AudioContextClass = window.AudioContext || window.webkitAudioContext;
    if (!AudioContextClass) return;
    context = new AudioContextClass();
    unlocked = true;
    context.resume().then(() => {
      if (pending) {
        const profile = pending;
        pending = null;
        synthesize(profile);
      }
    }).catch(() => {});
    document.removeEventListener('pointerdown', unlockSound, true);
    document.removeEventListener('keydown', unlockSound, true);
  } catch (error) {
    // Sound is optional; visual notifications still work.
  }
}

export function initSound() {
  document.addEventListener('pointerdown', unlockSound, true);
  document.addEventListener('keydown', unlockSound, true);
}

export function notificationSoundAllowed(user) {
  return Boolean(user && user.notificationsEnabled !== false && user.notificationSoundEnabled);
}

/** True when Web Audio exists but the browser still requires a user gesture. */
export function soundNeedsGesture() {
  return !unlocked || !context || context.state !== 'running';
}

/** Returns false when blocked by autoplay rules; the caller must not claim sound played. */
export function playNotificationSound(name) {
  if (!unlocked || !context || context.state !== 'running') {
    pending = PROFILES[normalizeSound(name)];
    return false;
  }
  const profile = PROFILES[normalizeSound(name)];
  const now = Date.now();
  if (now - lastPlayedAt < 2500) return false; // same 2.5s throttle as desktop
  lastPlayedAt = now;
  synthesize(profile);
  return true;
}

function synthesize(profile) {
  if (!context || context.state !== 'running') return;
  let start = context.currentTime + 0.01;
  for (const frequency of profile.frequencies) {
    const duration = profile.noteMillis / 1000;
    const oscillator = context.createOscillator();
    const gain = context.createGain();
    oscillator.type = 'sine';
    oscillator.frequency.setValueAtTime(frequency, start);

    const envelope = new Float32Array(64);
    for (let index = 0; index < envelope.length; index += 1) {
      const progress = index / (envelope.length - 1);
      envelope[index] = Math.min(1, progress / 0.04) * Math.pow(1 - progress, 1.8) * 0.22;
    }
    gain.gain.setValueCurveAtTime(envelope, start, duration);
    oscillator.connect(gain).connect(context.destination);
    oscillator.start(start);
    oscillator.stop(start + duration + 0.01);
    start += duration;
  }
}
