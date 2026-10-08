import {resolveRuntimeConfig} from './runtimeConfig'

const runtime = resolveRuntimeConfig(typeof window === 'undefined' ? undefined : window.READY2RACE_CONFIG, {
    apiBaseUrl: import.meta.env.VITE_API_BASE_URL,
    themeUrl: import.meta.env.VITE_THEME_URL,
    fontsUrl: import.meta.env.VITE_FONTS_URL,
    logosUrl: import.meta.env.VITE_LOGOS_URL,
    r2rLogoUrl: import.meta.env.VITE_R2R_LOGO_URL,
})

const Config = {
    mode: import.meta.env.MODE,
    api: {
        baseUrl: runtime.apiBaseUrl,
    } as const,
    themeUrl: runtime.themeUrl,
    fontsUrl: runtime.fontsUrl,
    logosUrl: runtime.logosUrl,
    r2rLogoUrl: runtime.r2rLogoUrl,
} as const

export default Config
