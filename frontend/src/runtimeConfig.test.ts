import {describe, expect, it} from 'vitest'
import {resolveRuntimeConfig} from './runtimeConfig'

describe('runtime configuration', () => {
    it('keeps absolute runtime URLs when embedded on another domain', () => {
        const config = resolveRuntimeConfig({
            apiBaseUrl: 'https://race.example.org/api',
            fontsUrl: 'https://race.example.org/static/fonts',
        }, {apiBaseUrl: 'https://old.example.org/api', themeUrl: '/legacy-theme.json'})
        expect(config.apiBaseUrl).toBe('https://race.example.org/api')
        expect(config.fontsUrl).toBe('https://race.example.org/static/fonts')
        expect(config.themeUrl).toBe('/legacy-theme.json')
    })

    it('supports existing Vite deployments without a runtime configuration', () => {
        expect(resolveRuntimeConfig(undefined, {apiBaseUrl: 'https://legacy.example.org/api'}).apiBaseUrl)
            .toBe('https://legacy.example.org/api')
    })

    it('defaults to the local gateway without any configuration', () => {
        expect(resolveRuntimeConfig()).toEqual({
            apiBaseUrl: '/api', themeUrl: '/static/theme.json', fontsUrl: '/static/fonts',
            logosUrl: '/static/logos', r2rLogoUrl: '/r2r_logo.png',
        })
    })
})
