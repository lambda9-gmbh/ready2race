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
import {LocalizationProvider} from "@mui/x-date-pickers";
import {AdapterDayjs} from "@mui/x-date-pickers/AdapterDayjs";
import {enUS as enDatePicker} from '@mui/x-date-pickers/locales/enUS'
import {deDE as deDatePicker} from '@mui/x-date-pickers/locales/deDE'
import i18next from "i18next";

client.setConfig({
    baseUrl: Config.api.baseUrl,
    credentials: 'include',
})

const language = document.getElementById('ready2race-root')!.dataset.lng ?? 'de'
i18next.changeLanguage(language)

const theme = muiTheme()

const App = () => {
    const user = useUser()

    const localeText =
        language === 'en'
            ? enDatePicker.components.MuiLocalizationProvider.defaultProps.localeText
            : deDatePicker.components.MuiLocalizationProvider.defaultProps.localeText

    return (
        <LocalizationProvider
        dateAdapter={AdapterDayjs}
        adapterLocale={language}
        localeText={localeText}>
        <ThemeProvider theme={theme}>
            <SnackbarProvider maxSnack={3} anchorOrigin={{vertical: 'bottom', horizontal: 'right'}}>
                <ConfirmationProvider>
                    <RouterProvider router={router} context={user}></RouterProvider>
                </ConfirmationProvider>
            </SnackbarProvider>
        </ThemeProvider>
        </LocalizationProvider>
    )
}

export default App
