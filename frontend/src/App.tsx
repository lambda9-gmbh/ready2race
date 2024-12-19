import './App.css'
import {useUser} from './contexts/user/UserContext.ts'
import {RouterProvider} from '@tanstack/react-router'
import {router} from './routes.tsx'
import {client} from './api'
import Config from './Config.ts'

client.setConfig({
    baseUrl: Config.api.baseUrl,
    credentials: 'include',
})

function App() {
    const user = useUser()

    return <RouterProvider router={router} context={user}></RouterProvider>
}

export default App
