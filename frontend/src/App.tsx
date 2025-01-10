import './App.css'
import {useUser} from './contexts/user/UserContext.ts'
import {RouterProvider} from '@tanstack/react-router'
import {router} from './routes.tsx'
import {client} from './api'
import Config from './Config.ts'
import {muiTheme} from './theme.ts'
import {ThemeProvider} from '@mui/material'

client.setConfig({
    baseUrl: Config.api.baseUrl,
    credentials: 'include',
})

const theme = muiTheme()

function App() {
    const user = useUser()

    return (
        <ThemeProvider theme={theme}>
            <RouterProvider router={router} context={user}></RouterProvider>
        </ThemeProvider>
    )
}

export default App
