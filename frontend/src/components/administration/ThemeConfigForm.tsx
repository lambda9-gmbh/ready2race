import React, {useState, useEffect} from 'react'
import {Box, Button, TextField, Typography, Paper, Alert} from '@mui/material'
import {useThemeConfig} from '@contexts/theme/ThemeContext.ts'
import {useConfirmation} from '@contexts/confirmation/ConfirmationContext.ts'
import {useSnackbar} from 'notistack'
import {useTranslation} from 'react-i18next'
import {updateThemeConfig, resetThemeConfig} from '../../api'

export function ThemeConfigForm() {
    const {t} = useTranslation()
    const {themeConfig, reloadTheme} = useThemeConfig()
    const {confirmAction} = useConfirmation()
    const {enqueueSnackbar} = useSnackbar()

    const [primaryMain, setPrimaryMain] = useState('#4d9f85')
    const [primaryLight, setPrimaryLight] = useState('#ecfaf7')
    const [textColorPrimary, setTextColorPrimary] = useState('#1c71d8')
    const [textColorSecondary, setTextColorSecondary] = useState('#666666')
    const [actionColorSuccess, setActionColorSuccess] = useState('#cbe694')
    const [actionColorWarning, setActionColorWarning] = useState('#f5d9b0')
    const [actionColorError, setActionColorError] = useState('#da4d4d')
    const [actionColorInfo, setActionColorInfo] = useState('#6fb0d4')
    const [backgroundColor, setBackgroundColor] = useState('#ffffff')
    const [enableCustomFont, setEnableCustomFont] = useState(false)
    const [fontFile, setFontFile] = useState<File | null>(null)
    const [currentFontFilename, setCurrentFontFilename] = useState<string | null>(null)
    const [loading, setLoading] = useState(false)

    useEffect(() => {
        if (themeConfig) {
            setPrimaryMain(themeConfig.primary.main)
            setPrimaryLight(themeConfig.primary.light)
            setTextColorPrimary(themeConfig.textColor.primary)
            setTextColorSecondary(themeConfig.textColor.secondary)
            setActionColorSuccess(themeConfig.actionColors.success)
            setActionColorWarning(themeConfig.actionColors.warning)
            setActionColorError(themeConfig.actionColors.error)
            setActionColorInfo(themeConfig.actionColors.info)
            setBackgroundColor(themeConfig.backgroundColor)
            setEnableCustomFont(themeConfig.customFont?.enabled || false)
            setCurrentFontFilename(themeConfig.customFont?.filename || null)
        }
    }, [themeConfig])

    const handleFileChange = (event: React.ChangeEvent<HTMLInputElement>) => {
        const file = event.target.files?.[0]
        if (file) {
            const extension = file.name.split('.').pop()?.toLowerCase()
            if (extension !== 'woff' && extension !== 'woff2') {
                enqueueSnackbar(t('administration.theme.errors.invalidFontFormat'), {
                    variant: 'error',
                })
                return
            }
            if (file.size > 5 * 1024 * 1024) {
                enqueueSnackbar(t('administration.theme.errors.fontTooLarge'), {variant: 'error'})
                return
            }
            setFontFile(file)
        }
    }

    const handleSubmit = () => {
        setLoading(true)
        confirmAction(
            async () => {
                try {
                    const {data, error, response} = await updateThemeConfig({
                        body: {
                            request: {
                                primary: {
                                    main: primaryMain,
                                    light: primaryLight,
                                },
                                textColor: {
                                    primary: textColorPrimary,
                                    secondary: textColorSecondary,
                                },
                                actionColors: {
                                    success: actionColorSuccess,
                                    warning: actionColorWarning,
                                    error: actionColorError,
                                    info: actionColorInfo,
                                },
                                backgroundColor,
                                enableCustomFont:
                                    enableCustomFont &&
                                    (fontFile !== null || currentFontFilename !== null),
                            },
                            fontFile: fontFile || undefined,
                        },
                    })
                    setLoading(false)

                    if (response.ok && data !== undefined) {
                        await reloadTheme()
                        enqueueSnackbar(t('administration.theme.success.updated'), {
                            variant: 'success',
                        })
                        setFontFile(null)
                    } else if (error) {
                        enqueueSnackbar(t('administration.theme.errors.updateFailed'), {
                            variant: 'error',
                        })
                    }
                } catch (error) {
                    setLoading(false)
                    console.error('Failed to update theme:', error)
                    enqueueSnackbar(t('administration.theme.errors.updateFailed'), {
                        variant: 'error',
                    })
                }
            },
            {
                title: t('administration.theme.confirmUpdate.title'),
                content: t('administration.theme.confirmUpdate.message'),
                okText: t('administration.theme.submit'),
                cancelText: t('common.cancel'),
                cancelAction: () => setLoading(false),
            },
        )
    }

    const handleReset = () => {
        setLoading(true)
        confirmAction(
            async () => {
                try {
                    const {data, error, response} = await resetThemeConfig()
                    setLoading(false)

                    if (response.ok && data !== undefined) {
                        await reloadTheme()
                        enqueueSnackbar(t('administration.theme.success.reset'), {
                            variant: 'success',
                        })
                        setFontFile(null)
                    } else if (error) {
                        enqueueSnackbar(t('administration.theme.errors.resetFailed'), {
                            variant: 'error',
                        })
                    }
                } catch (error) {
                    setLoading(false)
                    console.error('Failed to reset theme:', error)
                    enqueueSnackbar(t('administration.theme.errors.resetFailed'), {
                        variant: 'error',
                    })
                }
            },
            {
                title: t('administration.theme.confirmReset.title'),
                content: t('administration.theme.confirmReset.message'),
                okText: t('administration.theme.reset'),
                cancelText: t('common.cancel'),
                cancelAction: () => setLoading(false),
            },
        )
    }

    return (
        <Paper sx={{p: 3}}>
            <Typography variant="h2" gutterBottom>
                {t('administration.theme.title')}
            </Typography>
            <Alert severity="info" sx={{mb: 3}}>
                {t('administration.theme.info')}
            </Alert>

            <Box sx={{display: 'flex', flexDirection: 'column', gap: 3}}>
                <Box>
                    <Typography variant="h6" gutterBottom>
                        Primary Colors
                    </Typography>
                    <Box sx={{display: 'flex', gap: 2}}>
                        <Box sx={{flex: 1}}>
                            <Typography variant="subtitle1" gutterBottom>
                                Main
                            </Typography>
                            <TextField
                                type="color"
                                value={primaryMain}
                                onChange={e => setPrimaryMain(e.target.value)}
                                fullWidth
                            />
                        </Box>
                        <Box sx={{flex: 1}}>
                            <Typography variant="subtitle1" gutterBottom>
                                Light
                            </Typography>
                            <TextField
                                type="color"
                                value={primaryLight}
                                onChange={e => setPrimaryLight(e.target.value)}
                                fullWidth
                            />
                        </Box>
                    </Box>
                </Box>

                <Box>
                    <Typography variant="h6" gutterBottom>
                        Text Colors
                    </Typography>
                    <Box sx={{display: 'flex', gap: 2}}>
                        <Box sx={{flex: 1}}>
                            <Typography variant="subtitle1" gutterBottom>
                                Primary
                            </Typography>
                            <TextField
                                type="color"
                                value={textColorPrimary}
                                onChange={e => setTextColorPrimary(e.target.value)}
                                fullWidth
                            />
                        </Box>
                        <Box sx={{flex: 1}}>
                            <Typography variant="subtitle1" gutterBottom>
                                Secondary
                            </Typography>
                            <TextField
                                type="color"
                                value={textColorSecondary}
                                onChange={e => setTextColorSecondary(e.target.value)}
                                fullWidth
                            />
                        </Box>
                    </Box>
                </Box>

                <Box>
                    <Typography variant="h6" gutterBottom>
                        Action Colors
                    </Typography>
                    <Box sx={{display: 'flex', gap: 2, flexWrap: 'wrap'}}>
                        <Box sx={{flex: 1, minWidth: '200px'}}>
                            <Typography variant="subtitle1" gutterBottom>
                                Success
                            </Typography>
                            <TextField
                                type="color"
                                value={actionColorSuccess}
                                onChange={e => setActionColorSuccess(e.target.value)}
                                fullWidth
                            />
                        </Box>
                        <Box sx={{flex: 1, minWidth: '200px'}}>
                            <Typography variant="subtitle1" gutterBottom>
                                Warning
                            </Typography>
                            <TextField
                                type="color"
                                value={actionColorWarning}
                                onChange={e => setActionColorWarning(e.target.value)}
                                fullWidth
                            />
                        </Box>
                        <Box sx={{flex: 1, minWidth: '200px'}}>
                            <Typography variant="subtitle1" gutterBottom>
                                Error
                            </Typography>
                            <TextField
                                type="color"
                                value={actionColorError}
                                onChange={e => setActionColorError(e.target.value)}
                                fullWidth
                            />
                        </Box>
                        <Box sx={{flex: 1, minWidth: '200px'}}>
                            <Typography variant="subtitle1" gutterBottom>
                                Info
                            </Typography>
                            <TextField
                                type="color"
                                value={actionColorInfo}
                                onChange={e => setActionColorInfo(e.target.value)}
                                fullWidth
                            />
                        </Box>
                    </Box>
                </Box>

                <Box>
                    <Typography variant="h6" gutterBottom>
                        Background Color
                    </Typography>
                    <TextField
                        type="color"
                        value={backgroundColor}
                        onChange={e => setBackgroundColor(e.target.value)}
                        fullWidth
                    />
                </Box>

                <Box>
                    <Typography variant="subtitle1" gutterBottom>
                        {t('administration.theme.customFont')}
                    </Typography>
                    <Typography variant="body2" color="text.secondary" gutterBottom>
                        {t('administration.theme.fontHint')}
                    </Typography>
                    {currentFontFilename && (
                        <Typography variant="body2" sx={{mb: 1}}>
                            {t('administration.theme.currentFont')}: {currentFontFilename}
                        </Typography>
                    )}
                    <Box sx={{display: 'flex', alignItems: 'center', gap: 2}}>
                        <input
                            type="checkbox"
                            checked={enableCustomFont}
                            onChange={e => setEnableCustomFont(e.target.checked)}
                        />
                        <Typography>{t('administration.theme.enableCustomFont')}</Typography>
                    </Box>
                    {enableCustomFont && (
                        <TextField
                            type="file"
                            onChange={handleFileChange}
                            fullWidth
                            sx={{mt: 2}}
                            inputProps={{accept: '.woff,.woff2'}}
                            helperText={
                                fontFile ? fontFile.name : t('administration.theme.selectFontFile')
                            }
                        />
                    )}
                </Box>

                <Box sx={{display: 'flex', gap: 2, mt: 2}}>
                    <Button
                        variant="contained"
                        color="primary"
                        onClick={handleSubmit}
                        disabled={loading}>
                        {t('administration.theme.submit')}
                    </Button>
                    <Button
                        variant="outlined"
                        color="secondary"
                        onClick={handleReset}
                        disabled={loading}>
                        {t('administration.theme.reset')}
                    </Button>
                </Box>
            </Box>
        </Paper>
    )
}
