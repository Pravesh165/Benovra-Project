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
      </dl>

      {claim.decisionReason && !showSubmit && (
        <p className="claim-reason">{claim.decisionReason}</p>
      )}

      {claim.payoutReference && (
        <p className="claim-meta">Payout reference: {claim.payoutReference}</p>
      )}

      {showSubmit && (
        <div className="claim-actions">
          <button className="primary-button" onClick={handleClick} disabled={busy}>
            {busy ? 'Submitting…' : 'Review & submit'}
          </button>
        </div>
      )}
    </article>
  )
}

function StatusBadge({ status }) {
  return <span className={statusBadgeClass(status)}>{statusLabel(status)}</span>
}
