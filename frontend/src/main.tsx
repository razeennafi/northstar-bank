/*
 * Portfolio author: Razeen Abdun Nafi
 * Date: 2026-11-22
 * File: main.tsx
 * Description: Mounts the React application and loads shared styles.
 * Project version: 0.1.0
 */

import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import { App } from './App'
import './styles.css'

// Mounts the workspace and enables React’s development checks for unsafe effects.
createRoot(document.getElementById('root')!).render(
  <StrictMode>
    <App />
  </StrictMode>,
)
