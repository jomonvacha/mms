import React, {useEffect, useMemo, useState} from 'react';
import {ThemeContext} from '../hooks/useTheme.js';

export default function ThemeProvider({children}) {
  const [theme, setTheme] = useState(() => {
    try {
      const saved = localStorage.getItem('mms_theme');
      return saved === 'dark' ? 'dark' : 'light';
    } catch (_) {
      return 'light';
    }
  });

  useEffect(() => {
    try {
      localStorage.setItem('mms_theme', theme);
    } catch (_) {
    }
    const el = document.documentElement;
    el.setAttribute('data-bs-theme', theme);
  }, [theme]);

  const value = useMemo(() => ({
    theme,
    setTheme,
    toggleTheme: () => setTheme((t) => (t === 'dark' ? 'light' : 'dark')),
  }), [theme]);

  return (
    <ThemeContext.Provider value={value}>{children}</ThemeContext.Provider>
  );
}

