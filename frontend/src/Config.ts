const Config = {
    mode: import.meta.env.MODE,
    api: {
        baseUrl: import.meta.env.VITE_API_BASE_URL,
    } as const,
} as const

export default Config
