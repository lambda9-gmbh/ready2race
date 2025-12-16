import React, {useEffect, useState} from 'react'
import {Alert, Box, Button, Paper, TextField, Typography} from '@mui/material'
import {useThemeConfig} from '@contexts/theme/ThemeContext.ts'
import {useConfirmation} from '@contexts/confirmation/ConfirmationContext.ts'
import {useTranslation} from 'react-i18next'
import {resetThemeConfig, updateThemeConfig} from '../../api'
import {useFeedback} from '@utils/hooks.ts'

export function ThemeConfigForm() {
    const {t} = useTranslation()
    const {themeConfig, reloadTheme} = useThemeConfig()
    const {confirmAction} = useConfirmation()
    const feedback = useFeedback()

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
    const [enableCustomLogo, setEnableCustomLogo] = useState(false)
    const [logoFile, setLogoFile] = useState<File | null>(null)
    const [currentLogoFilename, setCurrentLogoFilename] = useState<string | null>(null)
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
            setEnableCustomLogo(themeConfig.customLogo?.enabled || false)
            setCurrentLogoFilename(themeConfig.customLogo?.filename || null)
        }
    }, [themeConfig])

    const handleFontFileChange = (event: React.ChangeEvent<HTMLInputElement>) => {
        const file = event.target.files?.[0]
        if (file) {
            const extension = file.name.split('.').pop()?.toLowerCase()
            if (extension !== 'woff' && extension !== 'woff2') {
                feedback.error(t('administration.theme.errors.invalidFontFormat'))
                return
            }
            if (file.size > 5 * 1024 * 1024) {
                feedback.error(t('administration.theme.errors.fontTooLarge'))
                return
            }
            setFontFile(file)
        }
    }

    const handleLogoFileChange = (event: React.ChangeEvent<HTMLInputElement>) => {
        const file = event.target.files?.[0]
        if (file) {
            const extension = file.name.split('.').pop()?.toLowerCase()
            if (!['png', 'jpg', 'jpeg', 'svg', 'webp'].includes(extension || '')) {
                feedback.error(t('administration.theme.errors.invalidLogoFormat'))
                return
            }
            if (file.size > 2 * 1024 * 1024) {
                feedback.error(t('administration.theme.errors.logoTooLarge'))
                return
            }
            setLogoFile(file)
        }
    }

    const handleSubmit = () => {
        confirmAction(
            async () => {
                setLoading(true)
                const {data, error} = await updateThemeConfig({
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
                            enableCustomLogo:
                                enableCustomLogo &&
                                (logoFile !== null || currentLogoFilename !== null),
                        },
                        fontFile: fontFile || undefined,
                        logoFile: logoFile || undefined,
                    },
                })
                setLoading(false)

                if (data) {
                    await reloadTheme()
                    feedback.success(t('administration.theme.success.updated'))
                    setFontFile(null)
                    setLogoFile(null)
                } else if (error) {
                    feedback.error(t('administration.theme.errors.updateFailed'))
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
        confirmAction(
            async () => {
                setLoading(true)
                const {data, error} = await resetThemeConfig()
                setLoading(false)

                if (data) {
                    await reloadTheme()
                    feedback.success(t('administration.theme.success.reset'))
                    setFontFile(null)
                    setLogoFile(null)
                } else if (error) {
                    feedback.error(t('administration.theme.errors.resetFailed'))
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
                        {t('administration.theme.colors.primaryColors')}
                    </Typography>
                    <Box sx={{display: 'flex', gap: 2}}>
                        <Box sx={{flex: 1}}>
                            <Typography variant="subtitle1" gutterBottom>
                                {t('administration.theme.colors.main')}
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
                                {t('administration.theme.colors.light')}
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
                        {t('administration.theme.colors.textColors')}
                    </Typography>
                    <Box sx={{display: 'flex', gap: 2}}>
                        <Box sx={{flex: 1}}>
                            <Typography variant="subtitle1" gutterBottom>
                                {t('administration.theme.colors.primary')}
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
                                {t('administration.theme.colors.secondary')}
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
                        {t('administration.theme.colors.actionColors')}
                    </Typography>
                    <Box sx={{display: 'flex', gap: 2, flexWrap: 'wrap'}}>
                        <Box sx={{flex: 1, minWidth: '200px'}}>
                            <Typography variant="subtitle1" gutterBottom>
                                {t('administration.theme.colors.secondary')}
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
                                {t('administration.theme.colors.warning')}
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
                                {t('administration.theme.colors.error')}
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
                                {t('administration.theme.colors.info')}
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
                        {t('administration.theme.colors.backgroundColor')}
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
                            onChange={handleFontFileChange}
                            fullWidth
                            sx={{mt: 2}}
                            inputProps={{accept: '.woff,.woff2'}}
                            helperText={
                                fontFile ? fontFile.name : t('administration.theme.selectFontFile')
                            }
                        />
                    )}
                </Box>

                <Box>
                    <Typography variant="subtitle1" gutterBottom>
                        {t('administration.theme.customLogo')}
                    </Typography>
                    <Typography variant="body2" color="text.secondary" gutterBottom>
                        {t('administration.theme.logoHint')}
                    </Typography>
                    {currentLogoFilename && (
                        <Typography variant="body2" sx={{mb: 1}}>
                            {t('administration.theme.currentLogo')}: {currentLogoFilename}
                        </Typography>
                    )}
                    <Box sx={{display: 'flex', alignItems: 'center', gap: 2}}>
                        <input
                            type="checkbox"
                            checked={enableCustomLogo}
                            onChange={e => setEnableCustomLogo(e.target.checked)}
                        />
                        <Typography>{t('administration.theme.enableCustomLogo')}</Typography>
                    </Box>
                    {enableCustomLogo && (
                        <TextField
                            type="file"
                            onChange={handleLogoFileChange}
                            fullWidth
                            sx={{mt: 2}}
                            inputProps={{accept: '.png,.jpg,.jpeg,.svg,.webp'}}
                            helperText={
                                logoFile ? logoFile.name : t('administration.theme.selectLogoFile')
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
                    <Button variant="outlined" onClick={handleReset} disabled={loading}>
                        {t('administration.theme.reset')}
                    </Button>
                </Box>
            </Box>
        </Paper>
    )
}
