import {AppFunction} from '@contexts/app/AppSessionContext.tsx'
import {
    updateAppCatererGlobal,
    updateAppCompetitionCheckGlobal,
    updateAppEventRequirementGlobal,
    updateAppQrManagementGlobal,
} from '@authorization/privileges.ts'
import {User} from '@contexts/user/UserContext.ts'

export const getAppRights = (user: User): AppFunction[] => {
    const rights: AppFunction[] = []
    if (user.checkPrivilege(updateAppQrManagementGlobal)) rights.push('APP_QR_MANAGEMENT')
    if (user.checkPrivilege(updateAppCompetitionCheckGlobal)) rights.push('APP_COMPETITION_CHECK')
    if (user.checkPrivilege(updateAppEventRequirementGlobal)) rights.push('APP_EVENT_REQUIREMENT')
    if (user.checkPrivilege(updateAppCatererGlobal)) rights.push('APP_CATERER')
    return rights
}
