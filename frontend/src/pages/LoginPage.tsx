import {useUser} from '../contexts/user/UserContext.ts'
import {useState} from 'react'
import {LoginRequest, userLogin} from '../api'

const LoginPage = () => {
    const {login} = useUser()

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
            console.log(data)
        } else {
            console.log(error)
        }
    }

    return (
        <form>
            <label>
                email
                <input
                    type={'email'}
                    value={formData.email}
                    onChange={e => setFormData(prev => ({...prev, email: e.target.value}))}
                />
            </label>
            <label>
                password
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
