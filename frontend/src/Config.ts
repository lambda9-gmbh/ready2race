const Config = {
    mode: import.meta.env.MODE,
    api: {
        baseUrl: import.meta.env.VITE_API_BASE_URL,
    } as const,
    themeUrl: import.meta.env.VITE_THEME_URL,
    fontsUrl: import.meta.env.VITE_FONTS_URL,
} as const

export default Config
