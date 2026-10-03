import { useState } from 'react'
import { statusLabel, statusBadgeClass, money } from './format.js'

// Renders one claim. In the "Claims to make" tab it shows the pre-filled details
// and a Submit button; in "Submitted claims" it shows the current status.
export default function ClaimCard({ claim, showSubmit, onSubmit }) {
  const [busy, setBusy] = useState(false)
  const d = claim.prefilledData || {}

  const item = d.itemDescription || claim.benefitName || 'Purchase'
  const merchant = d.merchantName
  const purchaseAmount = d.purchaseAmount
  const purchaseDate = d.purchaseDate
  const coverageExpiresOn = claim.coverageExpiresOn
  const currentDate = new Date()
  const todayUtc = Date.UTC(currentDate.getFullYear(), currentDate.getMonth(), currentDate.getDate())
  const dateBasedDaysRemaining = coverageExpiresOn
    ? Math.round((Date.parse(`${coverageExpiresOn}T00:00:00Z`) - todayUtc) / 86400000)
    : null
  const coverageDaysRemaining = claim.coverageDaysRemaining ?? dateBasedDaysRemaining
  const coverageExpired = coverageDaysRemaining != null && coverageDaysRemaining < 0
  const coverageExpiringSoon = coverageDaysRemaining != null && coverageDaysRemaining >= 0 && coverageDaysRemaining <= 7

  async function handleClick() {
    setBusy(true)
    try {
      await onSubmit()
    } finally {
      setBusy(false)
    }
  }

  return (
    <article className="claim-card">
      <div className="claim-head">
        <div>
          <span className="benefit-tag">{claim.benefitName}</span>
          <h3 className="claim-item">{item}</h3>
          {merchant && (
            <p className="claim-meta">
              {merchant}
              {purchaseDate ? ` · ${purchaseDate}` : ''}
            </p>
          )}
        </div>
        {!showSubmit && <StatusBadge status={claim.status} />}
      </div>

      <dl className="claim-figures">
        {purchaseAmount && (
          <div>
            <dt>Purchase</dt>
            <dd>{money(purchaseAmount, claim.currency)}</dd>
          </div>
        )}
        <div>
          <dt>You can claim</dt>
          <dd className="claim-amount">{money(claim.claimAmount, claim.currency)}</dd>
        </div>
        {coverageExpiresOn && (
          <div>
            <dt>Coverage deadline</dt>
            <dd>
              <span className={`deadline-state ${coverageExpired ? 'expired' : coverageExpiringSoon ? 'expiring' : 'active'}`}>
                {coverageExpired
                  ? `Expired ${Math.abs(coverageDaysRemaining)} days ago`
                  : coverageDaysRemaining === 0
                    ? 'Expires today'
                    : coverageExpiringSoon
                      ? `Expiring soon · ${coverageDaysRemaining} days`
                      : `${coverageDaysRemaining} days remaining`}
              </span>
              <time dateTime={coverageExpiresOn}> · {coverageExpiresOn}</time>
            </dd>
          </div>
        )}
        {claim.benefitType === 'TRAVEL_DELAY' && (
          <div>
            <dt>Coverage</dt>
            <dd>Event-based travel delay</dd>
          </div>
        )}
      </dl>

      {showSubmit && coverageExpired && <p className="claim-reason" role="status">Coverage expired on {coverageExpiresOn}. This claim cannot be submitted.</p>}

      {claim.decisionReason && !showSubmit && (
        <p className="claim-reason">{claim.decisionReason}</p>
      )}

      {claim.payoutReference && (
        <p className="claim-meta">Payout reference: {claim.payoutReference}</p>
      )}

      {showSubmit && (
        <div className="claim-actions">
          <button className="primary-button" onClick={handleClick} disabled={busy || coverageExpired}>
            {coverageExpired ? 'Coverage expired' : busy ? 'Submitting…' : 'Review & submit'}
          </button>
        </div>
      )}
    </article>
  )
}

function StatusBadge({ status }) {
  return <span className={statusBadgeClass(status)}>{statusLabel(status)}</span>
}
