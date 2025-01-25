import './i18n/config'
import {useUser} from './contexts/user/UserContext'
import {RouterProvider} from '@tanstack/react-router'
import {router} from './routes'
import {client} from './api'
import Config from './Config'
import {muiTheme} from './theme'
import {ThemeProvider} from '@mui/material'
import {SnackbarProvider} from 'notistack'

client.setConfig({
    baseUrl: Config.api.baseUrl,
    credentials: 'include',
})

const theme = muiTheme()

function App() {
    const user = useUser()

    return (
        <ThemeProvider theme={theme}>
            <SnackbarProvider maxSnack={3} anchorOrigin={{vertical: 'bottom', horizontal: 'right'}}>
                <RouterProvider router={router} context={user}></RouterProvider>
            </SnackbarProvider>
        </ThemeProvider>
    )
}

export default App
