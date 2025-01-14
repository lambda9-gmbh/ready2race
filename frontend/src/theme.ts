import {createTheme, Theme, ThemeOptions} from '@mui/material'

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

export const muiTheme = (): Theme => {
    const theme = createTheme(baseThemeOptions)
    return createTheme(theme, componentOverrides(theme))
}
