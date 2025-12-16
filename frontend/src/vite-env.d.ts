/// <reference types="vite/client" />

interface ImportMetaEnv {
    readonly VITE_API_BASE_URL: string
    readonly VITE_THEME_URL: string
    readonly VITE_FONTS_URL: string
}

interface ImportMeta {
    readonly env: ImportMetaEnv
}
