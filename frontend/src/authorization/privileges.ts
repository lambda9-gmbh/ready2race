import {Privilege} from '@api/types.gen.ts'

export const createUserGlobal: Privilege = {action: 'CREATE', resource: 'USER', scope: 'GLOBAL'}
export const readUserGlobal: Privilege = {action: 'READ', resource: 'USER', scope: 'GLOBAL'}
export const readUserOwn: Privilege = {action: 'READ', resource: 'USER', scope: 'OWN'}
export const updateUserGlobal: Privilege = {action: 'UPDATE', resource: 'USER', scope: 'GLOBAL'}
export const updateUserOwn: Privilege = {action: 'UPDATE', resource: 'USER', scope: 'OWN'}

export const createEventGlobal: Privilege = {action: 'CREATE', resource: 'EVENT', scope: 'GLOBAL'}
export const readEventOwn: Privilege = {action: 'READ', resource: 'EVENT', scope: 'OWN'}
export const readEventGlobal: Privilege = {action: 'READ', resource: 'EVENT', scope: 'GLOBAL'}
export const updateEventGlobal: Privilege = {action: 'UPDATE', resource: 'EVENT', scope: 'GLOBAL'}
export const deleteEventGlobal: Privilege = {action: 'DELETE', resource: 'EVENT', scope: 'GLOBAL'}

export const createClubGlobal: Privilege = {action: 'CREATE', resource: 'CLUB', scope: 'GLOBAL'}
export const createClubOwn: Privilege = {action: 'CREATE', resource: 'CLUB', scope: 'OWN'}
export const readClubGlobal: Privilege = {action: 'READ', resource: 'CLUB', scope: 'GLOBAL'}
export const readClubOwn: Privilege = {action: 'READ', resource: 'CLUB', scope: 'OWN'}
export const updateClubGlobal: Privilege = {action: 'UPDATE', resource: 'CLUB', scope: 'GLOBAL'}
export const updateClubOwn: Privilege = {action: 'UPDATE', resource: 'CLUB', scope: 'OWN'}
export const deleteClubGlobal: Privilege = {action: 'DELETE', resource: 'CLUB', scope: 'GLOBAL'}

export const updateAppQrManagementGlobal: Privilege = {
    action: 'UPDATE',
    resource: 'APP_QR_MANAGEMENT',
    scope: 'GLOBAL',
}
export const updateAppCompetitionCheckGlobal: Privilege = {
    action: 'UPDATE',
    resource: 'APP_COMPETITION_CHECK',
    scope: 'GLOBAL',
}
export const updateAppEventRequirementGlobal: Privilege = {
    action: 'UPDATE',
    resource: 'APP_EVENT_REQUIREMENT',
    scope: 'GLOBAL',
}
export const updateAppCatererGlobal: Privilege = {
    action: 'UPDATE',
    resource: 'APP_CATERER',
    scope: 'GLOBAL',
}

export const createRegistrationGlobal: Privilege = {
    action: 'CREATE',
    resource: 'REGISTRATION',
    scope: 'GLOBAL',
}
export const createRegistrationOwn: Privilege = {
    action: 'CREATE',
    resource: 'REGISTRATION',
    scope: 'OWN',
}
export const readRegistrationGlobal: Privilege = {
    action: 'READ',
    resource: 'REGISTRATION',
    scope: 'GLOBAL',
}
export const readRegistrationOwn: Privilege = {
    action: 'READ',
    resource: 'REGISTRATION',
    scope: 'OWN',
}
export const updateRegistrationGlobal: Privilege = {
    action: 'UPDATE',
    resource: 'REGISTRATION',
    scope: 'GLOBAL',
}
export const updateRegistrationOwn: Privilege = {
    action: 'UPDATE',
    resource: 'REGISTRATION',
    scope: 'OWN',
}
export const deleteRegistrationGlobal: Privilege = {
    action: 'UPDATE',
    resource: 'REGISTRATION',
    scope: 'GLOBAL',
}
export const deleteRegistrationOwn: Privilege = {
    action: 'UPDATE',
    resource: 'REGISTRATION',
    scope: 'OWN',
}
export const createInvoiceGlobal: Privilege = {
    action: 'CREATE',
    resource: 'INVOICE',
    scope: 'GLOBAL',
}
export const readInvoiceGlobal: Privilege = {
    action: 'READ',
    resource: 'INVOICE',
    scope: 'GLOBAL',
}
export const readInvoiceOwn: Privilege = {
    action: 'READ',
    resource: 'INVOICE',
    scope: 'OWN',
}
export const updateInvoiceGlobal: Privilege = {
    action: 'UPDATE',
    resource: 'INVOICE',
    scope: 'GLOBAL',
}

export const createSubstitutionGlobal: Privilege = {
    action: 'CREATE',
    resource: 'SUBSTITUTION',
    scope: 'GLOBAL',
}
export const deleteSubstitutionGlobal: Privilege = {
    action: 'DELETE',
    resource: 'SUBSTITUTION',
    scope: 'GLOBAL',
}
export const updateWebDavGlobal: Privilege = {
    action: 'UPDATE',
    resource: 'WEB_DAV',
    scope: 'GLOBAL',
    scope: 'GLOBAL',
}
export const readWebDavGlobal: Privilege = {
    action: 'READ',
    resource: 'WEB_DAV',
    scope: 'GLOBAL',
}

export const updateResultGlobal: Privilege = {
    action: 'UPDATE',
    resource: 'RESULT',
    scope: 'GLOBAL',
}

export const updateResultOwn: Privilege = {
    action: 'UPDATE',
    resource: 'RESULT',
    scope: 'OWN',
}

export const readResultGlobal: Privilege = {
    action: 'READ',
    resource: 'RESULT',
    scope: 'GLOBAL',
}

export const readResultOwn: Privilege = {
    action: 'READ',
    resource: 'RESULT',
    scope: 'OWN',
}
