import {useTranslation} from 'react-i18next'
import {useMemo, useRef, useState} from 'react'
import {SliderElement} from 'react-hook-form-mui'
import {UseFetchReturn, useWindowSize} from '@utils/hooks.ts'
import {ApiError, CaptchaDto} from '@api/types.gen.ts'
import {Box, Stack, Typography} from '@mui/material'
import {touchSupported} from '@utils/helpers.ts'
import logoUrl from '../../../assets/r2r_logo.png'

type Props = {
    captchaProps: UseFetchReturn<CaptchaDto, ApiError>
}

type CaptchaSizes = {
    width: number
    height: number
    logo: number
}

const FormInputCaptcha = (props: Props) => {
    const {t} = useTranslation()

    const windowSize = useWindowSize()
    const imgRef = useRef<HTMLDivElement>(null)
    const [loaded, setLoaded] = useState(false)

    const {data, pending} = props.captchaProps

    const sizes: CaptchaSizes = useMemo(() => {
        return {
            width: imgRef.current?.clientWidth ?? 0,
            height: imgRef.current?.clientHeight ?? 0,
            logo: (imgRef.current?.clientHeight ?? 0) * (data?.handleToHeightRatio ?? 0),
        }
    }, [windowSize, loaded])
    return (
        <Box display={'flex'} justifyContent={'center'}>
            {data ? (
                <Stack>
                    <Typography>
                        {touchSupported()
                            ? t('captcha.instruction.mobile')
                            : t('captcha.instruction.desktop')}
                    </Typography>
                    <Box position={'relative'} boxSizing={'unset'}>
                        <Box
                            position={'absolute'}
                            boxSizing={'unset'}
                            display={'flex'}
                            alignItems={'center'}
                            width={`${sizes.width - sizes.logo}px`}
                            height={`${sizes.height}px`}
                            px={`${sizes.logo / 2}px`}>
                            <SliderElement
                                name={'captcha'}
                                min={data.solutionMin}
                                max={data.solutionMax}
                                track={false}
                                valueLabelDisplay={'off'}
                                sx={{
                                    '& .MuiSlider-thumb': {
                                        background: `url('${logoUrl}')`,
                                        backgroundColor: 'white',
                                        border: '1px solid black',
                                        backgroundSize: sizes.logo - 5,
                                        width: sizes.logo,
                                        height: sizes.logo,
                                        backgroundRepeat: 'no-repeat',
                                        backgroundPosition: 'center',
                                    },
                                    '& .MuiSlider-rail': {
                                        display: 'none',
                                    },
                                }}
                            />
                        </Box>
                        <Box
                            ref={imgRef}
                            width={1}
                            height={'auto'}
                            component={'img'}
                            alt={'Captcha Challenge'}
                            src={props.captchaProps.data.imgSrc}
                            draggable={false}
                            onLoad={() => setLoaded(true)}
                        />
                    </Box>
                </Stack>
            ) : pending ? (
                <Typography>{t('captcha.loading')}</Typography>
            ) : (
                <Typography>{t('common.error.unexpected')}</Typography>
            )}
        </Box>
    )
}

export default FormInputCaptcha
