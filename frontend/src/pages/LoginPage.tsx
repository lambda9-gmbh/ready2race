import {useUser} from '../contexts/user/UserContext.ts'
import {useState} from 'react'
import {LoginRequest, userLogin} from '../api'
import {useTranslation} from 'react-i18next'
import {useFeedback} from '../utils/hooks.ts'

const LoginPage = () => {
    const {login} = useUser()
    const {t} = useTranslation()
    const feedback = useFeedback()

    const [formData, setFormData] = useState<LoginRequest>({
        email: '',
        password: '',
    })

    const handleSubmit = async () => {
        const {data, error} = await userLogin({
            body: formData,
        })
        if (data !== undefined) {
            login(data)
        } else {
            feedback.error(error)
        }
    }

    return (
        <form>
            <label>
                {t('login.email')}
                <input
                    type={'email'}
                    value={formData.email}
                    onChange={e => setFormData(prev => ({...prev, email: e.target.value}))}
                />
            </label>
            <label>
                {t('login.password')}
                <input
                    type={'password'}
                    value={formData.password}
                    onChange={e => setFormData(prev => ({...prev, password: e.target.value}))}
                />
            </label>
            <button onClick={handleSubmit}>Login</button>
        </form>
    )
}

export default LoginPage
