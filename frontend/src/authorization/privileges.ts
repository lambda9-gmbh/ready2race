import {Privilege} from '../api'

export const userCreateGlobal: Privilege = {resource: 'USER', action: 'CREATE', scope: 'GLOBAL'}
export const userReadGlobal: Privilege = {resource: 'USER', action: 'READ', scope: 'GLOBAL'}

export const eventCreateGlobal: Privilege = {resource: 'EVENT', action: 'CREATE', scope: 'GLOBAL'}
export const eventReadGlobal: Privilege = {resource: 'EVENT', action: 'READ', scope: 'GLOBAL'}
export const eventUpdateGlobal: Privilege = {resource: 'EVENT', action: 'UPDATE', scope: 'GLOBAL'}
export const eventDeleteGlobal: Privilege = {resource: 'EVENT', action: 'DELETE', scope: 'GLOBAL'}
