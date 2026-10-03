import { useState, useEffect } from 'react'
import { money } from './format.js'
import { useModalFocus } from './useModalFocus.js'
import {
  fetchMatchedBenefits,
  switchClaimBenefit,
  updateClaimDraft,
  submitClaim,
  fetchClaimDocuments,
  fetchClaimDocumentChecklist,
  uploadClaimDocument,
  deleteClaimDocument,
} from './api.js'

const DOC_LABELS = {
  PURCHASE_RECEIPT: 'Purchase receipt',
  PROOF_OF_LOSS: 'Proof of loss / incident photograph',
  MERCHANT_RETURN_DENIAL: 'Merchant return denial',
  AIRLINE_BOOKING_PROOF: 'Flight booking proof',
  AIRLINE_DELAY_CERTIFICATE: 'Delay certificate / airline note',
  POLICE_REPORT: 'Police report',
  OTHER_SUPPORTING: 'Additional supporting documentation',
}
const MAX_DOCUMENT_SIZE = 5 * 1024 * 1024

export default function ClaimReviewWizard({ claim, onClose, onSubmitted }) {
  const dialogRef = useModalFocus(onClose)
  const [matchedBenefits, setMatchedBenefits] = useState([])
  const [currentClaim, setCurrentClaim] = useState(claim)
  const [switchingBenefit, setSwitchingBenefit] = useState(false)
  const [documents, setDocuments] = useState([])
  const [documentChecklist, setDocumentChecklist] = useState([])
  const [documentsLoading, setDocumentsLoading] = useState(true)
  const [documentsReady, setDocumentsReady] = useState(false)
  const [selectedDocType, setSelectedDocType] = useState('')
  const [step, setStep] = useState(1)
  const [uploading, setUploading] = useState(false)
  const [deletingDocId, setDeletingDocId] = useState(null)

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

  const missingRequired = documentChecklist.filter((item) => item.required && !item.uploaded)
  const canSubmit = documentsReady && !documentsLoading && documentChecklist.length > 0 && missingRequired.length === 0
  const selectedBenefit = matchedBenefits.find((benefit) => benefit.benefitId === currentClaim.benefitId)

  useEffect(() => {
    if (claim.transactionId) {
      fetchMatchedBenefits(claim.transactionId)
        .then((benefits) => setMatchedBenefits(benefits || []))
        .catch(() => setMatchedBenefits([]))
    }
  }, [claim.transactionId])

  useEffect(() => {
    let active = true
    setDocumentsLoading(true)
    setDocumentsReady(false)
    setError('')
    Promise.all([
      fetchClaimDocuments(currentClaim.id),
      fetchClaimDocumentChecklist(currentClaim.id),
    ])
      .then(([items, checklist]) => {
        if (!active) return
        setDocuments(items || [])
        setDocumentChecklist(checklist || [])
        setDocumentsReady(true)
      })
      .catch((err) => {
        if (!active) return
        setError(err.message || 'Failed to load supporting documents')
        setDocuments([])
        setDocumentChecklist([])
      })
      .finally(() => {
        if (active) setDocumentsLoading(false)
      })
    return () => { active = false }
  }, [currentClaim.id, currentClaim.benefitType])

  useEffect(() => {
    if (documentChecklist.length && !documentChecklist.some((item) => item.documentType === selectedDocType)) {
      setSelectedDocType(documentChecklist[0].documentType)
    }
  }, [documentChecklist, selectedDocType])

  useEffect(() => {
    const cd = currentClaim.prefilledData || {}
    setItemDescription(cd.itemDescription || currentClaim.benefitName || '')
    setIncidentDate(cd.incidentDate || cd.purchaseDate || '')
    setIncidentExplanation(cd.incidentExplanation || '')
  }, [currentClaim])

  async function loadDocuments() {
    if (!currentClaim.id) return
    setDocumentsLoading(true)
    setDocumentsReady(false)
    try {
      const [items, checklist] = await Promise.all([
        fetchClaimDocuments(currentClaim.id),
        fetchClaimDocumentChecklist(currentClaim.id),
      ])
      setDocuments(items || [])
      setDocumentChecklist(checklist || [])
      setDocumentsReady(true)
    } catch (err) {
      setError(err.message || 'Failed to load supporting documents')
      throw err
    } finally {
      setDocumentsLoading(false)
    }
  }

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

  async function handleUpload(file) {
    if (!file || !selectedDocType) {
      setError('Choose a document type before uploading.')
      return
    }
    if (file.size > MAX_DOCUMENT_SIZE) {
      setError('Document exceeds the 5 MB limit.')
      return
    }
    setUploading(true)
    setError('')
    setSuccessMsg('')
    try {
      await uploadClaimDocument(currentClaim.id, file, selectedDocType)
      setSuccessMsg(`${DOC_LABELS[selectedDocType]} uploaded.`)
      await loadDocuments()
    } catch (err) {
      setError(err.message || 'Failed to upload document')
    } finally {
      setUploading(false)
    }
  }

  async function handleDeleteDocument(documentId) {
    setDeletingDocId(documentId)
    setError('')
    try {
      await deleteClaimDocument(currentClaim.id, documentId)
      await loadDocuments()
      setSuccessMsg('Document removed.')
    } catch (err) {
      setError(err.message || 'Failed to remove document')
    } finally {
      setDeletingDocId(null)
    }
  }

  async function handleFinalSubmit(e) {
    e.preventDefault()
    if (!documentsReady || documentsLoading) {
      setError('Supporting document checklist is not ready yet.')
      return
    }
    if (missingRequired.length > 0) {
      setError(`Missing required supporting documents: ${missingRequired.map((item) => DOC_LABELS[item.documentType]).join(', ')}`)
      return
    }

    setSubmitting(true)
    setError('')
    try {
      const updated = await updateClaimDraft(currentClaim.id, {
        itemDescription: itemDescription.trim(),
        incidentDate: incidentDate.trim(),
        incidentExplanation: incidentExplanation.trim(),
      })
      const result = await submitClaim(currentClaim.id)
      await onSubmitted?.(updated || result)
    } catch (err) {
      setError(err.message || 'Failed to submit claim')
    } finally {
      setSubmitting(false)
    }
  }

  const cd = currentClaim.prefilledData || {}

  return (
    <div className="modal-backdrop">
      <dialog open ref={dialogRef} className="modal-card" aria-modal="true" aria-labelledby="claim-review-title" tabIndex={-1}>
        <div className="modal-head">
          <div>
            <span className="benefit-tag">{currentClaim.benefitName}</span>
            <h2 className="modal-title" id="claim-review-title">Review & Submit Claim</h2>
            <p className="modal-subtitle">
              Verify your claim, upload required proof, and submit only once the supporting documents are complete.
            </p>
          </div>
          <button className="close-btn" onClick={onClose} aria-label="Close modal">×</button>
        </div>

        {error && <div className="banner banner-error">{error}</div>}
        {successMsg && <div className="banner banner-success">{successMsg}</div>}

        <nav className="wizard-steps" aria-label="Claim submission steps">
          {['Review & Edit', 'Supporting Documents', 'Final Review'].map((label, index) => (
            <div key={label} className={`wizard-step ${step === index + 1 ? 'active' : ''} ${step > index + 1 ? 'complete' : ''}`}>
              <span>{index + 1}</span>{label}
            </div>
          ))}
        </nav>

        <form onSubmit={handleFinalSubmit} className="wizard-form">
          {step === 1 && <>
        {matchedBenefits.length > 1 && (
          <div className="benefit-selector-section">
            <label className="field-label">Eligible Benefits ({matchedBenefits.length} detected for this purchase)</label>
            <p className="field-hint">This purchase qualifies for multiple card benefits. Select which protection you want to claim:</p>
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
                    <div className="option-amount">Up to {money(b.estimatedClaimAmount, currentClaim.currency)}</div>
                    <p className="option-reason">{b.reason}</p>
                  </button>
                )
              })}
            </div>
          </div>
        )}

        {selectedBenefit?.reason && <p className="claim-reason" role="note">{selectedBenefit.reason}</p>}

          <div className="wizard-summary-box">
            <div className="summary-item"><span className="summary-label">Merchant</span><span className="summary-value">{cd.merchantName || '—'}</span></div>
            <div className="summary-item"><span className="summary-label">Purchase Date</span><span className="summary-value">{cd.purchaseDate || '—'}</span></div>
            <div className="summary-item"><span className="summary-label">Purchase Amount</span><span className="summary-value">{money(cd.purchaseAmount, currentClaim.currency)}</span></div>
            <div className="summary-item highlight"><span className="summary-label">Claiming Amount</span><span className="summary-value claim-amount">{money(currentClaim.claimAmount, currentClaim.currency)}</span></div>
          </div>

          <div className="form-group">
            <label className="field-label" htmlFor="item-desc">Item / Protection Description *</label>
            <input id="item-desc" type="text" className="text-input" value={itemDescription} onChange={(e) => setItemDescription(e.target.value)} placeholder="e.g. MacBook Pro 14 or Wireless Headphones" required />
            <span className="field-hint">Specify the exact item or service covered by this benefit.</span>
          </div>

          <div className="form-group">
            <label className="field-label" htmlFor="incident-date">Incident / Claim Date</label>
            <input id="incident-date" type="date" className="text-input" value={incidentDate} onChange={(e) => setIncidentDate(e.target.value)} />
            <span className="field-hint">Date the damage, theft, return refusal, or travel delay occurred.</span>
          </div>

          <div className="form-group">
            <label className="field-label" htmlFor="incident-explanation">Incident Explanation & Supporting Notes</label>
            <textarea id="incident-explanation" className="text-input textarea-input" rows="3" value={incidentExplanation} onChange={(e) => setIncidentExplanation(e.target.value)} placeholder="Describe what occurred..." />
            <span className="field-hint">Clear context helps the reviewer assess the case quickly.</span>
          </div>

          </>}

          {step === 2 && <div className="benefit-info-box document-step">
            <h4>Supporting document checklist</h4>
            {documentsLoading ? <p className="muted">Loading document checklist…</p> : <>
              {!documentsReady && <button type="button" className="ghost-button tiny" onClick={loadDocuments}>Retry checklist</button>}
              <div className="doc-upload-row">
                <select className="text-input" value={selectedDocType} onChange={(e) => setSelectedDocType(e.target.value)}>
                {documentChecklist.map(({ documentType }) => (
                  <option key={documentType} value={documentType}>{DOC_LABELS[documentType]}</option>
                ))}
              </select>
              <label className="upload-button">
                <input type="file" accept=".pdf,image/png,image/jpeg,image/webp,image/gif" onChange={(e) => handleUpload(e.target.files?.[0])} disabled={uploading} />
                {uploading ? 'Uploading…' : 'Upload'}
              </label>
            </div>

            <ul className="doc-checklist">
              {documentChecklist.map(({ documentType, required, uploaded: isUploaded }) => {
                const uploaded = documents.find((doc) => doc.documentType === documentType)
                return (
                  <li key={documentType} className={`doc-row ${isUploaded ? 'uploaded' : 'missing'}`}>
                    <div>
                      <strong>{DOC_LABELS[documentType]}</strong>
                      <span>{required ? 'Required' : 'Optional'}</span>
                    </div>
                    <div className="doc-row-status">
                      {isUploaded && uploaded ? (
                        <>
                          <span>{uploaded.originalFilename}</span>
                          <button type="button" className="ghost-button danger tiny" onClick={() => handleDeleteDocument(uploaded.id)} disabled={deletingDocId === uploaded.id}>
                            {deletingDocId === uploaded.id ? 'Removing…' : 'Remove'}
                          </button>
                        </>
                      ) : (
                        <span className="muted">Missing</span>
                      )}
                    </div>
                  </li>
                )
              })}
            </ul>
            </>}
          </div>}

          {step === 3 && <section className="final-review">
            <h3>Claim summary</h3>
            <div className="wizard-summary-box">
              <div className="summary-item"><span className="summary-label">Benefit</span><span className="summary-value">{currentClaim.benefitName}</span></div>
              <div className="summary-item"><span className="summary-label">Item</span><span className="summary-value">{itemDescription || '—'}</span></div>
              <div className="summary-item"><span className="summary-label">Merchant</span><span className="summary-value">{cd.merchantName || '—'}</span></div>
              <div className="summary-item"><span className="summary-label">Purchase date</span><span className="summary-value">{cd.purchaseDate || '—'}</span></div>
              <div className="summary-item"><span className="summary-label">Incident date</span><span className="summary-value">{incidentDate || '—'}</span></div>
              <div className="summary-item"><span className="summary-label">Incident details</span><span className="summary-value">{incidentExplanation || '—'}</span></div>
              <div className="summary-item highlight"><span className="summary-label">Claim amount</span><span className="summary-value claim-amount">{money(currentClaim.claimAmount, currentClaim.currency)}</span></div>
            </div>
            <h3>Document status</h3>
            {documentsLoading ? <p className="muted">Loading document status…</p> : (
              <ul className="doc-checklist">
                {documentChecklist.map(({ documentType, required, uploaded: isUploaded }) => (
                  <li key={documentType} className={`doc-row ${isUploaded ? 'uploaded' : 'missing'}`}>
                    <div><strong>{DOC_LABELS[documentType]}</strong><span>{required ? 'Required' : 'Optional'}</span></div>
                    <span>{isUploaded ? 'Uploaded' : 'Missing'}</span>
                  </li>
                ))}
              </ul>
            )}
            {!documentsLoading && missingRequired.length > 0 && <p className="field-hint">Required documents missing: {missingRequired.map((item) => DOC_LABELS[item.documentType]).join(', ')}</p>}
          </section>}

          <div className="wizard-actions">
            {step === 1 ? <button type="button" className="ghost-button" onClick={handleSaveDraft} disabled={saving || submitting || switchingBenefit}>{saving ? 'Saving draft…' : 'Save draft changes'}</button> : <button type="button" className="ghost-button" onClick={() => setStep((current) => Math.max(1, current - 1))} disabled={submitting}>Back</button>}
            <div className="wizard-submit-group">
              <button type="button" className="ghost-button" onClick={onClose} disabled={submitting}>{step === 1 ? 'Cancel' : 'Close'}</button>
              {step < 3 ? <button type="button" className="primary-button inline" onClick={() => setStep((current) => current + 1)} disabled={switchingBenefit || (step === 2 && !documentsReady)}>
                Continue
              </button> : <button type="submit" className="primary-button inline" disabled={submitting || saving || switchingBenefit || !canSubmit}>
                {submitting ? 'Submitting claim…' : 'Confirm & Submit Claim'}
              </button>}
            </div>
          </div>
        </form>
      </dialog>
    </div>
  )
}
