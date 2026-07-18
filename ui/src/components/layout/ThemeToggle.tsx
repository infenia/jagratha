// SPDX-License-Identifier: Apache-2.0
// SPDX-FileCopyrightText: 2026 Infenia Private Limited

import { useState, useEffect } from 'react';
import { getTheme, setTheme, type Theme } from '@/lib/theme';

export default function ThemeToggle() {
  const [theme, setCurrentTheme] = useState<Theme>('system');

  useEffect(() => {
    setCurrentTheme(getTheme());
  }, []);

  const toggleTheme = () => {
    const themes: Theme[] = ['light', 'dark', 'system'];
    const currentIndex = themes.indexOf(theme);
    const nextTheme = themes[(currentIndex + 1) % themes.length];
    setTheme(nextTheme);
    setCurrentTheme(nextTheme);
  };

  const iconName =
    theme === 'dark'
      ? 'dark_mode'
      : theme === 'light'
        ? 'light_mode'
        : 'brightness_auto';

  return (
    <button
      onClick={toggleTheme}
      className="p-2 rounded hover:bg-surface-container-low transition-colors"
      aria-label="Toggle theme"
      title={`Current: ${theme}`}
    >
      <span className="material-symbols-outlined">{iconName}</span>
    </button>
  );
}
