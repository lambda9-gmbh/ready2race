import {Privilege} from '@api/types.gen.ts'

export const createUserGlobal: Privilege = {action: 'CREATE', resource: 'USER', scope: 'GLOBAL'}
export const readUserGlobal: Privilege = {action: 'READ', resource: 'USER', scope: 'GLOBAL'}
export const readUserOwn: Privilege = {action: 'READ', resource: 'USER', scope: 'OWN'}
export const updateUserGlobal: Privilege = {action: 'UPDATE', resource: 'USER', scope: 'GLOBAL'}
export const updateUserOwn: Privilege = {action: 'UPDATE', resource: 'USER', scope: 'OWN'}

export const createEventGlobal: Privilege = {action: 'CREATE', resource: 'EVENT', scope: 'GLOBAL'}
export const readEventGlobal: Privilege = {action: 'READ', resource: 'EVENT', scope: 'GLOBAL'}
export const updateEventGlobal: Privilege = {action: 'UPDATE', resource: 'EVENT', scope: 'GLOBAL'}
export const deleteEventGlobal: Privilege = {action: 'DELETE', resource: 'EVENT', scope: 'GLOBAL'}

export const createClubGlobal: Privilege = {action: 'CREATE', resource: 'CLUB', scope: 'GLOBAL'}
export const readClubGlobal: Privilege = {action: 'READ', resource: 'CLUB', scope: 'GLOBAL'}
export const readClubOwn: Privilege = {action: 'READ', resource: 'CLUB', scope: 'OWN'}
export const updateClubGlobal: Privilege = {action: 'UPDATE', resource: 'CLUB', scope: 'GLOBAL'}
export const updateClubOwn: Privilege = {action: 'UPDATE', resource: 'CLUB', scope: 'OWN'}
export const deleteClubGlobal: Privilege = {action: 'DELETE', resource: 'CLUB', scope: 'GLOBAL'}

export const createParticipantGlobal: Privilege = {action: 'CREATE', resource: 'PARTICIPANT', scope: 'GLOBAL'}
export const readParticipantGlobal: Privilege = {action: 'READ', resource: 'PARTICIPANT', scope: 'GLOBAL'}
export const readParticipantOwn: Privilege = {action: 'READ', resource: 'PARTICIPANT', scope: 'OWN'}
export const updateParticipantGlobal: Privilege = {action: 'UPDATE', resource: 'PARTICIPANT', scope: 'GLOBAL'}
export const updateParticipantOwn: Privilege = {action: 'UPDATE', resource: 'PARTICIPANT', scope: 'OWN'}
export const deleteParticipantGlobal: Privilege = {action: 'DELETE', resource: 'PARTICIPANT', scope: 'GLOBAL'}
export const deleteParticipantOwn: Privilege = {action: 'DELETE', resource: 'PARTICIPANT', scope: 'OWN'}