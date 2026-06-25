import { useAuth } from '../auth'
import { AuthForm } from './AuthForm'

export function Register() {
  const { register } = useAuth()
  return (
    <AuthForm
      title="Create account"
      submitLabel="Register"
      action={register}
      footer={{ prompt: 'Already have an account?', linkLabel: 'Sign in', to: '/login' }}
    />
  )
}
