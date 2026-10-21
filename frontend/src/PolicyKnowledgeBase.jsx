import { useState, useEffect } from 'react'
import { fetchPolicyKnowledge } from './api.js'
import { money } from './format.js'

const CARD_OPTIONS = ['', 'PLATINUM', 'GOLD', 'GREEN']

export default function PolicyKnowledgeBase() {
  const [policies, setPolicies] = useState([])
  const [query, setQuery] = useState('')
  const [cardProduct, setCardProduct] = useState('')
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState('')
  const [expandedId, setExpandedId] = useState(null)

  useEffect(() => {
    let active = true
    setLoading(true)
    setError('')
    fetchPolicyKnowledge(query, cardProduct)
      .then((data) => {
        if (active) setPolicies(data || [])
      })
      .catch((err) => {
        if (active) setError(err.message || 'Failed to load policy knowledge base')
      })
      .finally(() => {
        if (active) setLoading(false)
      })
    return () => { active = false }
  }, [query, cardProduct])

  return (
    <div className="policy-kb-container">
      <div className="policy-kb-header">
        <h2 className="policy-kb-title">Benefit Policy Knowledge Base</h2>
        <p className="policy-kb-subtitle">
          Search structured benefit terms, coverage limits, qualifying categories, exclusions, and documentation rules.
        </p>
      </div>

      <div className="filter-bar policy-filter-bar">
        <input
          type="search"
          className="text-input policy-search-input"
          placeholder="Search by benefit, category, keyword, or rule…"
          value={query}
          onChange={(e) => setQuery(e.target.value)}
          aria-label="Search policies"
        />

        <select
          className="text-input"
          value={cardProduct}
          onChange={(e) => setCardProduct(e.target.value)}
          aria-label="Filter by card product"
        >
          <option value="">All card products</option>
          {CARD_OPTIONS.filter(Boolean).map((card) => (
            <option key={card} value={card}>{card}</option>
          ))}
        </select>

        {(query || cardProduct) && (
          <button
            type="button"
            className="ghost-button"
            onClick={() => { setQuery(''); setCardProduct('') }}
          >
            Reset
          </button>
        )}
      </div>

      {error && <div className="banner banner-error">{error}</div>}
      {loading ? (
        <p className="muted">Loading policy rules…</p>
      ) : policies.length === 0 ? (
        <div className="empty">
          <p className="empty-title">No policies found</p>
          <p className="muted">No benefit policies match "{query || cardProduct}". Try clearing filters.</p>
        </div>
      ) : (
        <div className="policy-card-grid">
          {policies.map((p) => {
            const isExpanded = expandedId === p.benefitId
            return (
              <article key={p.benefitId} className="policy-card">
                <div className="policy-card-top">
                  <div>
                    <span className="benefit-tag">{p.type}</span>
                    <h3 className="policy-name">{p.name}</h3>
                    <p className="policy-desc">{p.description}</p>
                  </div>
                  <div className="policy-limit-badge">
                    <span className="policy-limit-label">Limit per claim</span>
                    <span className="policy-limit-val">{money(p.perClaimLimit, 'USD')}</span>
                  </div>
                </div>

                <div className="policy-explanation-box">
                  <span className="policy-explanation-title">Eligibility Criteria</span>
                  <p>{p.userEligibilityExplanation}</p>
                </div>

                <div className="policy-meta-grid">
                  <div>
                    <span className="meta-label">Coverage Window</span>
                    <span className="meta-val">{p.coverageWindowExplanation}</span>
                  </div>
                  <div>
                    <span className="meta-label">Eligible Cards</span>
                    <span className="meta-val">{p.eligibleCards.join(', ') || 'None'}</span>
                  </div>
                </div>

                <div className="policy-section">
                  <span className="meta-label">Qualifying Merchant Categories</span>
                  <div className="tag-list">
                    {p.eligibleCategories.map((cat) => (
                      <span key={cat} className="category-pill">{cat}</span>
                    ))}
                  </div>
                </div>

                <button
                  type="button"
                  className="link-button policy-toggle-btn"
                  onClick={() => setExpandedId(isExpanded ? null : p.benefitId)}
                  aria-expanded={isExpanded}
                >
                  {isExpanded ? 'Hide policy rules & exclusions ▲' : 'View exclusions & required documents ▼'}
                </button>

                {isExpanded && (
                  <div className="policy-expanded-details">
                    <div className="policy-rules-row">
                      <div className="rules-column">
                        <h4>Required Documents for Claims</h4>
                        <ul className="policy-list">
                          {p.requiredDocuments.map((doc, idx) => (
                            <li key={idx}>✓ {doc}</li>
                          ))}
                        </ul>
                      </div>
                      <div className="rules-column">
                        <h4>Standard Policy Exclusions</h4>
                        <ul className="policy-list exclusions">
                          {p.exclusions.map((exc, idx) => (
                            <li key={idx}>✕ {exc}</li>
                          ))}
                        </ul>
                      </div>
                    </div>
                  </div>
                )}
              </article>
            )
          })}
        </div>
      )}
    </div>
  )
}

