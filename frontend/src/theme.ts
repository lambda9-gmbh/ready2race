import {createTheme, Theme, ThemeOptions} from '@mui/material'
import {Locale} from './i18n/config.ts'

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

export const muiTheme = (locale: Locale): Theme => {
    const theme = createTheme(baseThemeOptions)

    return createTheme(
        {
            ...theme,
            ...componentOverrides(theme),
        },
        locale.material,
        locale.dataGrid,
    )
}
