import { useState, useEffect } from 'react'
import { money } from './format.js'
import { fetchMatchedBenefits, switchClaimBenefit, updateClaimDraft } from './api.js'

export default function ClaimReviewWizard({ claim, onClose, onSubmitted }) {
  const d = claim.prefilledData || {}

  // Multi-benefit state
  const [matchedBenefits, setMatchedBenefits] = useState([])
  const [currentClaim, setCurrentClaim] = useState(claim)
  const [loadingBenefits, setLoadingBenefits] = useState(false)
  const [switchingBenefit, setSwitchingBenefit] = useState(false)

  // Draft form state
  const [itemDescription, setItemDescription] = useState(
    currentClaim.prefilledData?.itemDescription || currentClaim.benefitName || ''
  )
  const [incidentDate, setIncidentDate] = useState(
    currentClaim.prefilledData?.incidentDate || currentClaim.prefilledData?.purchaseDate || ''
  )
  const [incidentExplanation, setIncidentExplanation] = useState(
    currentClaim.prefilledData?.incidentExplanation || ''
  )

  const [saving, setSaving] = useState(false)
  const [submitting, setSubmitting] = useState(false)
  const [error, setError] = useState('')
  const [successMsg, setSuccessMsg] = useState('')

  // Load alternative matched benefits if transactionId is available
  useEffect(() => {
    if (claim.transactionId) {
      setLoadingBenefits(true)
      fetchMatchedBenefits(claim.transactionId)
        .then((benefits) => {
          setMatchedBenefits(benefits || [])
        })
        .catch(() => {
          // Non-blocking: if alternative lookup fails, keep current benefit
        })
        .finally(() => setLoadingBenefits(false))
    }
  }, [claim.transactionId])

  // Sync form when currentClaim changes (e.g. after benefit switch)
  useEffect(() => {
    const cd = currentClaim.prefilledData || {}
    setItemDescription(cd.itemDescription || currentClaim.benefitName || '')
    setIncidentDate(cd.incidentDate || cd.purchaseDate || '')
    setIncidentExplanation(cd.incidentExplanation || '')
  }, [currentClaim])

  async function handleBenefitSwitch(benefitId) {
    if (benefitId === currentClaim.benefitId || switchingBenefit) return
    setSwitchingBenefit(true)
    setError('')
    try {
      const updated = await switchClaimBenefit(currentClaim.id, benefitId)
      setCurrentClaim(updated)
      setSuccessMsg(`Switched to ${updated.benefitName}. Details recalculated.`)
    } catch (err) {
      setError(err.message || 'Failed to switch benefit')
    } finally {
      setSwitchingBenefit(false)
    }
  }

  async function handleSaveDraft() {
    setSaving(true)
    setError('')
    setSuccessMsg('')
    try {
      const updated = await updateClaimDraft(currentClaim.id, {
        itemDescription: itemDescription.trim(),
        incidentDate: incidentDate.trim(),
        incidentExplanation: incidentExplanation.trim(),
      })
      setCurrentClaim(updated)
      setSuccessMsg('Claim draft details updated successfully.')
    } catch (err) {
      setError(err.message || 'Failed to update draft')
    } finally {
      setSaving(false)
    }
  }

  async function handleFinalSubmit(e) {
    e.preventDefault()
    setSubmitting(true)
    setError('')
    try {
      // Auto-save changes first before submitting
      const updated = await updateClaimDraft(currentClaim.id, {
        itemDescription: itemDescription.trim(),
        incidentDate: incidentDate.trim(),
        incidentExplanation: incidentExplanation.trim(),
      })
      await onSubmitted(updated)
    } catch (err) {
      setError(err.message || 'Failed to submit claim')
      setSubmitting(false)
    }
  }

  const cd = currentClaim.prefilledData || {}

  return (
    <div className="modal-backdrop" onClick={onClose}>
      <div className="modal-card" onClick={(e) => e.stopPropagation()}>
        <div className="modal-head">
          <div>
            <span className="benefit-tag">{currentClaim.benefitName}</span>
            <h2 className="modal-title">Review & Submit Claim</h2>
            <p className="modal-subtitle">
              Verify your claim details, switch to an alternative benefit if eligible, and provide any incident notes before submitting.
            </p>
          </div>
          <button className="close-btn" onClick={onClose} aria-label="Close modal">×</button>
        </div>

        {error && <div className="banner banner-error">{error}</div>}
        {successMsg && <div className="banner banner-success">{successMsg}</div>}

        {/* ── Multi-Benefit Selector (if 2+ benefits matched) ── */}
        {matchedBenefits.length > 1 && (
          <div className="benefit-selector-section">
            <label className="field-label">
              Eligible Benefits ({matchedBenefits.length} detected for this purchase)
            </label>
            <p className="field-hint">
              This purchase qualifies for multiple card benefits. Select which protection you want to claim:
            </p>
            <div className="benefit-options-grid">
              {matchedBenefits.map((b) => {
                const isSelected = b.benefitId === currentClaim.benefitId
                return (
                  <button
                    key={b.benefitId}
                    type="button"
                    className={`benefit-option-card ${isSelected ? 'selected' : ''}`}
                    onClick={() => handleBenefitSwitch(b.benefitId)}
                    disabled={switchingBenefit || isSelected}
                  >
                    <div className="option-header">
                      <span className="option-title">{b.name}</span>
                      {isSelected && <span className="selected-badge">Active</span>}
                    </div>
                    <div className="option-amount">
                      Up to {money(b.estimatedClaimAmount, currentClaim.currency)}
                    </div>
                    <p className="option-reason">{b.reason}</p>
                  </button>
                )
              })}
            </div>
          </div>
        )}

        <form onSubmit={handleFinalSubmit} className="wizard-form">
          {/* ── Claim Figures & Summary ── */}
          <div className="wizard-summary-box">
            <div className="summary-item">
              <span className="summary-label">Merchant</span>
              <span className="summary-value">{cd.merchantName || '—'}</span>
            </div>
            <div className="summary-item">
              <span className="summary-label">Purchase Date</span>
              <span className="summary-value">{cd.purchaseDate || '—'}</span>
            </div>
            <div className="summary-item">
              <span className="summary-label">Purchase Amount</span>
              <span className="summary-value">{money(cd.purchaseAmount, currentClaim.currency)}</span>
            </div>
            <div className="summary-item highlight">
              <span className="summary-label">Claiming Amount</span>
              <span className="summary-value claim-amount">{money(currentClaim.claimAmount, currentClaim.currency)}</span>
            </div>
          </div>

          {/* ── Editable Fields ── */}
          <div className="form-group">
            <label className="field-label" htmlFor="item-desc">
              Item / Protection Description *
            </label>
            <input
              id="item-desc"
              type="text"
              className="text-input"
              value={itemDescription}
              onChange={(e) => setItemDescription(e.target.value)}
              placeholder="e.g. MacBook Pro 14 or Wireless Headphones"
              required
            />
            <span className="field-hint">Specify the exact item or service covered by this benefit.</span>
          </div>

          <div className="form-group">
            <label className="field-label" htmlFor="incident-date">
              Incident / Claim Date
            </label>
            <input
              id="incident-date"
              type="date"
              className="text-input"
              value={incidentDate}
              onChange={(e) => setIncidentDate(e.target.value)}
            />
            <span className="field-hint">Date when the damage, theft, return refusal, or delay occurred.</span>
          </div>

          <div className="form-group">
            <label className="field-label" htmlFor="incident-explanation">
              Incident Explanation & Supporting Notes
            </label>
            <textarea
              id="incident-explanation"
              className="text-input textarea-input"
              rows="3"
              value={incidentExplanation}
              onChange={(e) => setIncidentExplanation(e.target.value)}
              placeholder="Describe what occurred (e.g. Item dropped and cracked on flight; merchant denied return within policy window...)"
            />
            <span className="field-hint">Providing clear context speeds up automated and reviewer approvals.</span>
          </div>

          {/* ── Benefit Specific Info Details ── */}
          <div className="benefit-info-box">
            <h4>Coverage Verification</h4>
            <ul>
              {currentClaim.benefitType === 'PURCHASE_PROTECTION' && (
                <>
                  <li>Incident Type: <strong>Damage or Theft</strong></li>
                  <li>Coverage Window: <strong>90 days from purchase</strong></li>
                  <li>Per-Claim Cap: <strong>Up to {money(currentClaim.claimAmount, currentClaim.currency)}</strong></li>
                </>
              )}
              {currentClaim.benefitType === 'RETURN_PROTECTION' && (
                <>
                  <li>Reason: <strong>Merchant Refusal to Accept Return</strong></li>
                  <li>Return Window: <strong>{cd.returnWindowDays || '90'} days</strong></li>
                  <li>Condition: <strong>Item in like-new or undamaged condition</strong></li>
                </>
              )}
              {currentClaim.benefitType === 'TRAVEL_DELAY' && (
                <>
                  <li>Trigger: <strong>Delay exceeds {cd.minimumDelayHours || '6'} hours</strong></li>
                  <li>Covered Expenses: <strong>Essential meals, lodging & toiletries</strong></li>
                  <li>Provider: <strong>{cd.travelProvider || cd.merchantName}</strong></li>
                </>
              )}
            </ul>
          </div>

          {/* ── Actions ── */}
          <div className="wizard-actions">
            <button
              type="button"
              className="ghost-button"
              onClick={handleSaveDraft}
              disabled={saving || submitting || switchingBenefit}
            >
              {saving ? 'Saving draft…' : 'Save draft changes'}
            </button>
            <div className="wizard-submit-group">
              <button
                type="button"
                className="ghost-button"
                onClick={onClose}
                disabled={submitting}
              >
                Cancel
              </button>
              <button
                type="submit"
                className="primary-button inline"
                disabled={submitting || saving || switchingBenefit}
              >
                {submitting ? 'Submitting claim…' : 'Confirm & Submit Claim'}
              </button>
            </div>
          </div>
        </form>
      </div>
    </div>
  )
}
