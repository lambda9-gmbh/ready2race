import {createTheme, Theme, ThemeOptions} from '@mui/material'
import {Locale} from './i18n/config.ts'

const baseThemeOptions: ThemeOptions = {}

const componentOverrides = (_theme: Theme): ThemeOptions => ({
    typography: {
        h1: {
            fontSize: '3rem',
            fontWeight: 'normal'
        },
        h2: {
            fontSize: '2rem',
            fontWeight: 'normal'
        },
        h3: {
            fontSize: '1.5rem',
            fontWeight: 'normal'
        },
        subtitle1: {
            fontSize: '1.3rem',
            fontWeight: 'normal'
        },
        body1:{
            fontSize: '1rem',
        }
    },
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
