import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import './index.css'
import App from './App.tsx'
import { AuthProvider } from './auth'
import { InstanceProvider } from './instance'

createRoot(document.getElementById('root')!).render(
  <StrictMode>
    <InstanceProvider>
      <AuthProvider>
        <App />
      </AuthProvider>
    </InstanceProvider>
  </StrictMode>,
)
