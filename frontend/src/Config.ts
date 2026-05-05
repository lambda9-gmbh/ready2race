const Config = {
    mode: import.meta.env.MODE,
    api: {
        baseUrl: import.meta.env.VITE_API_BASE_URL,
    } as const,
    themeUrl: import.meta.env.VITE_THEME_URL,
    fontsUrl: import.meta.env.VITE_FONTS_URL,
    logosUrl: import.meta.env.VITE_LOGOS_URL,
    r2rLogoUrl: import.meta.env.VITE_R2R_LOGO_URL,
} as const

export default Config
