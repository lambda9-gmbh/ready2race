const Config = {
    api: {
        baseUrl: import.meta.env.VITE_API_BASE_URL,
    } as const,
} as const

export default Config