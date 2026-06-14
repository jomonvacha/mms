import React from 'react'
import ReactDOM from 'react-dom/client'
import { BrowserRouter } from 'react-router-dom'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { Toaster } from 'react-hot-toast'
import App from './App'
import ThemeProvider from './context/ThemeProvider'
import { AuthProvider } from './context/AuthProvider'
import { legacyToastRenderer } from './components/Toast'
import './i18n'           // initializes i18next before any component renders
import './index.css'

const queryClient = new QueryClient({
  defaultOptions: {
    queries: { staleTime: 5 * 60 * 1000, retry: 1 },
  },
})

ReactDOM.createRoot(document.getElementById('root')!).render(
  <React.StrictMode>
    <BrowserRouter>
      <ThemeProvider>
        <QueryClientProvider client={queryClient}>
          <AuthProvider>
            <App />
            {/* Themed toaster — re-skins every call site (toast.success / toast.error)
                through ThemedToast via legacyToastRenderer. See mms-ui/src/components/Toast.tsx. */}
            <Toaster
              position="top-right"
              gutter={10}
              toastOptions={{
                duration: 4000,
                style: { background: 'transparent', boxShadow: 'none', padding: 0, margin: 0, maxWidth: 'none' },
                ariaProps: { role: 'status', 'aria-live': 'polite' },
              }}>
              {(t) => legacyToastRenderer(t)}
            </Toaster>
          </AuthProvider>
        </QueryClientProvider>
      </ThemeProvider>
    </BrowserRouter>
  </React.StrictMode>,
)
