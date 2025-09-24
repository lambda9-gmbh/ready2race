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
    selectedFolder: string
    checkedResources: ExportFormCheckType[]
    availableEvents: {
        eventFolderName: string
        checked: boolean
        availableCompetitions: {
            competitionFolderName: string
            checked: boolean
        }[]
    }[]
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

const hasSelectedImportEvents = (events: ImportForm['availableEvents']): boolean =>
    events.some(event => event.checked)

const hasSelectedImportCompetitions = (events: ImportForm['availableEvents']): boolean =>
    events.some(event => event.availableCompetitions?.some(comp => comp.checked) ?? false)

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

const updateResourcesWithDependencies = (
    resources: ExportFormCheckType[],
    requiredDependencies: Set<WebDAVExportType>,
    anyEventSelected: boolean,
    anyCompetitionSelected: boolean,
    mode: 'export' | 'import' = 'export',
): {updatedResources: ExportFormCheckType[]; hasChanges: boolean} => {
    const checkedTypes = getCheckedExportTypes(resources)

    const updatedResources = resources.map(item => {
        const shouldBeChecked = requiredDependencies.has(item.type) ||
            (item.type === 'DB_EVENT' && anyEventSelected) ||
            (item.type === 'DB_COMPETITION' && anyCompetitionSelected)

        if (mode === 'import') {
            // Import mode: uncheck if no longer required and wasn't manually selected
            if (!shouldBeChecked && item.checked && !checkedTypes.includes(item.type)) {
                return {...item, checked: false}
            }
        } else {
            // Export mode: handle special cases for DB_EVENT and DB_COMPETITION
            if (item.type === 'DB_EVENT' && anyEventSelected !== item.checked) {
                return {...item, checked: anyEventSelected}
            }
            if (item.type === 'DB_COMPETITION' && anyCompetitionSelected !== item.checked) {
                return {...item, checked: anyCompetitionSelected}
            }
        }

        // Auto-check required dependencies
        if (shouldBeChecked && !item.checked) {
            return {...item, checked: true}
        }

        return item
    })

    const hasChanges = updatedResources.some((item, i) => item.checked !== resources[i].checked)
    return {updatedResources, hasChanges}
}

// Generic dependency checker creator
const createDependencyChecker = (
    checkedResources: ExportFormCheckType[],
    anyEventSelected: boolean,
    anyCompetitionSelected: boolean,
): ((type: WebDAVExportType) => boolean) => {
    const checkedTypes = getCheckedExportTypes(checkedResources)

    return (type: WebDAVExportType): boolean => {
        return checkedTypes.some(checkedType =>
            webDAVExportTypeDependencies[checkedType]?.includes(type) ?? false
        ) || (anyEventSelected && (
            type === 'DB_EVENT' || (webDAVExportTypeDependencies['DB_EVENT']?.includes(type) ?? false)
        )) || (anyCompetitionSelected && (
            type === 'DB_COMPETITION' || (webDAVExportTypeDependencies['DB_COMPETITION']?.includes(type) ?? false)
        ))
    }
}

// Unified hook for managing dependencies (works for both export and import)
const useDependencies = <T extends ExportForm | ImportForm>(
    formData: T,
    setFormData: React.Dispatch<React.SetStateAction<T>>,
    options?: { activeStep?: number },
) => {
    const isExport = 'events' in formData && 'checkedDatabaseExports' in formData
    const resources = isExport
        ? (formData as ExportForm).checkedDatabaseExports
        : (formData as ImportForm).checkedResources

    useEffect(() => {
        // For export, only run on step 2
        if (isExport && options?.activeStep !== 2) return
        if (!resources?.length) return

        const checkedTypes = getCheckedExportTypes(resources)
        const anyEventSelected = isExport
            ? hasSelectedEvents((formData as ExportForm).events)
            : hasSelectedImportEvents((formData as ImportForm).availableEvents)
        const anyCompetitionSelected = isExport
            ? hasSelectedCompetitions((formData as ExportForm).events)
            : hasSelectedImportCompetitions((formData as ImportForm).availableEvents)

        const requiredDependencies = calculateAllRequiredDependencies(
            checkedTypes,
            anyEventSelected,
            anyCompetitionSelected,
        )

        const {updatedResources, hasChanges} = updateResourcesWithDependencies(
            resources,
            requiredDependencies,
            anyEventSelected,
            anyCompetitionSelected,
            isExport ? 'export' : 'import',
        )

        if (hasChanges) {
            if (isExport) {
                setFormData(prev => ({
                    ...prev,
                    checkedDatabaseExports: updatedResources,
                }) as T)
            } else {
                setFormData(prev => ({
                    ...prev,
                    checkedResources: updatedResources,
                }) as T)
            }
        }
    }, [
        resources,
        isExport ? (formData as ExportForm).events : (formData as ImportForm).availableEvents,
        options?.activeStep,
        setFormData,
    ])

    const isRequiredDependency = useCallback(
        (type: WebDAVExportType): boolean => {
            const anyEventSelected = isExport
                ? hasSelectedEvents((formData as ExportForm).events)
                : hasSelectedImportEvents((formData as ImportForm).availableEvents)
            const anyCompetitionSelected = isExport
                ? hasSelectedCompetitions((formData as ExportForm).events)
                : hasSelectedImportCompetitions((formData as ImportForm).availableEvents)

            return createDependencyChecker(resources ?? [], anyEventSelected, anyCompetitionSelected)(type)
        },
        [resources, isExport ? (formData as ExportForm).events : (formData as ImportForm).availableEvents],
    )

    return {isRequiredDependency}
}

// Export-specific wrapper
export const useExportDependencies = (
    formData: ExportForm,
    setFormData: React.Dispatch<React.SetStateAction<ExportForm>>,
    activeStep: number,
) => useDependencies(formData, setFormData, {activeStep})

// Import-specific wrapper
export const useImportDependencies = (
    formData: ImportForm,
    setFormData: React.Dispatch<React.SetStateAction<ImportForm>>,
) => useDependencies(formData, setFormData)

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
    selectedFolder: '',
    checkedResources: [],
    availableEvents: [],
})

// Helper to check if any import form has selections
export const hasImportSelections = (formData: ImportForm): boolean => {
    const hasDataSelections = formData.checkedResources.some(item => item.checked)
    const hasEventSelections = formData.availableEvents.some(event => event.checked)
    return hasDataSelections || hasEventSelections
}
