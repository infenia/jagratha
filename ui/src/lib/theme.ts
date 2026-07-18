export type Theme = 'light' | 'dark' | 'system';

export function getTheme(): Theme {
  const stored = localStorage.getItem('theme');
  return (stored as Theme) || 'system';
}

export function setTheme(theme: Theme): void {
  localStorage.setItem('theme', theme);

  const isDark =
    theme === 'dark' ||
    (theme === 'system' &&
      window.matchMedia('(prefers-color-scheme: dark)').matches);

  if (isDark) {
    document.documentElement.classList.add('dark');
  } else {
    document.documentElement.classList.remove('dark');
  }
}

export function useTheme() {
  const theme = getTheme();
  return { theme, setTheme };
}
