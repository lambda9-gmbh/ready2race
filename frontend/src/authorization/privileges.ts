import {Privilege} from '../api'

export const createUserGlobal: Privilege = {action: 'CREATE', resource: 'USER', scope: 'GLOBAL'}
export const readUserGlobal: Privilege = {action: 'READ', resource: 'USER', scope: 'GLOBAL'}
export const readUserOwn: Privilege = {action: 'READ', resource: 'USER', scope: 'OWN'}

export const readRoleGlobal: Privilege = {action: 'READ', resource: 'ROLE', scope: 'GLOBAL'}

export const createEventGlobal: Privilege = {action: 'CREATE', resource: 'EVENT', scope: 'GLOBAL'}
export const readEventGlobal: Privilege = {action: 'READ', resource: 'EVENT', scope: 'GLOBAL'}
export const updateEventGlobal: Privilege = {action: 'UPDATE', resource: 'EVENT', scope: 'GLOBAL'}
export const deleteEventGlobal: Privilege = {action: 'DELETE', resource: 'EVENT', scope: 'GLOBAL'}
