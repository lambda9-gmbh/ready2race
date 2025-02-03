import './i18n/config'
import {useUser} from './contexts/user/UserContext'
import {RouterProvider} from '@tanstack/react-router'
import {router} from './routes'
import {client} from './api'
import Config from './Config'
import {muiTheme} from './theme'
import {ThemeProvider} from '@mui/material'
import {SnackbarProvider} from 'notistack'
import {ConfirmationProvider} from './contexts/confirmation/ConfirmationProvider.tsx'

client.setConfig({
    baseUrl: Config.api.baseUrl,
    credentials: 'include',
})

const theme = muiTheme()

const App = () => {
    const user = useUser()

    return (
        <ThemeProvider theme={theme}>
            <SnackbarProvider maxSnack={3} anchorOrigin={{vertical: 'bottom', horizontal: 'right'}}>
                <ConfirmationProvider>
                    <RouterProvider router={router} context={user}></RouterProvider>
                </ConfirmationProvider>
            </SnackbarProvider>
        </ThemeProvider>
    )
}

export default App
