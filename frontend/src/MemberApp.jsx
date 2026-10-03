import { useEffect, useState, useCallback } from 'react'
import { fetchMyClaims, submitClaim, SessionExpiredError } from './api.js'
import { statusLabel } from './format.js'
import ClaimCard from './ClaimCard.jsx'
import ClaimReviewWizard from './ClaimReviewWizard.jsx'
import { handleTabListKeydown } from './keyboard.js'

const TABS = { TODO: 'todo', SUBMITTED: 'submitted' }

export default function MemberApp({ cardMemberId, onSignOut }) {
  const [tab, setTab] = useState(TABS.TODO)
  const [toClaim, setToClaim] = useState([])
  const [submitted, setSubmitted] = useState([])
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState('')
  const [notice, setNotice] = useState('')
  const [selectedClaimForReview, setSelectedClaimForReview] = useState(null)

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

  function handleOpenReview(claim) {
    setSelectedClaimForReview(claim)
  }

  async function handleClaimSubmitted(claim) {
    setNotice('')
    setError('')
    try {
      const updated = await submitClaim(claim.id)
      const item = updated.prefilledData?.itemDescription || updated.benefitName || 'your purchase'
      setNotice(`Claim for ${item} submitted — status is now ${statusLabel(updated.status)}.`)
      setSelectedClaimForReview(null)
      await loadClaims()
      setTab(TABS.SUBMITTED)
    } catch (err) {
      if (err instanceof SessionExpiredError) onSignOut()
      else setError(err.message)
    }
  }

  const activeList = tab === TABS.TODO ? toClaim : submitted
  const expiringClaims = toClaim.filter((claim) =>
    claim.coverageDaysRemaining != null
      && claim.coverageDaysRemaining >= 0
      && claim.coverageDaysRemaining <= 7)
  const activePanel = loading ? <p className="muted">Loading your claims…</p> : activeList.length === 0 ? (
    <EmptyState tab={tab} />
  ) : (
    <div className="claim-list">
      {activeList.map((claim) => (
        <ClaimCard
          key={claim.id}
          claim={claim}
          showSubmit={tab === TABS.TODO}
          onSubmit={() => handleOpenReview(claim)}
        />
      ))}
    </div>
  )

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
            id="member-tab-todo"
            role="tab"
            aria-selected={tab === TABS.TODO}
            aria-controls="member-panel-todo"
            tabIndex={tab === TABS.TODO ? 0 : -1}
            className={tab === TABS.TODO ? 'tab tab-active' : 'tab'}
            onClick={() => setTab(TABS.TODO)}
            onKeyDown={(event) => handleTabListKeydown(event, ['member-tab-todo', 'member-tab-submitted'], tab === TABS.TODO ? 0 : 1, (index) => setTab(index === 0 ? TABS.TODO : TABS.SUBMITTED))}
          >
            Claims to make
            {toClaim.length > 0 && <span className="tab-count">{toClaim.length}</span>}
          </button>
          <button
            id="member-tab-submitted"
            role="tab"
            aria-selected={tab === TABS.SUBMITTED}
            aria-controls="member-panel-submitted"
            tabIndex={tab === TABS.SUBMITTED ? 0 : -1}
            className={tab === TABS.SUBMITTED ? 'tab tab-active' : 'tab'}
            onClick={() => setTab(TABS.SUBMITTED)}
            onKeyDown={(event) => handleTabListKeydown(event, ['member-tab-todo', 'member-tab-submitted'], tab === TABS.TODO ? 0 : 1, (index) => setTab(index === 0 ? TABS.TODO : TABS.SUBMITTED))}
          >
            Submitted claims
            {submitted.length > 0 && <span className="tab-count">{submitted.length}</span>}
          </button>
        </nav>

        {notice && <div className="banner banner-success">{notice}</div>}
        {error && <div className="banner banner-error">{error}</div>}
        {tab === TABS.TODO && expiringClaims.length > 0 && (
          <div className="banner banner-warning" role="status">
            {expiringClaims.length} claim{expiringClaims.length === 1 ? '' : 's'} expire within 7 days.
          </div>
        )}

        <section id="member-panel-todo" role="tabpanel" aria-labelledby="member-tab-todo" tabIndex={0} hidden={tab !== TABS.TODO}>{tab === TABS.TODO && activePanel}</section>
        <section id="member-panel-submitted" role="tabpanel" aria-labelledby="member-tab-submitted" tabIndex={0} hidden={tab !== TABS.SUBMITTED}>{tab === TABS.SUBMITTED && activePanel}</section>
      </main>

      {selectedClaimForReview && (
        <ClaimReviewWizard
          claim={selectedClaimForReview}
          onClose={() => setSelectedClaimForReview(null)}
          onSubmitted={handleClaimSubmitted}
        />
      )}
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
