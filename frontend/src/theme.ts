import {createTheme, Theme, ThemeOptions} from '@mui/material'
import {Locale} from './i18n/config.ts'

const baseThemeOptions: ThemeOptions = {
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
        common: {
            black: '#1d1d1d',
        },
        primary: {
            main: '#4d9f85',
            light: '#ecfaf7',
        },
    },
    typography: {
        h1: {
            fontSize: '3rem',
            fontWeight: 'normal',
        },
        h2: {
            fontSize: '2rem',
            fontWeight: 'normal',
        },
        h3: {
            fontSize: '1.5rem',
            fontWeight: 'normal',
        },
        subtitle1: {
            fontSize: '1.3rem',
            fontWeight: 'normal',
        },
        body1: {
            fontSize: '1rem',
        },
    },
}

const componentOverrides = (_theme: Theme): ThemeOptions => ({
    components: {
        MuiAppBar: {
            styleOverrides: {
                root: {
                    background: _theme.palette.primary.light,
                },
            },
        },
        MuiButton: {
            styleOverrides: {
                root: {
                    textTransform: 'none',
                },
            },
        },
        MuiTab: {
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
                        width: '25px',
                    },
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
