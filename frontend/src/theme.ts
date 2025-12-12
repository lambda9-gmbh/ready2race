import {createTheme, Theme, ThemeOptions} from '@mui/material'
import {Locale} from './i18n/config.ts'
import type {ThemeConfigDto} from './api'

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
        text: {
            primary: '#1d1d1d',
            secondary: '#666666',
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
                    '.mobile-optimized-layout &': {
                        [_theme.breakpoints.down('sm')]: {
                            minHeight: '3.5rem',
                            fontSize: '1.2rem',
                            padding: '0.75rem 1rem',
                            fontWeight: 600,
                        },
                    },
                },
            },
            defaultProps: {
                size: 'large',
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
        MuiTextField: {
            styleOverrides: {
                root: {
                    [_theme.breakpoints.down('sm')]: {
                        '& .MuiInputBase-root': {
                            fontSize: '1.1rem',
                        },
                        '& .MuiInputLabel-root': {
                            fontSize: '1.1rem',
                        },
                    },
                },
            },
            defaultProps: {
                size: 'medium',
            },
        },
        MuiDialog: {
            defaultProps: {
                maxWidth: 'sm',
                fullWidth: true,
            },
        },
    },
})

export const muiTheme = (locale: Locale, themeConfig: ThemeConfigDto | null): Theme => {
    // Apply theme config overrides if available
    const themeOptions: ThemeOptions = themeConfig
        ? {
              ...baseThemeOptions,
              palette: {
                  ...baseThemeOptions.palette,
                  primary: {
                      ...baseThemeOptions.palette?.primary,
                      main: themeConfig.primary.main,
                      light: themeConfig.primary.light,
                  },
                  text: {
                      ...baseThemeOptions.palette?.text,
                      primary: themeConfig.textColor.primary,
                      secondary: themeConfig.textColor.secondary,
                  },
                  background: {
                      ...baseThemeOptions.palette?.background,
                      default: themeConfig.backgroundColor,
                      paper: themeConfig.backgroundColor,
                  },
                  success: {
                      ...baseThemeOptions.palette?.success,
                      main: themeConfig.actionColors.success,
                  },
                  warning: {
                      ...baseThemeOptions.palette?.warning,
                      main: themeConfig.actionColors.warning,
                  },
                  error: {
                      ...baseThemeOptions.palette?.error,
                      main: themeConfig.actionColors.error,
                  },
                  info: {
                      ...baseThemeOptions.palette?.info,
                      main: themeConfig.actionColors.info,
                  },
              },
              typography: {
                  ...baseThemeOptions.typography,
                  fontFamily: themeConfig.customFont?.enabled
                      ? "'CustomFont', 'Roboto', 'Helvetica', 'Arial', sans-serif"
                      : "'Roboto', 'Helvetica', 'Arial', sans-serif",
              },
          }
        : baseThemeOptions

    const theme = createTheme(themeOptions)

    return createTheme(
        {
            ...theme,
            ...componentOverrides(theme),
        },
        locale.material,
        locale.dataGrid,
    )
}
