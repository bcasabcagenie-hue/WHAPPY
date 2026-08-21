type WebAudioWindow = Window & typeof globalThis & {
  webkitAudioContext?: typeof AudioContext;
};

let audioContext: AudioContext | null = null;

function context() {
  if (typeof window === "undefined") return null;
  const Constructor = window.AudioContext || (window as WebAudioWindow).webkitAudioContext;
  if (!Constructor) return null;
  audioContext ||= new Constructor();
  if (audioContext.state === "suspended") void audioContext.resume().catch(() => {});
  return audioContext;
}

function note(frequency: number, delay: number, duration: number, volume: number) {
  const sound = context();
  if (!sound) return;
  const oscillator = sound.createOscillator();
  const gain = sound.createGain();
  const start = sound.currentTime + delay;
  oscillator.type = "sine";
  oscillator.frequency.setValueAtTime(frequency, start);
  gain.gain.setValueAtTime(0.0001, start);
  gain.gain.exponentialRampToValueAtTime(volume, start + 0.018);
  gain.gain.exponentialRampToValueAtTime(0.0001, start + duration);
  oscillator.connect(gain);
  gain.connect(sound.destination);
  oscillator.start(start);
  oscillator.stop(start + duration + 0.03);
}

export function playMessageSound() {
  note(659.25, 0, 0.12, 0.055);
  note(880, 0.1, 0.18, 0.045);
}

export function playMediaAddedSound() {
  note(587.33, 0, 0.08, 0.035);
  note(783.99, 0.07, 0.11, 0.045);
  note(987.77, 0.15, 0.16, 0.035);
}

export function playOfferSuccessSound() {
  note(523.25, 0, 0.09, 0.04);
  note(659.25, 0.08, 0.1, 0.045);
  note(880, 0.17, 0.2, 0.04);
}

export function playCallConnectedSound() {
  note(523.25, 0, 0.11, 0.05);
  note(659.25, 0.11, 0.11, 0.05);
  note(783.99, 0.22, 0.2, 0.045);
}

export function startRingtone(kind: "incoming" | "outgoing") {
  let stopped = false;
  const ring = () => {
    if (stopped) return;
    if (kind === "incoming") {
      note(783.99, 0, 0.32, 0.065);
      note(987.77, 0.36, 0.32, 0.06);
      note(783.99, 0.72, 0.32, 0.055);
    } else {
      note(440, 0, 0.28, 0.04);
      note(554.37, 0.32, 0.28, 0.035);
    }
  };
  ring();
  const timer = window.setInterval(ring, kind === "incoming" ? 2200 : 2600);
  return () => {
    stopped = true;
    window.clearInterval(timer);
  };
}
