import {useState, useEffect} from 'react';
import {Box, Button, TextField, Typography, Paper, Alert} from '@mui/material';
import {useThemeConfig} from '../../contexts/theme/ThemeContext';
import {useConfirmation} from '../../contexts/confirmation/ConfirmationContext';
import {useSnackbar} from 'notistack';
import {useTranslation} from 'react-i18next';
import {getThemeConfig, updateThemeConfig, resetThemeConfig} from '../../api';

export function ThemeConfigForm() {
    const {t} = useTranslation();
    const {themeConfig, reloadTheme} = useThemeConfig();
    const {showConfirmation} = useConfirmation();
    const {enqueueSnackbar} = useSnackbar();

    const [primaryColor, setPrimaryColor] = useState('#4d9f85');
    const [textColor, setTextColor] = useState('#1d1d1d');
    const [backgroundColor, setBackgroundColor] = useState('#ffffff');
    const [enableCustomFont, setEnableCustomFont] = useState(false);
    const [fontFile, setFontFile] = useState<File | null>(null);
    const [currentFontFilename, setCurrentFontFilename] = useState<string | null>(null);
    const [loading, setLoading] = useState(false);

    useEffect(() => {
        if (themeConfig) {
            setPrimaryColor(themeConfig.primaryColor);
            setTextColor(themeConfig.textColor);
            setBackgroundColor(themeConfig.backgroundColor);
            setEnableCustomFont(themeConfig.customFont?.enabled || false);
            setCurrentFontFilename(themeConfig.customFont?.filename || null);
        }
    }, [themeConfig]);

    const handleFileChange = (event: React.ChangeEvent<HTMLInputElement>) => {
        const file = event.target.files?.[0];
        if (file) {
            const extension = file.name.split('.').pop()?.toLowerCase();
            if (extension !== 'woff' && extension !== 'woff2') {
                enqueueSnackbar(t('administration.theme.errors.invalidFontFormat'), {variant: 'error'});
                return;
            }
            if (file.size > 5 * 1024 * 1024) {
                enqueueSnackbar(t('administration.theme.errors.fontTooLarge'), {variant: 'error'});
                return;
            }
            setFontFile(file);
        }
    };

    const handleSubmit = async () => {
        const confirmed = await showConfirmation({
            title: t('administration.theme.confirmUpdate.title'),
            message: t('administration.theme.confirmUpdate.message'),
        });

        if (!confirmed) return;

        setLoading(true);
        try {
            const formData = new FormData();

            const requestData = {
                primaryColor,
                textColor,
                backgroundColor,
                enableCustomFont: enableCustomFont && (fontFile !== null || currentFontFilename !== null),
            };

            formData.append('request', JSON.stringify(requestData));

            if (fontFile) {
                formData.append('fontFile', fontFile);
            }

            await updateThemeConfig({body: formData});
            await reloadTheme();
            enqueueSnackbar(t('administration.theme.success.updated'), {variant: 'success'});
            setFontFile(null);
        } catch (error) {
            console.error('Failed to update theme:', error);
            enqueueSnackbar(t('administration.theme.errors.updateFailed'), {variant: 'error'});
        } finally {
            setLoading(false);
        }
    };

    const handleReset = async () => {
        const confirmed = await showConfirmation({
            title: t('administration.theme.confirmReset.title'),
            message: t('administration.theme.confirmReset.message'),
        });

        if (!confirmed) return;

        setLoading(true);
        try {
            await resetThemeConfig();
            await reloadTheme();
            enqueueSnackbar(t('administration.theme.success.reset'), {variant: 'success'});
            setFontFile(null);
        } catch (error) {
            console.error('Failed to reset theme:', error);
            enqueueSnackbar(t('administration.theme.errors.resetFailed'), {variant: 'error'});
        } finally {
            setLoading(false);
        }
    };

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
                    <Typography variant="subtitle1" gutterBottom>
                        {t('administration.theme.primaryColor')}
                    </Typography>
                    <TextField
                        type="color"
                        value={primaryColor}
                        onChange={(e) => setPrimaryColor(e.target.value)}
                        fullWidth
                    />
                </Box>

                <Box>
                    <Typography variant="subtitle1" gutterBottom>
                        {t('administration.theme.textColor')}
                    </Typography>
                    <TextField
                        type="color"
                        value={textColor}
                        onChange={(e) => setTextColor(e.target.value)}
                        fullWidth
                    />
                </Box>

                <Box>
                    <Typography variant="subtitle1" gutterBottom>
                        {t('administration.theme.backgroundColor')}
                    </Typography>
                    <TextField
                        type="color"
                        value={backgroundColor}
                        onChange={(e) => setBackgroundColor(e.target.value)}
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
                            onChange={(e) => setEnableCustomFont(e.target.checked)}
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
                            helperText={fontFile ? fontFile.name : t('administration.theme.selectFontFile')}
                        />
                    )}
                </Box>

                <Box sx={{display: 'flex', gap: 2, mt: 2}}>
                    <Button
                        variant="contained"
                        color="primary"
                        onClick={handleSubmit}
                        disabled={loading}
                    >
                        {t('administration.theme.submit')}
                    </Button>
                    <Button
                        variant="outlined"
                        color="secondary"
                        onClick={handleReset}
                        disabled={loading}
                    >
                        {t('administration.theme.reset')}
                    </Button>
                </Box>
            </Box>
        </Paper>
    );
}
