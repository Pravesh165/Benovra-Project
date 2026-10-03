import { useEffect, useState, useCallback } from 'react'
import {
  fetchAdminClaims,
  decideClaim,
  fetchMetrics,
  SessionExpiredError,
  fetchAdminClaimDocuments,
  fetchClaimAudit,
  fetchAdminClaimDocumentContent,
  fetchTransactions,
  fetchExpiringClaims,
} from './api.js'
import { statusLabel, statusBadgeClass, money } from './format.js'
import { handleTabListKeydown } from './keyboard.js'
import { useModalFocus } from './useModalFocus.js'

const TABS = { QUEUE: 'queue', SEARCH: 'search', DASHBOARD: 'dashboard' }
const CARD_PRODUCTS = ['PLATINUM', 'GOLD', 'GREEN']
const CATEGORIES = ['ELECTRONICS', 'APPLIANCES', 'APPAREL', 'DEPARTMENT_STORE', 'AIRLINE', 'LODGING', 'TRAVEL_AGENCY']
const BENEFIT_TYPES = ['PURCHASE_PROTECTION', 'RETURN_PROTECTION', 'TRAVEL_DELAY']
const STATUSES = ['PREFILLED', 'SUBMITTED', 'UNDER_REVIEW', 'APPROVED', 'REJECTED', 'PAID']

export default function ReviewerApp({ onSignOut }) {
  const [tab, setTab] = useState(TABS.QUEUE)
  const [inspectionClaim, setInspectionClaim] = useState(null)

  return (
    <div className="app">
      <header className="topbar">
        <div className="brand"><span className="brand-mark">◆</span><span>RewardHub · Reviewer</span></div>
        <div className="account">
          <span className="account-id">admin</span>
          <button className="link-button" onClick={onSignOut}>Sign out</button>
        </div>
      </header>

      <main className="content content-wide">
        <nav className="tabs" role="tablist" aria-label="Reviewer views">
          <button id="reviewer-tab-queue" role="tab" aria-selected={tab === TABS.QUEUE} aria-controls="reviewer-panel-queue" tabIndex={tab === TABS.QUEUE ? 0 : -1} className={tab === TABS.QUEUE ? 'tab tab-active' : 'tab'} onClick={() => setTab(TABS.QUEUE)} onKeyDown={(event) => handleTabListKeydown(event, ['reviewer-tab-queue', 'reviewer-tab-search', 'reviewer-tab-dashboard'], Object.values(TABS).indexOf(tab), (index) => setTab(Object.values(TABS)[index]))}>Review queue</button>
          <button id="reviewer-tab-search" role="tab" aria-selected={tab === TABS.SEARCH} aria-controls="reviewer-panel-search" tabIndex={tab === TABS.SEARCH ? 0 : -1} className={tab === TABS.SEARCH ? 'tab tab-active' : 'tab'} onClick={() => setTab(TABS.SEARCH)} onKeyDown={(event) => handleTabListKeydown(event, ['reviewer-tab-queue', 'reviewer-tab-search', 'reviewer-tab-dashboard'], Object.values(TABS).indexOf(tab), (index) => setTab(Object.values(TABS)[index]))}>Search</button>
          <button id="reviewer-tab-dashboard" role="tab" aria-selected={tab === TABS.DASHBOARD} aria-controls="reviewer-panel-dashboard" tabIndex={tab === TABS.DASHBOARD ? 0 : -1} className={tab === TABS.DASHBOARD ? 'tab tab-active' : 'tab'} onClick={() => setTab(TABS.DASHBOARD)} onKeyDown={(event) => handleTabListKeydown(event, ['reviewer-tab-queue', 'reviewer-tab-search', 'reviewer-tab-dashboard'], Object.values(TABS).indexOf(tab), (index) => setTab(Object.values(TABS)[index]))}>Dashboard</button>
        </nav>

        {inspectionClaim && <ReviewDetailPanel claim={inspectionClaim} onClose={() => setInspectionClaim(null)} />}
        <section id="reviewer-panel-queue" role="tabpanel" aria-labelledby="reviewer-tab-queue" tabIndex={0} hidden={tab !== TABS.QUEUE}>{tab === TABS.QUEUE && <ReviewQueue onSignOut={onSignOut} onInspect={setInspectionClaim} />}</section>
        <section id="reviewer-panel-search" role="tabpanel" aria-labelledby="reviewer-tab-search" tabIndex={0} hidden={tab !== TABS.SEARCH}>{tab === TABS.SEARCH && <ClaimSearch onSignOut={onSignOut} onInspect={setInspectionClaim} />}</section>
        <section id="reviewer-panel-dashboard" role="tabpanel" aria-labelledby="reviewer-tab-dashboard" tabIndex={0} hidden={tab !== TABS.DASHBOARD}>{tab === TABS.DASHBOARD && <Dashboard onSignOut={onSignOut} />}</section>
      </main>
    </div>
  )
}

function ReviewQueue({ onSignOut, onInspect }) {
  const [claims, setClaims] = useState([])
  const [reasons, setReasons] = useState({})
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState('')
  const [notice, setNotice] = useState('')
  const [busyId, setBusyId] = useState(null)

  const load = useCallback(async () => {
    setLoading(true)
    setError('')
    try {
      setClaims(await fetchAdminClaims({ status: 'UNDER_REVIEW' }))
    } catch (err) {
      if (err instanceof SessionExpiredError) onSignOut()
      else setError(err.message)
    } finally {
      setLoading(false)
    }
  }, [onSignOut])

  useEffect(() => { load() }, [load])

  async function decide(claim, decision) {
    setBusyId(claim.id)
    setNotice('')
    setError('')
    try {
      const updated = await decideClaim(claim.id, decision, reasons[claim.id] || '')
      setNotice(`Claim #${claim.id} (${claim.cardMemberId}) → ${statusLabel(updated.status)}.`)
      await load()
    } catch (err) {
      if (err instanceof SessionExpiredError) onSignOut()
      else setError(err.message)
    } finally {
      setBusyId(null)
    }
  }

  return (
    <section>
      <h1 className="page-title">Claims awaiting review</h1>
      <p className="page-subtitle">Approve to disburse, or reject with a reason. {claims.length} pending.</p>

      {notice && <div className="banner banner-success">{notice}</div>}
      {error && <div className="banner banner-error">{error}</div>}

      {loading ? (
        <p className="muted">Loading queue…</p>
      ) : claims.length === 0 ? (
        <div className="empty"><p className="empty-title">Queue is clear</p><p className="muted">No claims are waiting for a decision.</p></div>
      ) : (
        <div className="claim-list">
          {claims.map((c) => (
            <article key={c.id} className="claim-card">
              <div className="claim-head">
                <div>
                  <span className="benefit-tag">{c.benefitName}</span>
                  <h3 className="claim-item">{c.merchantName} · {money(c.claimAmount, c.currency)}</h3>
                  <p className="claim-meta">Customer {c.cardMemberId} · {c.cardProduct} · {c.merchantCategory}</p>
                </div>
                <span className={statusBadgeClass(c.status)}>{statusLabel(c.status)}</span>
              </div>

              <input className="text-input reason-input" type="text" placeholder="Reason (optional)" value={reasons[c.id] || ''} onChange={(e) => setReasons({ ...reasons, [c.id]: e.target.value })} />

              <div className="claim-actions row">
                <button className="primary-button" disabled={busyId === c.id} onClick={() => decide(c, 'APPROVE')}>{busyId === c.id ? '…' : 'Approve'}</button>
                <button className="ghost-button danger" disabled={busyId === c.id} onClick={() => decide(c, 'REJECT')}>Reject</button>
                <button className="ghost-button" type="button" onClick={() => onInspect(c)}>Inspect</button>
              </div>
            </article>
          ))}
        </div>
      )}
    </section>
  )
}

function ClaimSearch({ onSignOut, onInspect }) {
  const [filters, setFilters] = useState({ cardMemberId: '', cardProduct: '', merchantCategory: '', benefitType: '', status: '' })
  const [results, setResults] = useState([])
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState('')
  const [searched, setSearched] = useState(false)

  const runSearch = useCallback(async (f) => {
    setLoading(true)
    setError('')
    try {
      setResults(await fetchAdminClaims(f))
      setSearched(true)
    } catch (err) {
      if (err instanceof SessionExpiredError) onSignOut()
      else setError(err.message)
    } finally {
      setLoading(false)
    }
  }, [onSignOut])

  useEffect(() => { runSearch({}) }, [runSearch])

  function update(key, value) {
    setFilters((f) => ({ ...f, [key]: value }))
  }

  function onSubmit(e) {
    e.preventDefault()
    runSearch(filters)
  }

  function reset() {
    const cleared = { cardMemberId: '', cardProduct: '', merchantCategory: '', benefitType: '', status: '' }
    setFilters(cleared)
    runSearch(cleared)
  }

  return (
    <section>
      <h1 className="page-title">Search claims</h1>
      <p className="page-subtitle">Filter across all customers by ID, card, purchase type, or status.</p>

      <form className="filter-bar" onSubmit={onSubmit} aria-label="Claim search filters">
        <input className="text-input" aria-label="Customer ID" placeholder="Customer ID" value={filters.cardMemberId} onChange={(e) => update('cardMemberId', e.target.value)} />
        <select className="text-input" aria-label="Card product" value={filters.cardProduct} onChange={(e) => update('cardProduct', e.target.value)}>
          <option value="">Any card</option>
          {CARD_PRODUCTS.map((p) => <option key={p} value={p}>{p}</option>)}
        </select>
        <select className="text-input" aria-label="Merchant category" value={filters.merchantCategory} onChange={(e) => update('merchantCategory', e.target.value)}>
          <option value="">Any category</option>
          {CATEGORIES.map((p) => <option key={p} value={p}>{p}</option>)}
        </select>
        <select className="text-input" aria-label="Benefit type" value={filters.benefitType} onChange={(e) => update('benefitType', e.target.value)}>
          <option value="">Any benefit</option>
          {BENEFIT_TYPES.map((p) => <option key={p} value={p}>{p.replace(/_/g, ' ')}</option>)}
        </select>
        <select className="text-input" aria-label="Claim status" value={filters.status} onChange={(e) => update('status', e.target.value)}>
          <option value="">Any status</option>
          {STATUSES.map((p) => <option key={p} value={p}>{statusLabel(p)}</option>)}
        </select>
        <button className="primary-button inline" type="submit">Search</button>
        <button className="ghost-button" type="button" onClick={reset}>Reset</button>
      </form>

      {error && <div className="banner banner-error">{error}</div>}
      {loading ? <p className="muted">Searching…</p> : searched && results.length === 0 ? <div className="empty"><p className="empty-title">No matches</p><p className="muted">Try widening your filters.</p></div> : <ResultsTable rows={results} onInspect={onInspect} />}
    </section>
  )
}

function ResultsTable({ rows, onInspect }) {
  return (
    <div className="table-wrap" role="region" aria-label="Claim search results" tabIndex={0}>
      <table className="data-table">
        <caption className="visually-hidden">Claim search results</caption>
        <thead>
          <tr>
            <th>#</th><th>Customer</th><th>Card</th><th>Benefit</th><th>Merchant</th><th>Category</th><th className="num">Claim</th><th>Status</th><th>Action</th>
          </tr>
        </thead>
        <tbody>
          {rows.map((c) => (
            <tr key={c.id}>
              <td>{c.id}</td>
              <td>{c.cardMemberId}</td>
              <td>{c.cardProduct}</td>
              <td>{c.benefitName}</td>
              <td>{c.merchantName}</td>
              <td>{c.merchantCategory}</td>
              <td className="num">{money(c.claimAmount, c.currency)}</td>
              <td><span className={statusBadgeClass(c.status)}>{statusLabel(c.status)}</span></td>
              <td><button className="ghost-button tiny" type="button" onClick={() => onInspect(c)}>Inspect</button></td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  )
}

function ReviewDetailPanel({ claim, onClose }) {
  const dialogRef = useModalFocus(onClose)
  const [documents, setDocuments] = useState([])
  const [auditTrail, setAuditTrail] = useState([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')

  useEffect(() => {
    if (!claim?.id) return
    ;(async () => {
      try {
        const [documentsResult, auditResult] = await Promise.all([
          fetchAdminClaimDocuments(claim.id),
          fetchClaimAudit(claim.id),
        ])
        setDocuments(documentsResult || [])
        setAuditTrail(auditResult || [])
      } catch (err) {
        setError(err.message || 'Failed to load claim details')
      } finally {
        setLoading(false)
      }
    })()
  }, [claim])

  if (!claim) return null

  return (
    <div className="modal-backdrop">
      <dialog open ref={dialogRef} className="modal-card wide" aria-modal="true" aria-labelledby="claim-inspection-title" tabIndex={-1}>
        <div className="modal-head">
          <div>
            <span className="benefit-tag">{claim.benefitName}</span>
            <h2 className="modal-title" id="claim-inspection-title">Claim inspection · #{claim.id}</h2>
            <p className="modal-subtitle">Document checklist and event audit trail for {claim.cardMemberId}.</p>
          </div>
          <button className="close-btn" onClick={onClose} aria-label="Close modal">×</button>
        </div>
        {error && <div className="banner banner-error">{error}</div>}

        {loading ? (
          <p className="muted">Loading claim record…</p>
        ) : (
          <>
          <section className="inspection-summary" aria-label="Claim summary">
            <div><span>Claim</span><strong>#{claim.id}</strong></div>
            <div><span>Status</span><strong><span className={statusBadgeClass(claim.status)}>{statusLabel(claim.status)}</span></strong></div>
            <div><span>Benefit</span><strong>{claim.benefitName} · {claim.benefitType}</strong></div>
            <div><span>Claim amount</span><strong>{money(claim.claimAmount, claim.currency)}</strong></div>
            <div><span>Purchase amount</span><strong>{money(claim.purchaseAmount, claim.currency)}</strong></div>
            <div><span>Coverage deadline</span><strong>{claim.coverageExpiresOn || (claim.benefitType === 'TRAVEL_DELAY' ? 'Event-based' : 'Unavailable')}</strong></div>
            <div><span>Card member</span><strong>{claim.cardMemberId} · {claim.cardProduct}</strong></div>
            <div><span>Merchant</span><strong>{claim.merchantName} · {claim.merchantCategory}</strong></div>
          </section>
          <div className="detail-grid">
            <div className="detail-panel">
              <h3>Supporting documents</h3>
              {documents.length === 0 ? (
                <p className="muted">No documents uploaded.</p>
              ) : (
                <ul className="doc-list">
                  {documents.map((doc) => (
                    <li key={doc.id}>
                      <div>
                        <strong>{doc.documentType}</strong>
                        <span>{doc.originalFilename}</span>
                        <span>{doc.required ? 'Required' : 'Optional'} · {new Date(doc.uploadedAt).toLocaleString()}</span>
                      </div>
                      <button type="button" className="ghost-button tiny" onClick={async () => {
                        const blob = await fetchAdminClaimDocumentContent(claim.id, doc.id)
                        const url = URL.createObjectURL(blob)
                        const a = document.createElement('a')
                        a.href = url
                        a.download = doc.originalFilename || 'document'
                        a.click()
                        URL.revokeObjectURL(url)
                      }}>Download</button>
                    </li>
                  ))}
                </ul>
              )}
            </div>

            <div className="detail-panel">
              <h3>Audit trail</h3>
              {auditTrail.length === 0 ? (
                <p className="muted">No audit events recorded.</p>
              ) : (
                <ul className="audit-list">
                  {auditTrail.map((event) => (
                    <li key={event.id}>
                      <span>{new Date(event.occurredAt).toLocaleString()}</span>
                      <strong>{event.fromStatus ? statusLabel(event.fromStatus) : 'Created'} to {statusLabel(event.toStatus)}</strong>
                      <strong>{event.actor}</strong>
                      <p>{event.detail}</p>
                    </li>
                  ))}
                </ul>
              )}
            </div>
          </div>
          </>
        )}
      </dialog>
    </div>
  )
}

function Dashboard({ onSignOut }) {
  const [m, setM] = useState(null)
  const [unmatchedTransactions, setUnmatchedTransactions] = useState([])
  const [expiringClaims, setExpiringClaims] = useState([])
  const [error, setError] = useState('')

  useEffect(() => {
    (async () => {
      try {
        const [metrics, transactions, expiring] = await Promise.all([
          fetchMetrics(),
          fetchTransactions('NO_MATCH'),
          fetchExpiringClaims(7),
        ])
        setM(metrics)
        setUnmatchedTransactions(transactions || [])
        setExpiringClaims(expiring || [])
      } catch (err) {
        if (err instanceof SessionExpiredError) onSignOut()
        else setError(err.message)
      }
    })()
  }, [onSignOut])

  if (error) return <div className="banner banner-error">{error}</div>
  if (!m) return <p className="muted">Loading metrics…</p>

  const cur = m.currency && m.currency !== 'MIXED' ? m.currency : ''
  const statusEntries = STATUSES.map((s) => [s, (m.claimsByStatus && m.claimsByStatus[s]) || 0])
  const maxCount = Math.max(1, ...statusEntries.map(([, n]) => n))

  return (
    <section>
      <h1 className="page-title">Dashboard</h1>
      <p className="page-subtitle">A live summary of detection and claims across the programme.</p>

      <div className="metric-grid">
        <MetricCard label="Purchases ingested" value={m.totalTransactions} />
        <MetricCard label="Matched to a benefit" value={m.matchedTransactions} sub={`${m.detectionRatePct}% detection rate`} />
        <MetricCard label="Total claims" value={m.totalClaims} />
        <MetricCard label="Detectable value" value={money(m.totalDetectableValue, cur)} />
        <MetricCard label="Claimed value" value={money(m.claimedValue, cur)} accent />
        <MetricCard label="Processed value" value={money(m.paidValue, cur)} />
        <MetricCard label="Still unclaimed" value={money(m.unclaimedValue, cur)} />
        <MetricCard label="Unclaimed reduction" value={`${m.unclaimedReductionPct}%`} accent />
      </div>

      <h2 className="section-title">Claims expiring within 7 days</h2>
      {expiringClaims.length === 0 ? (
        <div className="empty"><p className="empty-title">No claims expiring soon</p></div>
      ) : (
        <div className="table-wrap" role="region" aria-label="Claims expiring within seven days" tabIndex={0}>
          <table className="data-table">
            <caption className="visually-hidden">Claims nearing coverage expiration</caption>
            <thead><tr><th>Claim</th><th>Member</th><th>Benefit</th><th>Merchant</th><th>Days remaining</th><th>Deadline</th></tr></thead>
            <tbody>
              {expiringClaims.map((claim) => (
                <tr key={claim.id}>
                  <td>#{claim.id}</td>
                  <td>{claim.cardMemberId}</td>
                  <td>{claim.benefitName}</td>
                  <td>{claim.merchantName}</td>
                  <td>{claim.coverageDaysRemaining}</td>
                  <td>{claim.coverageExpiresOn}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}

      <h2 className="section-title">Benefit utilization</h2>
      <div className="benefit-utilization-list">
        {BENEFIT_TYPES.map((type) => {
          const utilization = m.utilizationByBenefit?.[type]
          const percent = utilization?.utilizationPct || 0
          return (
            <div className="benefit-utilization-row" key={type}>
              <div className="benefit-utilization-heading">
                <strong>{type.replaceAll('_', ' ')}</strong>
                <span>{percent}% · {utilization?.claimedClaims || 0}/{utilization?.eligibleClaims || 0} claims</span>
              </div>
              <div className="bar-track" role="progressbar" aria-label={`${type.replaceAll('_', ' ')} utilization`} aria-valuenow={percent} aria-valuemin="0" aria-valuemax="100">
                <div className="bar-fill" style={{ width: `${Math.min(100, percent)}%` }} />
              </div>
              <span className="muted">Claimed {money(utilization?.claimedValue, cur)} of {money(utilization?.claimableValue, cur)}</span>
            </div>
          )
        })}
      </div>

      <h2 className="section-title">Claims by status</h2>
      <div className="bars">
        {statusEntries.map(([s, n]) => (
          <div className="bar-row" key={s}>
            <span className="bar-label">{statusLabel(s)}</span>
            <div className="bar-track">
              <div className="bar-fill" style={{ width: `${(n / maxCount) * 100}%` }} />
            </div>
            <span className="bar-value">{n}</span>
          </div>
        ))}
      </div>

      <h2 className="section-title unmatched-title">Unmatched purchases</h2>
      {unmatchedTransactions.length === 0 ? (
        <div className="empty"><p className="empty-title">No unmatched purchases</p><p className="muted">All recorded purchases match an active benefit or have not been evaluated.</p></div>
      ) : (
        <div className="table-wrap" role="region" aria-label="Unmatched purchases" tabIndex={0}>
          <table className="data-table">
            <caption className="visually-hidden">Unmatched purchases and benefit matching reasons</caption>
            <thead><tr><th>Purchase</th><th>Card</th><th>Purchase date</th><th>Reason</th></tr></thead>
            <tbody>
              {unmatchedTransactions.slice(0, 10).map((transaction) => (
                <tr key={transaction.id}>
                  <td>{transaction.merchantName} · {money(transaction.amount, transaction.currency)}</td>
                  <td>{transaction.cardProduct}</td>
                  <td>{transaction.purchaseDate}</td>
                  <td>{transaction.matchReason || 'No matching reason recorded.'}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </section>
  )
}

function MetricCard({ label, value, sub, accent }) {
  return (
    <div className="metric-card">
      <div className="metric-label">{label}</div>
      <div className={accent ? 'metric-value metric-accent' : 'metric-value'}>{value}</div>
      {sub && <div className="metric-sub">{sub}</div>}
    </div>
  )
}
