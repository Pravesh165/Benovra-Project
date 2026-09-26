import { useState } from 'react'
import { login, saveSession, clearSession, getToken, getCardMemberId, getRole } from './api.js'
import Login from './Login.jsx'
import MemberApp from './MemberApp.jsx'
import ReviewerApp from './ReviewerApp.jsx'

export default function App() {
  const [session, setSession] = useState(() =>
    getToken() ? { cardMemberId: getCardMemberId(), role: getRole() } : null,
  )

  async function handleLogin(id) {
    const res = await login(id)
    saveSession(res.token, res.cardMemberId, res.role)
    setSession({ cardMemberId: res.cardMemberId, role: res.role })
  }

  function handleSignOut() {
    clearSession()
    setSession(null)
  }

  if (!session) {
    return <Login onLogin={handleLogin} />
  }

  if (session.role === 'REVIEWER') {
    return <ReviewerApp onSignOut={handleSignOut} />
  }

  return <MemberApp cardMemberId={session.cardMemberId} onSignOut={handleSignOut} />
}
