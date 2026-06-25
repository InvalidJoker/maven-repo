import { useAuth } from '../auth'
import { AuthForm } from './AuthForm'

export function Login() {
  const { login } = useAuth()
  return (
    <AuthForm
      title="Sign in"
      submitLabel="Sign in"
      action={login}
      footer={{ prompt: 'No account?', linkLabel: 'Register', to: '/register' }}
    />
  )
}
