import React, {useState, useEffect, useMemo, useCallback} from 'react'
import ThemeContext from './ThemeContext'
import type {ThemeConfigDto} from '../../api'
import Config from '../../Config.ts'

const DEFAULT_THEME: ThemeConfigDto = {
    primary: {
        main: '#4d9f85',
        light: '#ecfaf7',
    },
    textColor: {
        primary: '#1d1d1d',
        secondary: '#666666',
    },
    actionColors: {
        success: '#cbe694',
        warning: '#f5d9b0',
        error: '#da4d4d',
        info: '#6fb0d4',
    },
    backgroundColor: '#ffffff',
    customFont: {
        enabled: false,
        filename: null,
    },
    customLogo: {
        enabled: false,
        filename: null,
    },
}

interface ThemeProviderProps {
    children: React.ReactNode
}

export function ThemeProvider({children}: ThemeProviderProps) {
    const [themeConfig, setThemeConfig] = useState<ThemeConfigDto | null>(null)

    const loadTheme = useCallback(async () => {
        try {
            const response = await fetch(Config.themeUrl)
            if (!response.ok) {
                console.warn('Failed to load theme, using defaults')
                setThemeConfig(DEFAULT_THEME)
                return
            }
            const theme = await response.json()
            setThemeConfig(theme)

            // Inject custom font if enabled
            if (theme.customFont?.enabled && theme.customFont?.filename) {
                const fontFace = `
                      @font-face {
                        font-family: 'CustomFont';
                        src: url('${Config.fontsUrl}/${theme.customFont.filename}') format('woff2'),
                             url('${Config.fontsUrl}/${theme.customFont.filename}') format('woff');
                        font-weight: normal;
                        font-style: normal;
                      }
                    `

                // Remove existing custom font style if any
                const existingStyle = document.getElementById('custom-font-style')
                if (existingStyle) {
                    existingStyle.remove()
                }

                // Inject new custom font style
                const styleElement = document.createElement('style')
                styleElement.id = 'custom-font-style'
                styleElement.textContent = fontFace
                document.head.appendChild(styleElement)
            } else {
                // Remove custom font style if custom font is disabled
                const existingStyle = document.getElementById('custom-font-style')
                if (existingStyle) {
                    existingStyle.remove()
                }
            }
        } catch (error) {
            console.error('Error loading theme:', error)
            setThemeConfig(DEFAULT_THEME)
        }
    }, [])

    useEffect(() => {
        void loadTheme()
    }, [loadTheme])

    const contextValue = useMemo(
        () => ({
            themeConfig,
            reloadTheme: loadTheme,
        }),
        [themeConfig, loadTheme],
    )

    return <ThemeContext.Provider value={contextValue}>{children}</ThemeContext.Provider>
}
