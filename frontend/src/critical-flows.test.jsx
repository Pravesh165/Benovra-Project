import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react'
import Login from './Login.jsx'
import ClaimReviewWizard from './ClaimReviewWizard.jsx'
import ClaimCard from './ClaimCard.jsx'

const api = vi.hoisted(() => ({
  fetchMatchedBenefits: vi.fn(),
  switchClaimBenefit: vi.fn(),
  updateClaimDraft: vi.fn(),
  submitClaim: vi.fn(),
  fetchClaimDocuments: vi.fn(),
  fetchClaimDocumentChecklist: vi.fn(),
  uploadClaimDocument: vi.fn(),
  deleteClaimDocument: vi.fn(),
}))

vi.mock('./api.js', () => api)

const claim = {
  id: 42,
  transactionId: 7,
  benefitId: 3,
  benefitType: 'PURCHASE_PROTECTION',
  benefitName: 'Purchase Protection',
  claimAmount: 120,
  currency: 'USD',
  prefilledData: {
    itemDescription: 'Headphones',
    purchaseDate: '2026-09-01',
    merchantName: 'Audio Shop',
    purchaseAmount: '120.00',
  },
}

const checklist = [
  { documentType: 'PURCHASE_RECEIPT', required: true, uploaded: false },
  { documentType: 'PROOF_OF_LOSS', required: true, uploaded: false },
  { documentType: 'POLICE_REPORT', required: false, uploaded: false },
]

function renderWizard(overrides = {}) {
  return render(
    <ClaimReviewWizard claim={claim} onClose={vi.fn()} onSubmitted={vi.fn()} {...overrides} />
  )
}

beforeEach(() => {
  vi.resetAllMocks()
  api.fetchMatchedBenefits.mockResolvedValue([])
  api.fetchClaimDocuments.mockResolvedValue([])
  api.fetchClaimDocumentChecklist.mockResolvedValue(checklist)
  api.updateClaimDraft.mockResolvedValue(claim)
  api.submitClaim.mockResolvedValue({ ...claim, status: 'PAID' })
})

afterEach(cleanup)

describe('critical member flows', () => {
  it('requires a member ID before login', async () => {
    const onLogin = vi.fn()
    render(<Login onLogin={onLogin} />)

    fireEvent.click(screen.getByRole('button', { name: 'Continue' }))

    expect(await screen.findByText('Please enter your card member ID.')).toBeTruthy()
    expect(onLogin).not.toHaveBeenCalled()
  })

  it('exposes claim review as a labeled dialog and closes on Escape', async () => {
    const onClose = vi.fn()
    renderWizard({ onClose })

    expect(await screen.findByRole('dialog', { name: 'Review & Submit Claim' })).toBeTruthy()
    fireEvent.keyDown(document, { key: 'Escape' })

    expect(onClose).toHaveBeenCalledOnce()
  })

  it('traps keyboard focus in claim dialog and restores trigger focus on close', async () => {
    const trigger = document.createElement('button')
    document.body.append(trigger)
    trigger.focus()
    const { unmount } = renderWizard()

    await screen.findByRole('dialog', { name: 'Review & Submit Claim' })
    const closeButton = screen.getByRole('button', { name: 'Close modal' })
    expect(document.activeElement).toBe(closeButton)
    fireEvent.keyDown(document, { key: 'Tab', shiftKey: true })
    expect(document.activeElement).toBe(screen.getByRole('button', { name: 'Continue' }))
    fireEvent.keyDown(document, { key: 'Tab' })
    expect(document.activeElement).toBe(closeButton)

    unmount()
    expect(document.activeElement).toBe(trigger)
    trigger.remove()
  })

  it('switches eligible benefits and displays selected benefit explanation', async () => {
    const returnClaim = {
      ...claim,
      benefitId: 4,
      benefitType: 'RETURN_PROTECTION',
      benefitName: 'Return Protection',
    }
    api.fetchMatchedBenefits.mockResolvedValue([
      { benefitId: 3, name: 'Purchase Protection', reason: 'Covered electronics purchase' },
      { benefitId: 4, name: 'Return Protection', reason: 'Merchant refused eligible return' },
    ])
    api.switchClaimBenefit.mockResolvedValue(returnClaim)
    renderWizard()

    fireEvent.click(await screen.findByRole('button', { name: /Return Protection/ }))

    expect(await screen.findByText('Switched to Return Protection. Details recalculated.')).toBeTruthy()
    expect((await screen.findByRole('note')).textContent).toContain('Merchant refused eligible return')
    expect(api.switchClaimBenefit).toHaveBeenCalledWith(claim.id, 4)
  })

  it('rejects oversized files and uploads accepted files', async () => {
    renderWizard()
    fireEvent.click(await screen.findByRole('button', { name: 'Continue' }))
    await screen.findByText('Supporting document checklist')
    const fileInput = screen.getByLabelText('Upload')
    const oversized = new File([new Uint8Array((5 * 1024 * 1024) + 1)], 'large.pdf', { type: 'application/pdf' })
    fireEvent.change(fileInput, { target: { files: [oversized] } })

    expect(await screen.findByText('Document exceeds the 5 MB limit.')).toBeTruthy()
    expect(api.uploadClaimDocument).not.toHaveBeenCalled()

    const valid = new File(['pdf'], 'receipt.pdf', { type: 'application/pdf' })
    fireEvent.change(fileInput, { target: { files: [valid] } })
    await waitFor(() => expect(api.uploadClaimDocument).toHaveBeenCalledWith(claim.id, valid, 'PURCHASE_RECEIPT'))
  })

  it('blocks final submission until required documents are uploaded', async () => {
    renderWizard()

    await screen.findByText('Purchase Protection')
    fireEvent.click(screen.getByRole('button', { name: 'Continue' }))
    await screen.findByText('Supporting document checklist')
    fireEvent.click(screen.getByRole('button', { name: 'Continue' }))

    expect(await screen.findByText(/Required documents missing/)).toBeTruthy()
    expect(screen.getByRole('button', { name: 'Confirm & Submit Claim' }).disabled).toBe(true)
    expect(api.submitClaim).not.toHaveBeenCalled()
  })

  it('saves draft and submits when required documents are uploaded', async () => {
    const completeChecklist = checklist.map((item) => ({ ...item, uploaded: item.required }))
    const onSubmitted = vi.fn()
    api.fetchClaimDocuments.mockResolvedValue([
      { id: 10, documentType: 'PURCHASE_RECEIPT', originalFilename: 'receipt.pdf' },
      { id: 11, documentType: 'PROOF_OF_LOSS', originalFilename: 'damage.pdf' },
    ])
    api.fetchClaimDocumentChecklist.mockResolvedValue(completeChecklist)
    renderWizard({ onSubmitted })

    await screen.findByText('Purchase Protection')
    fireEvent.click(screen.getByRole('button', { name: 'Continue' }))
    await screen.findByText('Supporting document checklist')
    await waitFor(() => expect(screen.getByRole('button', { name: 'Continue' }).disabled).toBe(false))
    fireEvent.click(screen.getByRole('button', { name: 'Continue' }))
    fireEvent.click(screen.getByRole('button', { name: 'Confirm & Submit Claim' }))

    await waitFor(() => expect(api.submitClaim).toHaveBeenCalledWith(claim.id))
    expect(api.updateClaimDraft).toHaveBeenCalledWith(claim.id, expect.objectContaining({
      itemDescription: 'Headphones',
    }))
    expect(onSubmitted).toHaveBeenCalledWith(claim)
  })

  it('shows expired coverage deadline and disables draft submission', () => {
    render(<ClaimCard claim={{ ...claim, coverageExpiresOn: '2020-01-01' }} showSubmit onSubmit={vi.fn()} />)

    expect(screen.getByText('Coverage expired on 2020-01-01. This claim cannot be submitted.')).toBeTruthy()
    expect(screen.getByRole('button', { name: 'Coverage expired' }).disabled).toBe(true)
  })
})
