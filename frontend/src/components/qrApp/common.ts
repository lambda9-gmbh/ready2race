import {AppFunction} from '@contexts/app/AppSessionContext.tsx'
import {PrivilegeDto} from '@api/types.gen.ts'
import {User} from '@contexts/user/UserContext.ts'
import {
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
