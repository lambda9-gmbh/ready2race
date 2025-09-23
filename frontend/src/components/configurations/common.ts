import React, {useEffect, useCallback} from 'react'
import {EventForExportDto, WebDAVExportType} from '@api/types.gen.ts'

export type ExportForm = {
    name: string
    events: {
        eventId: string
        docExportChecked: boolean
        selectedDocExports: ExportFormCheckType[]
        exportData: boolean
        selectedCompetitionIds: ExportFormCompetitionCheckType[]
    }[]
    checkedDatabaseExports: ExportFormCheckType[]
}

export type ExportFormCompetitionCheckType = {
    competitionId: string
    checked: boolean
}

export type ExportFormCheckType = {
    type: WebDAVExportType
    checked: boolean
}

export type ImportForm = {
    folderName: string
    selectedData: ExportFormCheckType[]
}

export const EVENT_TYPE_OPTIONS: WebDAVExportType[] = [
    'REGISTRATION_RESULTS',
    'INVOICES',
    'DOCUMENTS',
    'RESULTS',
    'START_LISTS',
]

export const DATA_TYPE_OPTIONS: WebDAVExportType[] = [
    'DB_USERS',
    'DB_PARTICIPANTS',
    'DB_BANK_ACCOUNTS',
    'DB_CONTACT_INFORMATION',
    'DB_EMAIL_INDIVIDUAL_TEMPLATES',
    'DB_EVENT_DOCUMENT_TYPES',
    'DB_MATCH_RESULT_IMPORT_CONFIGS',
    'DB_STARTLIST_EXPORT_CONFIGS',
    'DB_WORK_TYPES',
    'DB_PARTICIPANT_REQUIREMENTS',
    'DB_RATING_CATEGORIES',
    'DB_COMPETITION_CATEGORIES',
    'DB_FEES',
    'DB_NAMED_PARTICIPANTS',
    'DB_COMPETITION_SETUP_TEMPLATES',
    'DB_COMPETITION_TEMPLATES',
]

// Mirror the backend dependency structure
export const webDAVExportTypeDependencies: Partial<Record<WebDAVExportType, WebDAVExportType[]>> = {
    DB_PARTICIPANTS: ['DB_USERS'],
    DB_BANK_ACCOUNTS: ['DB_USERS'],
    DB_CONTACT_INFORMATION: ['DB_USERS'],
    DB_EMAIL_INDIVIDUAL_TEMPLATES: ['DB_USERS'],
    DB_EVENT_DOCUMENT_TYPES: ['DB_USERS'],
    DB_MATCH_RESULT_IMPORT_CONFIGS: ['DB_USERS'],
    DB_STARTLIST_EXPORT_CONFIGS: ['DB_USERS'],
    DB_WORK_TYPES: ['DB_USERS'],
    DB_PARTICIPANT_REQUIREMENTS: ['DB_USERS'],
    DB_RATING_CATEGORIES: ['DB_USERS'],
    DB_COMPETITION_CATEGORIES: ['DB_USERS'],
    DB_FEES: ['DB_USERS'],
    DB_NAMED_PARTICIPANTS: ['DB_USERS'],
    DB_COMPETITION_SETUP_TEMPLATES: ['DB_USERS'],
    DB_COMPETITION_TEMPLATES: [
        'DB_USERS',
        'DB_COMPETITION_SETUP_TEMPLATES',
        'DB_COMPETITION_CATEGORIES',
        'DB_FEES',
        'DB_NAMED_PARTICIPANTS',
    ],
    DB_EVENT: [
        'DB_USERS',
        'DB_CONTACT_INFORMATION',
        'DB_BANK_ACCOUNTS',
        'DB_PARTICIPANT_REQUIREMENTS',
    ],
    DB_COMPETITION: [
        'DB_USERS',
        'DB_EVENT',
        'DB_COMPETITION_CATEGORIES',
        'DB_FEES',
        'DB_NAMED_PARTICIPANTS',
    ],
} as const

// Helper function to get all dependencies for selected types
export const getDependenciesForTypes = (types: WebDAVExportType[]): Set<WebDAVExportType> => {
    const dependencies = new Set<WebDAVExportType>()
    types.forEach(type => {
        const deps = webDAVExportTypeDependencies[type]
        if (deps) {
            deps.forEach(dep => dependencies.add(dep))
        }
    })
    return dependencies
}

// Utility functions for dependency management
const getCheckedExportTypes = (checkedDatabaseExports: ExportFormCheckType[]): WebDAVExportType[] =>
    checkedDatabaseExports?.filter(item => item?.checked).map(item => item.type) ?? []

const hasSelectedEvents = (events: ExportForm['events']): boolean =>
    events.some(event => event.exportData)

const hasSelectedCompetitions = (events: ExportForm['events']): boolean =>
    events.some(event => event.selectedCompetitionIds.some(comp => comp.checked))

const calculateAllRequiredDependencies = (
    checkedTypes: WebDAVExportType[],
    anyEventSelected: boolean,
    anyCompetitionSelected: boolean,
): Set<WebDAVExportType> => {
    const requiredDependencies = getDependenciesForTypes(checkedTypes)

    if (anyEventSelected) {
        const eventDeps = webDAVExportTypeDependencies['DB_EVENT']
        eventDeps?.forEach(dep => requiredDependencies.add(dep))
    }

    if (anyCompetitionSelected) {
        const competitionDeps = webDAVExportTypeDependencies['DB_COMPETITION']
        competitionDeps?.forEach(dep => requiredDependencies.add(dep))
    }

    return requiredDependencies
}

const updateDatabaseExportsWithDependencies = (
    databaseExports: ExportFormCheckType[],
    requiredDependencies: Set<WebDAVExportType>,
    anyEventSelected: boolean,
    anyCompetitionSelected: boolean,
): {updatedExports: ExportFormCheckType[]; hasChanges: boolean} => {
    let hasChanges = false

    const updatedExports = databaseExports.map(item => {
        // Handle DB_EVENT auto-selection based on event selection
        if (item.type === 'DB_EVENT') {
            if (anyEventSelected && !item.checked) {
                hasChanges = true
                return {...item, checked: true}
            }
            if (!anyEventSelected && item.checked) {
                hasChanges = true
                return {...item, checked: false}
            }
        }

        // Handle DB_COMPETITION auto-selection based on competition selection
        if (item.type === 'DB_COMPETITION') {
            if (anyCompetitionSelected && !item.checked) {
                hasChanges = true
                return {...item, checked: true}
            }
            if (!anyCompetitionSelected && item.checked) {
                hasChanges = true
                return {...item, checked: false}
            }
        }

        // Auto-check required dependencies
        if (requiredDependencies.has(item.type) && !item.checked) {
            hasChanges = true
            return {...item, checked: true}
        }

        return item
    })

    return {updatedExports, hasChanges}
}

// Dependency checking logic
const createDependencyChecker = (
    checkedDatabaseExports: ExportFormCheckType[],
    events: ExportForm['events'],
) => {
    const checkedTypes = getCheckedExportTypes(checkedDatabaseExports)
    const anyEventSelected = hasSelectedEvents(events)
    const anyCompetitionSelected = hasSelectedCompetitions(events)

    return (type: WebDAVExportType): boolean => {
        // Check if type is a dependency of any checked type
        const isDependency = checkedTypes.some(checkedType => {
            const deps = webDAVExportTypeDependencies[checkedType]
            return deps?.includes(type) ?? false
        })

        // Handle DB_EVENT special case
        if (anyEventSelected) {
            const eventDeps = webDAVExportTypeDependencies['DB_EVENT']
            if (eventDeps?.includes(type) || type === 'DB_EVENT') {
                return true
            }
        }

        // Handle DB_COMPETITION special case
        if (anyCompetitionSelected) {
            const competitionDeps = webDAVExportTypeDependencies['DB_COMPETITION']
            if (competitionDeps?.includes(type) || type === 'DB_COMPETITION') {
                return true
            }
        }

        return isDependency
    }
}

// Custom hook for managing export dependencies
export const useExportDependencies = (
    formData: ExportForm,
    setFormData: React.Dispatch<React.SetStateAction<ExportForm>>,
    activeStep: number,
) => {
    useEffect(() => {
        // Only run dependency checking on the data export step
        if (activeStep !== 2 || !formData.checkedDatabaseExports) return

        const checkedTypes = getCheckedExportTypes(formData.checkedDatabaseExports)
        const anyEventSelected = hasSelectedEvents(formData.events)
        const anyCompetitionSelected = hasSelectedCompetitions(formData.events)
        const requiredDependencies = calculateAllRequiredDependencies(
            checkedTypes,
            anyEventSelected,
            anyCompetitionSelected,
        )

        const {updatedExports, hasChanges} = updateDatabaseExportsWithDependencies(
            formData.checkedDatabaseExports,
            requiredDependencies,
            anyEventSelected,
            anyCompetitionSelected,
        )

        // Only update if there were changes
        if (hasChanges) {
            setFormData(prev => ({
                ...prev,
                checkedDatabaseExports: updatedExports,
            }))
        }
    }, [formData.checkedDatabaseExports, formData.events, activeStep, setFormData])

    // Memoized function to check if a type is a required dependency
    const isRequiredDependency = useCallback(
        createDependencyChecker(formData.checkedDatabaseExports ?? [], formData.events),
        [formData.checkedDatabaseExports, formData.events],
    )

    return {isRequiredDependency}
}

// Helper to create initial form data
export const createInitialExportForm = (events: EventForExportDto[]): ExportForm => ({
    name: '',
    events: events.map(event => ({
        eventId: event.id,
        docExportChecked: false,
        selectedDocExports: EVENT_TYPE_OPTIONS.map(type => ({
            type: type,
            checked: false,
        })),
        exportData: false,
        selectedCompetitionIds: event.competitions.map(c => ({
            competitionId: c.id,
            checked: false,
        })),
    })),
    checkedDatabaseExports: DATA_TYPE_OPTIONS.map(type => ({
        type: type,
        checked: false,
    })),
})

export const createInitialImportForm = (): ImportForm => ({
    folderName: '',
    selectedData: DATA_TYPE_OPTIONS.map(type => ({
        type: type,
        checked: false,
    })),
})
