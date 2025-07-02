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

export const readInvoiceGlobal: Privilege = {
    action: 'READ',
    resource: 'INVOICE',
    scope: 'GLOBAL'
}
export const readInvoiceOwn: Privilege = {
    action: 'READ',
    resource: 'INVOICE',
    scope: 'OWN'
}