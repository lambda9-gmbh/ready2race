export type RuntimeConfig = {
    apiBaseUrl?: string
    themeUrl?: string
    fontsUrl?: string
    logosUrl?: string
    r2rLogoUrl?: string
}

declare global {
    interface Window {
        READY2RACE_CONFIG?: RuntimeConfig
    }
}

// Legacy Vite settings remain available for deployments that build their own frontend.
export const resolveRuntimeConfig = (runtime: RuntimeConfig = {}, build: RuntimeConfig = {}) => ({
    apiBaseUrl: runtime.apiBaseUrl ?? build.apiBaseUrl ?? '/api',
    themeUrl: runtime.themeUrl ?? build.themeUrl ?? '/static/theme.json',
    fontsUrl: runtime.fontsUrl ?? build.fontsUrl ?? '/static/fonts',
    logosUrl: runtime.logosUrl ?? build.logosUrl ?? '/static/logos',
    r2rLogoUrl: runtime.r2rLogoUrl ?? build.r2rLogoUrl ?? '/r2r_logo.png',
})
