const fs = require('fs');
const path = require('path');
const { loadApp, APP_PATH } = require('./setup/loadApp');

const CSS_PATH = path.join(path.dirname(APP_PATH), 'style.css');
const THEME_KEY = 'ops-theme';
const HEX_COLOUR = /#[0-9a-fA-F]{3,8}\b/;

/** Stub the OS colour-scheme preference. Returns a function that changes it and fires 'change'. */
function preferScheme(scheme) {
  const query = {
    matches: scheme === 'dark',
    media: '(prefers-color-scheme: dark)',
    listeners: [],
    addEventListener(type, fn) { if (type === 'change') this.listeners.push(fn); },
    removeEventListener() {}
  };
  window.matchMedia = jest.fn(() => query);
  return function setScheme(next) {
    query.matches = next === 'dark';
    query.listeners.forEach((fn) => fn({ matches: query.matches }));
  };
}

function currentTheme(document) {
  return document.documentElement.getAttribute('data-theme');
}

beforeEach(() => {
  localStorage.clear();
  document.documentElement.removeAttribute('data-theme');
  preferScheme('light');
});

afterAll(() => {
  delete window.matchMedia;
});

describe('theme toggle (TODO-231)', () => {
  test('AC-1: a toggle in the header switches theme and names the theme you will get', async () => {
    const { document } = await loadApp();
    const toggle = document.getElementById('theme-toggle');
    expect(document.getElementById('app-header').contains(toggle)).toBe(true);

    expect(currentTheme(document)).toBe('light');
    expect(toggle.textContent).toMatch(/dark/i);

    toggle.click();
    expect(currentTheme(document)).toBe('dark');
    expect(toggle.textContent).toMatch(/light/i);

    toggle.click();
    expect(currentTheme(document)).toBe('light');
  });

  test('AC-2: colours live in CSS variables, the charts use them, and app.js has no colours', () => {
    const css = fs.readFileSync(CSS_PATH, 'utf8');
    const rootBlock = css.match(/:root\s*\{[^}]*\}/);
    expect(rootBlock).not.toBeNull();
    expect(css).toMatch(/:root\[data-theme="dark"\]\s*\{[^}]*color-scheme:\s*dark/);
    expect(css).toMatch(/:root\[data-theme="light"\]\s*\{[^}]*color-scheme:\s*light/);

    // Every hex colour is declared once, in :root; no rule outside it hardcodes one.
    expect(css.replace(rootBlock[0], '')).not.toMatch(HEX_COLOUR);

    for (const selector of ['.chart-svg .bar', '.chart-svg .bar.warn', '.chart-svg .bar-label', '.chart-svg .bar-value']) {
      const escaped = selector.replace(/\./g, '\\.');
      expect(css).toMatch(new RegExp(escaped + '\\s*\\{[^}]*fill:\\s*var\\(--'));
    }

    expect(fs.readFileSync(APP_PATH, 'utf8')).not.toMatch(HEX_COLOUR);
  });

  test('AC-3: a theme that differs from the OS preference is stored and restored on load', async () => {
    let { document } = await loadApp();
    document.getElementById('theme-toggle').click();
    expect(localStorage.getItem(THEME_KEY)).toBe('dark');

    document.documentElement.removeAttribute('data-theme');
    ({ document } = await loadApp());
    expect(currentTheme(document)).toBe('dark');
  });

  test('AC-4: with nothing stored, the theme follows the OS preference', async () => {
    let { document } = await loadApp();
    expect(currentTheme(document)).toBe('light');

    preferScheme('dark');
    ({ document } = await loadApp());
    expect(currentTheme(document)).toBe('dark');
    expect(localStorage.getItem(THEME_KEY)).toBeNull();
  });

  test('AC-4: switching back to the OS preference clears the stored value', async () => {
    preferScheme('dark');
    const { document } = await loadApp();
    const toggle = document.getElementById('theme-toggle');

    toggle.click();
    expect(localStorage.getItem(THEME_KEY)).toBe('light');

    toggle.click();
    expect(currentTheme(document)).toBe('dark');
    expect(localStorage.getItem(THEME_KEY)).toBeNull();
  });

  test('AC-4: an OS preference change is followed live unless the user picked a theme', async () => {
    const setScheme = preferScheme('light');
    const { document } = await loadApp();

    setScheme('dark');
    expect(currentTheme(document)).toBe('dark');

    document.getElementById('theme-toggle').click(); // light, differs from OS: stored
    setScheme('dark');
    expect(currentTheme(document)).toBe('light');
  });
});
