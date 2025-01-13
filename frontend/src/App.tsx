import './i18n/config'
import './App.css'
import {useUser} from './contexts/user/UserContext'
import {RouterProvider} from '@tanstack/react-router'
import {router} from './routes'
import {client} from './api'
import Config from './Config'
import {muiTheme} from './theme'
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
