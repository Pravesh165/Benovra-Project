import { useState } from 'react'

// Simple sign-in: the card member enters their member id. On success the parent
// stores the returned token and shows the claims screen.
export default function Login({ onLogin, initialError }) {
  const [cardMemberId, setCardMemberId] = useState('')
  const [error, setError] = useState(initialError || '')
  const [busy, setBusy] = useState(false)

  async function handleSubmit(e) {
    e.preventDefault()
    const id = cardMemberId.trim()
    if (!id) {
      setError('Please enter your card member ID.')
      return
    }
    setBusy(true)
    setError('')
    try {
      await onLogin(id)
    } catch (err) {
      setError(err.message || 'Could not sign in. Please try again.')
    } finally {
      setBusy(false)
    }
  }

  return (
    <div className="login-page">
      <form className="login-card" onSubmit={handleSubmit}>
        <div className="brand brand-large">
          <span className="brand-mark">◆</span>
          <span>RewardHub</span>
        </div>
        <h1 className="login-title">Sign in</h1>
        <p className="login-subtitle">
          Enter your card member ID to see the benefit claims we've prepared for you.
          Reviewers can sign in by typing <strong>admin</strong>.
        </p>

        <label className="field-label" htmlFor="cardMemberId">Card member ID</label>
        <input
          id="cardMemberId"
          className="text-input"
          type="text"
          autoComplete="off"
          autoFocus
          placeholder="e.g. CM-1001"
          value={cardMemberId}
          onChange={(e) => setCardMemberId(e.target.value)}
        />

        {error && <div className="banner banner-error">{error}</div>}

        <button className="primary-button" type="submit" disabled={busy}>
          {busy ? 'Signing in…' : 'Continue'}
        </button>
      </form>
    </div>
  )
}
