import { useEffect, useMemo, useState } from 'react'
import { ThemeContext, type Theme } from '../hooks/useTheme'

function getInitialTheme(): Theme {
  try {
    const saved = localStorage.getItem('mms_theme')
    if (saved === 'dark' || saved === 'light') return saved
  } catch (_) {}
  if (typeof window !== 'undefined' && window.matchMedia?.('(prefers-color-scheme: dark)').matches) return 'dark'
  return 'light'
}

export default function ThemeProvider({ children }: { children: React.ReactNode }) {
  const [theme, setTheme] = useState<Theme>(getInitialTheme)

  useEffect(() => {
    document.documentElement.classList.toggle('dark', theme === 'dark')
    try { localStorage.setItem('mms_theme', theme) } catch (_) {}
  }, [theme])

  const value = useMemo(() => ({
    theme,
    setTheme,
    toggleTheme: () => setTheme((t) => (t === 'dark' ? 'light' : 'dark')),
  }), [theme])

  return <ThemeContext.Provider value={value}>{children}</ThemeContext.Provider>
}
