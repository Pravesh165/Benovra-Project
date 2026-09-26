import { useEffect, useState, useCallback } from 'react'
import { fetchAdminClaims, decideClaim, fetchMetrics, SessionExpiredError } from './api.js'
import { statusLabel, statusBadgeClass, money } from './format.js'

const TABS = { QUEUE: 'queue', SEARCH: 'search', DASHBOARD: 'dashboard' }

const CARD_PRODUCTS = ['PLATINUM', 'GOLD', 'GREEN']
const CATEGORIES = ['ELECTRONICS', 'APPLIANCES', 'APPAREL', 'DEPARTMENT_STORE', 'AIRLINE', 'LODGING', 'TRAVEL_AGENCY']
const BENEFIT_TYPES = ['PURCHASE_PROTECTION', 'RETURN_PROTECTION', 'TRAVEL_DELAY']
const STATUSES = ['PREFILLED', 'SUBMITTED', 'UNDER_REVIEW', 'APPROVED', 'REJECTED', 'PAID']

export default function ReviewerApp({ onSignOut }) {
  const [tab, setTab] = useState(TABS.QUEUE)

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
        <nav className="tabs" role="tablist">
          <button className={tab === TABS.QUEUE ? 'tab tab-active' : 'tab'} onClick={() => setTab(TABS.QUEUE)}>Review queue</button>
          <button className={tab === TABS.SEARCH ? 'tab tab-active' : 'tab'} onClick={() => setTab(TABS.SEARCH)}>Search</button>
          <button className={tab === TABS.DASHBOARD ? 'tab tab-active' : 'tab'} onClick={() => setTab(TABS.DASHBOARD)}>Dashboard</button>
        </nav>

        {tab === TABS.QUEUE && <ReviewQueue onSignOut={onSignOut} />}
        {tab === TABS.SEARCH && <ClaimSearch onSignOut={onSignOut} />}
        {tab === TABS.DASHBOARD && <Dashboard onSignOut={onSignOut} />}
      </main>
    </div>
  )
}

// ---------------------------------------------------------------- Review queue
function ReviewQueue({ onSignOut }) {
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
                  <p className="claim-meta">
                    Customer {c.cardMemberId} · {c.cardProduct} · {c.merchantCategory}
                  </p>
                </div>
                <span className={statusBadgeClass(c.status)}>{statusLabel(c.status)}</span>
              </div>

              <input
                className="text-input reason-input"
                type="text"
                placeholder="Reason (optional)"
                value={reasons[c.id] || ''}
                onChange={(e) => setReasons({ ...reasons, [c.id]: e.target.value })}
              />
              <div className="claim-actions row">
                <button className="primary-button" disabled={busyId === c.id} onClick={() => decide(c, 'APPROVE')}>
                  {busyId === c.id ? '…' : 'Approve'}
                </button>
                <button className="ghost-button danger" disabled={busyId === c.id} onClick={() => decide(c, 'REJECT')}>
                  Reject
                </button>
              </div>
            </article>
          ))}
        </div>
      )}
    </section>
  )
}

// -------------------------------------------------------------------- Search
function ClaimSearch({ onSignOut }) {
  const [filters, setFilters] = useState({
    cardMemberId: '', cardProduct: '', merchantCategory: '', benefitType: '', status: '',
  })
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

  useEffect(() => { runSearch({}) }, [runSearch]) // show everything initially

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

      <form className="filter-bar" onSubmit={onSubmit}>
        <input className="text-input" placeholder="Customer ID" value={filters.cardMemberId}
          onChange={(e) => update('cardMemberId', e.target.value)} />
        <select className="text-input" value={filters.cardProduct} onChange={(e) => update('cardProduct', e.target.value)}>
          <option value="">Any card</option>
          {CARD_PRODUCTS.map((p) => <option key={p} value={p}>{p}</option>)}
        </select>
        <select className="text-input" value={filters.merchantCategory} onChange={(e) => update('merchantCategory', e.target.value)}>
          <option value="">Any category</option>
          {CATEGORIES.map((p) => <option key={p} value={p}>{p}</option>)}
        </select>
        <select className="text-input" value={filters.benefitType} onChange={(e) => update('benefitType', e.target.value)}>
          <option value="">Any benefit</option>
          {BENEFIT_TYPES.map((p) => <option key={p} value={p}>{p.replace(/_/g, ' ')}</option>)}
        </select>
        <select className="text-input" value={filters.status} onChange={(e) => update('status', e.target.value)}>
          <option value="">Any status</option>
          {STATUSES.map((p) => <option key={p} value={p}>{statusLabel(p)}</option>)}
        </select>
        <button className="primary-button inline" type="submit">Search</button>
        <button className="ghost-button" type="button" onClick={reset}>Reset</button>
      </form>

      {error && <div className="banner banner-error">{error}</div>}

      {loading ? (
        <p className="muted">Searching…</p>
      ) : searched && results.length === 0 ? (
        <div className="empty"><p className="empty-title">No matches</p><p className="muted">Try widening your filters.</p></div>
      ) : (
        <ResultsTable rows={results} />
      )}
    </section>
  )
}

function ResultsTable({ rows }) {
  return (
    <div className="table-wrap">
      <table className="data-table">
        <thead>
          <tr>
            <th>#</th><th>Customer</th><th>Card</th><th>Benefit</th>
            <th>Merchant</th><th>Category</th><th className="num">Claim</th><th>Status</th>
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
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  )
}

// ------------------------------------------------------------------ Dashboard
function Dashboard({ onSignOut }) {
  const [m, setM] = useState(null)
  const [error, setError] = useState('')

  useEffect(() => {
    (async () => {
      try {
        setM(await fetchMetrics())
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
