import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import { BrowserRouter } from 'react-router-dom'
import './index.css'
import App from './App.tsx'
import { SILENT_CALLBACK_PATH, userManager } from './services/oidc'

if (window.location.pathname === SILENT_CALLBACK_PATH) {
  // Iframe of the silent sign-in (SDD-024 F3): hand the result to the parent window
  // without rendering the app.
  void userManager.signinSilentCallback()
} else {
  createRoot(document.getElementById('root')!).render(
    <StrictMode>
      <BrowserRouter>
        <App />
      </BrowserRouter>
    </StrictMode>,
  )
}
