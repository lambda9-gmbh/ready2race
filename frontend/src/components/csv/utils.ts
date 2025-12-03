import Papa from 'papaparse'
import {ParsedCsvData} from './types'

/**
 * Common CSV separators to try for auto-detection
 */
const COMMON_SEPARATORS = [',', ';', '\t', '|']

/**
 * Detects the most likely separator used in a CSV file
 */
const detectSeparator = (text: string): string => {
    const firstLine = text.split('\n')[0]
    if (!firstLine) return ','

    // Count occurrences of each separator in the first line
    const counts = COMMON_SEPARATORS.map(sep => ({
        separator: sep,
        count: (firstLine.match(new RegExp(`\\${sep}`, 'g')) || []).length,
    }))

    // Return the separator with the highest count, or default to comma
    const best = counts.reduce((prev, current) => (current.count > prev.count ? current : prev))

    return best.count > 0 ? best.separator : ','
}

/**
 * Attempts to detect the charset of a file
 * Note: This is a simple heuristic and may not be 100% accurate
 */
export const detectCharset = async (file: File): Promise<string> => {
    // Read a small sample of the file
    const slice = file.slice(0, Math.min(1024, file.size))
    const buffer = await slice.arrayBuffer()
    const bytes = new Uint8Array(buffer)

    // Check for BOM markers
    if (bytes.length >= 3 && bytes[0] === 0xef && bytes[1] === 0xbb && bytes[2] === 0xbf) {
        return 'UTF-8'
    }
    if (bytes.length >= 2 && bytes[0] === 0xff && bytes[1] === 0xfe) {
        return 'UTF-16LE'
    }
    if (bytes.length >= 2 && bytes[0] === 0xfe && bytes[1] === 0xff) {
        return 'UTF-16BE'
    }

    // Default to UTF-8 (most common for modern files)
    return 'UTF-8'
}

/**
 * Parses a CSV file and returns structured data with auto-detection
 */
export const parseCsvFile = async (
    file: File,
    options?: {
        separator?: string
        charset?: string
        hasHeader?: boolean
        previewRowCount?: number
    },
): Promise<ParsedCsvData> => {
    return new Promise((resolve, reject) => {
        const previewRowCount = options?.previewRowCount ?? 5

        // Read file as text with specified charset
        const reader = new FileReader()

        reader.onload = async e => {
            const text = e.target?.result as string
            if (!text) {
                reject(new Error('Failed to read file'))
                return
            }

            // Auto-detect separator if not provided
            const separator = options?.separator ?? detectSeparator(text)

            // Parse the CSV
            Papa.parse(text, {
                delimiter: separator,
                skipEmptyLines: true,
                complete: results => {
                    const data = results.data as string[][]

                    if (data.length === 0) {
                        reject(new Error('CSV file is empty'))
                        return
                    }

                    const hasHeader = options?.hasHeader ?? true
                    let headers: string[]
                    let dataRows: string[][]

                    if (hasHeader) {
                        // First row is headers
                        headers = data[0]
                        dataRows = data.slice(1)
                    } else {
                        // Generate column indices as strings (1, 2, 3, ...)
                        const columnCount = data[0].length
                        headers = Array.from({length: columnCount}, (_, i) => `${i + 1}`)
                        dataRows = data
                    }

                    // Get preview rows
                    const previewRows = dataRows.slice(0, previewRowCount)

                    resolve({
                        headers,
                        previewRows,
                        detectedSeparator: separator,
                        totalRows: dataRows.length,
                    })
                },
                error: (error: any) => {
                    reject(new Error(`Failed to parse CSV: ${error.message}`))
                },
            })
        }

        reader.onerror = () => {
            reject(new Error('Failed to read file'))
        }

        // Read with specified charset or default
        const charset = options?.charset ?? 'UTF-8'
        reader.readAsText(file, charset)
    })
}

/**
 * Validates that all required field mappings are filled
 */
export const validateColumnMappings = (
    mappings: Record<string, string | number | undefined>,
    requiredFields: string[],
): string[] => {
    const errors: string[] = []

    for (const field of requiredFields) {
        if (mappings[field] === undefined || mappings[field] === '') {
            errors.push(field)
        }
    }

    return errors
}

/**
 * Validates that all required value mappings are filled
 */
export const validateValueMappings = (
    mappings: Record<string, string | undefined>,
    requiredFields: string[],
): string[] => {
    const errors: string[] = []

    for (const field of requiredFields) {
        if (mappings[field] === undefined || mappings[field] === '') {
            errors.push(field)
        }
    }

    return errors
}
