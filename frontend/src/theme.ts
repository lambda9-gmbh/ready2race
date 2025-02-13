import {createTheme, Theme, ThemeOptions} from '@mui/material'
import {deDE, enUS} from '@mui/material/locale'
import {enUS as enDataGrid} from '@mui/x-data-grid/locales/enUS'
import {enUS as enDatePicker} from '@mui/x-date-pickers/locales/enUS'
import {deDE as deDataGrid} from '@mui/x-data-grid/locales/deDE'
import {deDE as deDatePicker} from '@mui/x-date-pickers/locales/deDE'
import {Language} from './utils/types.ts'

const baseThemeOptions: ThemeOptions = {}

const componentOverrides = (_theme: Theme): ThemeOptions => ({
    components: {
        MuiButton: {
            styleOverrides: {
                root: {
                    textTransform: 'none',
                },
            },
        },
    },
})

export const muiTheme = (language: Language): Theme => {
    const theme = createTheme(baseThemeOptions)

    const loc =
        language === 'en' ? [enUS, enDataGrid, enDatePicker] : [deDE, deDataGrid, deDatePicker]

    return createTheme(
        {
            ...theme,
            ...componentOverrides(theme),
        },
        ...loc,
    )
}
