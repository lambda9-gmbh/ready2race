/**
 * Configuration for a field that needs to be mapped from CSV columns
 */
export type CsvFieldMapping = {
    /**
     * Unique key for this field
     */
    key: string
    /**
     * Display label for the user
     */
    label: string
    /**
     * Whether this field must be mapped
     */
    required: boolean
    /**
     * Optional default column name to pre-select if found in CSV headers
     */
    defaultColumnName?: string
}

/**
 * Configuration for value mappings (e.g., gender values M/F/D)
 */
export type CsvValueMapping = {
    /**
     * Unique key for this value mapping
     */
    key: string
    /**
     * Display label for the user
     */
    label: string
    /**
     * Whether this value mapping is required
     */
    required: boolean
    /**
     * Default value
     */
    defaultValue?: string
}

/**
 * Configuration step 1: File and parsing settings
 */
export type CsvImportConfig = {
    file: File
    separator: string
    charset: string
    hasHeader: boolean
}

/**
 * The result of column mappings
 * Maps field keys to CSV column names/indices
 */
export type CsvColumnMappings = Record<string, string | number | undefined>

/**
 * The result of value mappings
 * Maps value mapping keys to their configured values
 */
export type CsvValueMappings = Record<string, string | undefined>

/**
 * Parsed CSV data structure
 */
export type ParsedCsvData = {
    /**
     * Column headers (or generated "Column 1", "Column 2"... if no header)
     */
    headers: string[]
    /**
     * Preview rows (first N rows of data)
     */
    previewRows: string[][]
    /**
     * Detected separator
     */
    detectedSeparator?: string
    /**
     * Total number of rows in the file
     */
    totalRows: number
}

/**
 * Main configuration for the CSV Import Wizard
 */
export type CsvImportWizardConfig = {
    /**
     * Title of the dialog
     */
    title: string
    /**
     * Field mappings configuration
     */
    fieldMappings: CsvFieldMapping[]
    /**
     * Optional value mappings configuration (shown in step 3 if provided)
     */
    valueMappings?: CsvValueMapping[]
    /**
     * Number of preview rows to show (default: 5)
     */
    previewRowCount?: number
    /**
     * Default separator (default: ',')
     */
    defaultSeparator?: string
    /**
     * Default charset (default: 'UTF-8')
     */
    defaultCharset?: string
    /**
     * Accepted file extensions (default: '.csv')
     */
    acceptedFileTypes?: string
}

/**
 * Result returned when wizard is completed
 */
export type CsvImportWizardResult = {
    config: CsvImportConfig
    columnMappings: CsvColumnMappings
    valueMappings: CsvValueMappings
}