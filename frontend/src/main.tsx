import {StrictMode} from 'react'
import {createRoot} from 'react-dom/client'
import App from './App.tsx'
import UserProvider from './contexts/user/UserProvider.tsx'
import {getRootElement} from './utils/helpers.ts'
import '@fontsource/roboto/300.css'
import '@fontsource/roboto/400.css'
import '@fontsource/roboto/500.css'
import '@fontsource/roboto/700.css'
import './index.scss'

createRoot(getRootElement()).render(
    <StrictMode>
        <UserProvider>
            <App />
        </UserProvider>
    </StrictMode>,
)
