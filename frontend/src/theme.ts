import {createTheme, Theme, ThemeOptions} from '@mui/material'
import {Locale} from './i18n/config.ts'

const baseThemeOptions: ThemeOptions = {}

const componentOverrides = (_theme: Theme): ThemeOptions => ({
    palette: {
        mode: 'light',
        success: {
            main: '#cbe694',
        },
        warning: {
            main: '#f5d9b0',
        },
        error: {
            main: '#da4d4d',
        },
        info: {
            main: '#6fb0d4',
        },
        background: {
            paper: '#fafafa',
        },
        common:{
            black: '#1d1d1d'
        }
    },
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
        },
    },
    components: {
        MuiButton: {
            styleOverrides: {
                root: {
                    textTransform: 'none',
                },
            },
        },
        MuiToolbar: {
            styleOverrides: {
                root: {
                    '& .MuiSvgIcon-root': {
                        height: '25px',
                        width: '25px'
                    }
                }
            }
        }
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
