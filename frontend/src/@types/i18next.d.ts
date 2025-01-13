import {resources} from '../i18n/config.ts'

declare module 'i18next' {
    interface CustomTypeOptions {
        resources: (typeof resources)['en']
    }
}
