import {useEffect, useState} from 'react'
import {Stack, Typography, TextField, Checkbox, FormControlLabel, Alert} from '@mui/material'
import {Trans, useTranslation} from 'react-i18next'
import {Info} from '@mui/icons-material'
import SelectFileButton from '@components/SelectFileButton'
import {CsvImportWizardConfig, CsvImportConfig} from './types'
import {detectCharset} from './utils'

type Props = {
    config: CsvImportWizardConfig
    importConfig: CsvImportConfig | null
    onConfigChange: (config: CsvImportConfig | null) => void
}

const CsvImportWizardStep1 = ({config, importConfig, onConfigChange}: Props) => {
    const {t} = useTranslation()

    const [file, setFile] = useState<File | null>(importConfig?.file ?? null)
    const [separator, setSeparator] = useState(
        importConfig?.separator ?? config.defaultSeparator ?? ','
    )
    const [charset, setCharset] = useState(
        importConfig?.charset ?? config.defaultCharset ?? 'UTF-8'
    )
    const [hasHeader, setHasHeader] = useState(importConfig?.hasHeader ?? true)
    const [detecting, setDetecting] = useState(false)

    // Auto-detect charset when file is selected
    useEffect(() => {
        if (file && !importConfig) {
            setDetecting(true)
            detectCharset(file)
                .then(detected => {
                    setCharset(detected)
                })
                .catch(() => {
                    // Fallback to default
                    setCharset(config.defaultCharset ?? 'UTF-8')
                })
                .finally(() => {
                    setDetecting(false)
                })
        }
    }, [file])

    // Update parent config whenever values change
    useEffect(() => {
        if (file) {
            onConfigChange({
                file,
                separator,
                charset,
                hasHeader,
            })
        } else {
            onConfigChange(null)
        }
    }, [file, separator, charset, hasHeader, onConfigChange])

    const handleFileSelect = (selectedFile: File) => {
        setFile(selectedFile)
    }

    return (
        <Stack spacing={3}>
            <Alert icon={<Info />} severity="info">
                <Trans i18nKey="csv.wizard.step1.info" />
            </Alert>

            <Stack spacing={2}>
                <Typography variant="subtitle1">
                    <Trans i18nKey="csv.wizard.step1.fileSelection" />
                </Typography>
                {file && <Typography>{file.name}</Typography>}
                <SelectFileButton
                    variant={file ? 'text' : 'outlined'}
                    onSelected={handleFileSelect}
                    accept={config.acceptedFileTypes ?? '.csv'}>
                    {file ? (
                        <Trans i18nKey="csv.wizard.step1.changeFile" />
                    ) : (
                        <Trans i18nKey="csv.wizard.step1.selectFile" />
                    )}
                </SelectFileButton>
            </Stack>

            {file && (
                <>
                    <Stack spacing={2}>
                        <Typography variant="subtitle1">
                            <Trans i18nKey="csv.wizard.step1.parsingOptions" />
                        </Typography>

                        <TextField
                            label={t('csv.wizard.step1.separator')}
                            value={separator}
                            onChange={e => setSeparator(e.target.value)}
                            helperText={t('csv.wizard.step1.separatorHelp')}
                            fullWidth
                            inputProps={{maxLength: 1}}
                        />

                        <TextField
                            label={t('csv.wizard.step1.charset')}
                            value={charset}
                            onChange={e => setCharset(e.target.value)}
                            helperText={
                                detecting
                                    ? t('csv.wizard.step1.detectingCharset')
                                    : t('csv.wizard.step1.charsetHelp')
                            }
                            fullWidth
                            disabled={detecting}
                        />

                        <FormControlLabel
                            control={
                                <Checkbox
                                    checked={hasHeader}
                                    onChange={e => setHasHeader(e.target.checked)}
                                />
                            }
                            label={t('csv.wizard.step1.hasHeader')}
                        />
                    </Stack>
                </>
            )}
        </Stack>
    )
}

export default CsvImportWizardStep1