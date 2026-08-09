import {AppFunction, AppView} from '@contexts/app/AppSessionContext.tsx'
import {PrivilegeDto} from '@api/types.gen.ts'
import {User} from '@contexts/user/UserContext.ts'
import {
    readLiveDashboardGlobal,
    updateAppCatererGlobal,
    updateAppCompetitionCheckGlobal,
    updateAppEventRequirementGlobal,
    updateAppQrManagementGlobal,
} from '@authorization/privileges.ts'

export const getUserAppRights = (user: User): AppFunction[] => {
    const rights: AppFunction[] = []
    if (user.checkPrivilege(updateAppQrManagementGlobal)) rights.push('APP_QR_MANAGEMENT')
    if (user.checkPrivilege(updateAppCompetitionCheckGlobal)) rights.push('APP_COMPETITION_CHECK')
    if (user.checkPrivilege(updateAppEventRequirementGlobal)) rights.push('APP_EVENT_REQUIREMENT')
    if (user.checkPrivilege(updateAppCatererGlobal)) rights.push('APP_CATERER')
    return rights
}

export const getAppRights = (privileges: PrivilegeDto[]): AppFunction[] => {
    return privileges
        .filter(
            p =>
                p.action === 'UPDATE' &&
                (p.resource === 'APP_QR_MANAGEMENT' ||
                    p.resource === 'APP_COMPETITION_CHECK' ||
                    p.resource === 'APP_EVENT_REQUIREMENT' ||
                    p.resource === 'APP_CATERER') &&
                p.scope == 'GLOBAL',
        )
        .map(p =>
            p.resource === 'APP_QR_MANAGEMENT'
                ? 'APP_QR_MANAGEMENT'
                : p.resource === 'APP_COMPETITION_CHECK'
                  ? 'APP_COMPETITION_CHECK'
                  : p.resource === 'APP_EVENT_REQUIREMENT'
                    ? 'APP_EVENT_REQUIREMENT'
                    : 'APP_CATERER',
        )
}

/**
 * Ein Eintrag der Funktionsauswahl. Scanner-Funktionen tragen ihre `AppFunction`; das
 * Dashboard ist keine Scanner-Funktion und trägt deshalb `null` - der Scanner darf nichts
 * bekommen, womit er nichts anfangen kann.
 */
/**
 * Die Beschriftungen sind bewusst als Literale getippt und nicht als `string`: `t()` nimmt nur
 * bekannte Schlüssel entgegen, ein loser `string` scheitert an dessen Typprüfung.
 */
export type AppEntryLabelKey =
    | 'app.functionSelect.functions.qrManagement'
    | 'app.functionSelect.functions.competitionCheck'
    | 'app.functionSelect.functions.eventRequirement'
    | 'app.functionSelect.functions.caterer'
    | 'app.functionSelect.functions.liveDashboard'

export type AppEntry = {
    key: string
    labelKey: AppEntryLabelKey
    target: AppView
    appFunction: AppFunction
}

const scannerLabels: Record<Exclude<AppFunction, null>, AppEntryLabelKey> = {
    APP_QR_MANAGEMENT: 'app.functionSelect.functions.qrManagement',
    APP_COMPETITION_CHECK: 'app.functionSelect.functions.competitionCheck',
    APP_EVENT_REQUIREMENT: 'app.functionSelect.functions.eventRequirement',
    APP_CATERER: 'app.functionSelect.functions.caterer',
}

export const appEntries = (user: User): AppEntry[] => {
    const entries: AppEntry[] = getUserAppRights(user)
        .filter((fn): fn is Exclude<AppFunction, null> => fn !== null)
        .map(fn => ({
            key: fn,
            labelKey: scannerLabels[fn],
            target: 'APP_Scanner' as AppView,
            appFunction: fn,
        }))

    if (user.checkPrivilege(readLiveDashboardGlobal)) {
        entries.push({
            key: 'LIVE_DASHBOARD',
            labelKey: 'app.functionSelect.functions.liveDashboard',
            target: 'APP_Dashboard',
            appFunction: null,
        })
    }

    return entries
}
