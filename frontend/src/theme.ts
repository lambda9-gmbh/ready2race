import {createTheme, Theme, ThemeOptions} from '@mui/material'

const baseThemeOptions: ThemeOptions = {}

const componentOverrides = (theme: Theme): ThemeOptions => ({})

export const muiTheme = (): Theme => {
    const theme = createTheme(baseThemeOptions)
    return createTheme(theme, componentOverrides(theme))
}
