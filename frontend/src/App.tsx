import {useUser} from './contexts/user/UserContext'
import {RouterProvider} from '@tanstack/react-router'
import {router} from '@routes'
import {client} from './api'
import Config from './Config'
import {muiTheme} from './theme'
import {ThemeProvider} from '@mui/material'
import {SnackbarProvider} from 'notistack'
import {ConfirmationProvider} from './contexts/confirmation/ConfirmationProvider.tsx'
import {LocalizationProvider} from '@mui/x-date-pickers'
import {AdapterDateFns} from '@mui/x-date-pickers/AdapterDateFnsV3'
import i18next from 'i18next'
import './i18n/config'
import {fallbackLng, Language, locales} from './i18n/config.ts'

client.setConfig({
    baseUrl: Config.api.baseUrl,
    credentials: 'include',
})

const language: Language = (document.getElementById('ready2race-root')!.dataset.lng ??
    fallbackLng) as Language
// todo: throw error on unfitting lng
i18next.changeLanguage(language).then()

const locale = locales[language]
const theme = muiTheme(locale)

const App = () => {
    const user = useUser()

    return (
        <LocalizationProvider
            dateAdapter={AdapterDateFns}
            adapterLocale={locale.date}
            localeText={locale.datePicker}>
            <ThemeProvider theme={theme}>
                <SnackbarProvider
                    maxSnack={1}
                    anchorOrigin={{vertical: 'bottom', horizontal: 'right'}}>
                    <ConfirmationProvider>
                        <RouterProvider router={router} context={user}></RouterProvider>
                    </ConfirmationProvider>
                </SnackbarProvider>
            </ThemeProvider>
        </LocalizationProvider>
    )
}

export default App
