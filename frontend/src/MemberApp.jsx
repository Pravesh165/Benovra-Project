import { useEffect, useState, useCallback } from 'react'
import { fetchMyClaims, submitClaim, SessionExpiredError } from './api.js'
import { statusLabel } from './format.js'
import ClaimCard from './ClaimCard.jsx'

const TABS = { TODO: 'todo', SUBMITTED: 'submitted' }

export default function MemberApp({ cardMemberId, onSignOut }) {
  const [tab, setTab] = useState(TABS.TODO)
  const [toClaim, setToClaim] = useState([])
  const [submitted, setSubmitted] = useState([])
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState('')
  const [notice, setNotice] = useState('')

  const loadClaims = useCallback(async () => {
    setLoading(true)
    setError('')
    try {
      const [prefilled, all] = await Promise.all([fetchMyClaims('PREFILLED'), fetchMyClaims()])
      setToClaim(prefilled)
      setSubmitted(all.filter((c) => c.status !== 'PREFILLED'))
    } catch (err) {
      if (err instanceof SessionExpiredError) onSignOut()
      else setError(err.message)
    } finally {
      setLoading(false)
    }
  }, [onSignOut])

  useEffect(() => { loadClaims() }, [loadClaims])

  async function handleSubmit(claim) {
    setNotice('')
    setError('')
    try {
      const updated = await submitClaim(claim.id)
      const item = claim.prefilledData?.itemDescription || claim.benefitName || 'your purchase'
      setNotice(`Claim for ${item} submitted — status is now ${statusLabel(updated.status)}.`)
      await loadClaims()
      setTab(TABS.SUBMITTED)
    } catch (err) {
      if (err instanceof SessionExpiredError) onSignOut()
      else setError(err.message)
    }
  }

  const activeList = tab === TABS.TODO ? toClaim : submitted

  return (
    <div className="app">
      <header className="topbar">
        <div className="brand"><span className="brand-mark">◆</span><span>RewardHub</span></div>
        <div className="account">
          <span className="account-id">{cardMemberId}</span>
          <button className="link-button" onClick={onSignOut}>Sign out</button>
        </div>
      </header>

      <main className="content">
        <h1 className="page-title">Your protection benefits</h1>
        <p className="page-subtitle">
          We checked your recent purchases and prepared the claims you can make.
        </p>

        <nav className="tabs" role="tablist">
          <button
            role="tab"
            className={tab === TABS.TODO ? 'tab tab-active' : 'tab'}
            onClick={() => setTab(TABS.TODO)}
          >
            Claims to make
            {toClaim.length > 0 && <span className="tab-count">{toClaim.length}</span>}
          </button>
          <button
            role="tab"
            className={tab === TABS.SUBMITTED ? 'tab tab-active' : 'tab'}
            onClick={() => setTab(TABS.SUBMITTED)}
          >
            Submitted claims
            {submitted.length > 0 && <span className="tab-count">{submitted.length}</span>}
          </button>
        </nav>

        {notice && <div className="banner banner-success">{notice}</div>}
        {error && <div className="banner banner-error">{error}</div>}

        {loading ? (
          <p className="muted">Loading your claims…</p>
        ) : activeList.length === 0 ? (
          <EmptyState tab={tab} />
        ) : (
          <div className="claim-list">
            {activeList.map((claim) => (
              <ClaimCard
                key={claim.id}
                claim={claim}
                showSubmit={tab === TABS.TODO}
                onSubmit={() => handleSubmit(claim)}
              />
            ))}
          </div>
        )}
      </main>
    </div>
  )
}

function EmptyState({ tab }) {
  if (tab === TABS.TODO) {
    return (
      <div className="empty">
        <p className="empty-title">You're all caught up</p>
        <p className="muted">There are no new claims waiting to be made.</p>
      </div>
    )
  }
  return (
    <div className="empty">
      <p className="empty-title">Nothing submitted yet</p>
      <p className="muted">Claims you submit will show up here with their status.</p>
    </div>
  )
}
