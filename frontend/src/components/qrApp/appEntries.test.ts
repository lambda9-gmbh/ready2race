import {describe, expect, it} from 'vitest'
import {appEntries} from './common.ts'
import {User} from '@contexts/user/UserContext.ts'
import {Privilege} from '@api/types.gen.ts'

const userWith = (privileges: Privilege[]): User =>
    ({
        loggedIn: true,
        checkPrivilege: (p: Privilege) =>
            privileges.some(
                q => q.action === p.action && q.resource === p.resource && q.scope === p.scope,
            ),
    }) as unknown as User

const qrManagement: Privilege = {action: 'UPDATE', resource: 'APP_QR_MANAGEMENT', scope: 'GLOBAL'}
const liveDashboard: Privilege = {action: 'READ', resource: 'LIVE_DASHBOARD', scope: 'GLOBAL'}

describe('appEntries', () => {
    it('gibt ohne Rechte nichts zurück', () => {
        expect(appEntries(userWith([]))).toEqual([])
    })

    it('gibt Scanner-Funktionen mit dem Scanner als Ziel zurück', () => {
        const entries = appEntries(userWith([qrManagement]))
        expect(entries).toHaveLength(1)
        expect(entries[0].appFunction).toBe('APP_QR_MANAGEMENT')
        expect(entries[0].target).toBe('APP_Scanner')
    })

    it('gibt das Dashboard mit eigenem Ziel zurück', () => {
        const entries = appEntries(userWith([liveDashboard]))
        expect(entries).toHaveLength(1)
        expect(entries[0].key).toBe('LIVE_DASHBOARD')
        expect(entries[0].target).toBe('APP_Dashboard')
        expect(entries[0].appFunction).toBeNull()
    })

    it('gibt bei beiden Rechten beide Einträge zurück', () => {
        expect(appEntries(userWith([qrManagement, liveDashboard]))).toHaveLength(2)
    })
})
