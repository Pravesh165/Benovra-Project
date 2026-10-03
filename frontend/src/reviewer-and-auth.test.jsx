import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { act, cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react'
import App from './App.jsx'
import MemberApp from './MemberApp.jsx'
import ReviewerApp from './ReviewerApp.jsx'

const api = vi.hoisted(() => ({
  getToken: vi.fn(),
  getCardMemberId: vi.fn(),
  getRole: vi.fn(),
  saveSession: vi.fn(),
  clearSession: vi.fn(),
  login: vi.fn(),
  fetchMyClaims: vi.fn(),
  submitClaim: vi.fn(),
  fetchMatchedBenefits: vi.fn(),
  switchClaimBenefit: vi.fn(),
  updateClaimDraft: vi.fn(),
  fetchClaimDocuments: vi.fn(),
  fetchClaimDocumentChecklist: vi.fn(),
  uploadClaimDocument: vi.fn(),
  deleteClaimDocument: vi.fn(),
  fetchAdminClaims: vi.fn(),
  decideClaim: vi.fn(),
  fetchMetrics: vi.fn(),
  fetchAdminClaimDocuments: vi.fn(),
  fetchClaimAudit: vi.fn(),
  fetchAdminClaimDocumentContent: vi.fn(),
  fetchTransactions: vi.fn(),
  fetchExpiringClaims: vi.fn(),
}))

vi.mock('./api.js', () => ({
  ...api,
  SessionExpiredError: class SessionExpiredError extends Error {},
}))

const memberClaim = {
  id: 4,
  benefitName: 'Purchase Protection',
  benefitType: 'PURCHASE_PROTECTION',
  claimAmount: 125,
  currency: 'USD',
  status: 'PREFILLED',
  coverageExpiresOn: '2026-12-01',
  prefilledData: {
    itemDescription: 'Headphones',
    merchantName: 'Audio Shop',
    purchaseDate: '2026-09-01',
    purchaseAmount: '125.00',
  },
}

const reviewerClaim = {
  id: 18,
  status: 'UNDER_REVIEW',
  cardMemberId: 'CM-1001',
  cardProduct: 'PLATINUM',
  merchantName: 'Audio Shop',
  merchantCategory: 'ELECTRONICS',
  benefitType: 'PURCHASE_PROTECTION',
  benefitName: 'Purchase Protection',
  claimAmount: 125,
  purchaseAmount: 125,
  currency: 'USD',
}

beforeEach(() => {
  vi.resetAllMocks()
  localStorage.clear()
  api.getToken.mockReturnValue(null)
  api.getRole.mockReturnValue('CARD_MEMBER')
  api.getCardMemberId.mockReturnValue('CM-1001')
  api.fetchMyClaims.mockResolvedValue([])
  api.fetchAdminClaims.mockResolvedValue([])
  api.fetchMetrics.mockResolvedValue({ claimsByStatus: {} })
  api.fetchTransactions.mockResolvedValue([])
  api.fetchExpiringClaims.mockResolvedValue([])
  api.fetchAdminClaimDocuments.mockResolvedValue([])
  api.fetchClaimAudit.mockResolvedValue([])
})

afterEach(cleanup)

describe('authentication state', () => {
  it('logs in with normalized member ID and persists session', async () => {
    api.login.mockResolvedValue({ token: 'signed-token', cardMemberId: 'CM-1001', role: 'CARD_MEMBER' })
    api.fetchMyClaims.mockImplementation((status) => Promise.resolve(status ? [memberClaim] : [memberClaim]))
    render(<App />)

    fireEvent.change(screen.getByRole('textbox', { name: 'Card member ID' }), { target: { value: ' CM-1001 ' } })
    fireEvent.click(screen.getByRole('button', { name: 'Continue' }))

    expect(await screen.findByRole('heading', { name: 'Your protection benefits' })).toBeTruthy()
    expect(api.login).toHaveBeenCalledWith('CM-1001')
    expect(api.saveSession).toHaveBeenCalledWith('signed-token', 'CM-1001', 'CARD_MEMBER')
  })

  it('restores reviewer session and clears it on sign out', async () => {
    api.getToken.mockReturnValue('signed-token')
    api.getRole.mockReturnValue('REVIEWER')
    render(<App />)

    expect(await screen.findByRole('heading', { name: 'Claims awaiting review' })).toBeTruthy()
    fireEvent.click(screen.getByRole('button', { name: 'Sign out' }))

    expect(api.clearSession).toHaveBeenCalledOnce()
    expect(screen.getByRole('heading', { name: 'Sign in' })).toBeTruthy()
  })
})

describe('member tabs and reviewer queue', () => {
  it('moves member tabs with arrow keys and exposes selected panel', async () => {
    api.fetchMyClaims.mockImplementation((status) => Promise.resolve(
      status === 'PREFILLED' ? [memberClaim] : [{ ...memberClaim, id: 5, status: 'PAID' }],
    ))
    render(<MemberApp cardMemberId="CM-1001" onSignOut={vi.fn()} />)

    const todoTab = await screen.findByRole('tab', { name: /Claims to make/ })
    const submittedTab = screen.getByRole('tab', { name: /Submitted claims/ })
    expect(todoTab.getAttribute('aria-selected')).toBe('true')
    expect(todoTab.getAttribute('aria-controls')).toBe('member-panel-todo')

    todoTab.focus()
    fireEvent.keyDown(todoTab, { key: 'ArrowRight' })

    expect(submittedTab.getAttribute('aria-selected')).toBe('true')
    expect(document.activeElement).toBe(submittedTab)
    expect(screen.getByRole('tabpanel')).toBeTruthy()
  })

  it('shows loading and empty states in reviewer queue', async () => {
    let resolveClaims
    api.fetchAdminClaims.mockReturnValueOnce(new Promise((resolve) => { resolveClaims = resolve }))
    render(<ReviewerApp onSignOut={vi.fn()} />)
    expect(screen.getByText('Loading queue…')).toBeTruthy()

    await act(async () => resolveClaims([]))
    expect(await screen.findByText('Queue is clear')).toBeTruthy()
  })

  it('shows reviewer queue API errors', async () => {
    api.fetchAdminClaims.mockRejectedValueOnce(new Error('Queue unavailable'))
    render(<ReviewerApp onSignOut={vi.fn()} />)
    expect(await screen.findByText('Queue unavailable')).toBeTruthy()
  })

  it.each([
    ['Approve', 'APPROVE', 'PAID'],
    ['Reject', 'REJECT', 'REJECTED'],
  ])('records reviewer %s decision', async (label, decision, status) => {
    api.fetchAdminClaims.mockResolvedValue([reviewerClaim])
    api.decideClaim.mockResolvedValue({ ...reviewerClaim, status })
    render(<ReviewerApp onSignOut={vi.fn()} />)

    fireEvent.click(await screen.findByRole('button', { name: label }))

    await waitFor(() => expect(api.decideClaim).toHaveBeenCalledWith(reviewerClaim.id, decision, ''))
  })

  it('shows unmatched purchase explanation on dashboard', async () => {
    api.fetchMetrics.mockResolvedValue({ claimsByStatus: {}, totalTransactions: 1 })
    api.fetchTransactions.mockResolvedValue([{
      id: 91,
      merchantName: 'Audio Shop',
      cardProduct: 'BLACK',
      purchaseDate: '2026-09-01',
      amount: 125,
      currency: 'USD',
      matchReason: 'Card product BLACK has no active benefit entitlements.',
    }])
    api.fetchExpiringClaims.mockResolvedValue([])
    render(<ReviewerApp onSignOut={vi.fn()} />)

    fireEvent.click(screen.getByRole('tab', { name: 'Dashboard' }))

    expect(await screen.findByText('Card product BLACK has no active benefit entitlements.')).toBeTruthy()
    expect(api.fetchTransactions).toHaveBeenCalledWith('NO_MATCH')
  })

  it('surfaces claims nearing expiration on reviewer dashboard', async () => {
    api.fetchExpiringClaims.mockResolvedValue([{
      ...reviewerClaim,
      coverageExpiresOn: '2026-10-08',
      coverageDaysRemaining: 5,
    }])
    render(<ReviewerApp onSignOut={vi.fn()} />)

    fireEvent.click(screen.getByRole('tab', { name: 'Dashboard' }))

    expect(await screen.findByText('Claims expiring within 7 days')).toBeTruthy()
    expect(await screen.findByText('2026-10-08')).toBeTruthy()
    expect(screen.getByText('5')).toBeTruthy()
    expect(api.fetchExpiringClaims).toHaveBeenCalledWith(7)
  })

  it('moves reviewer tabs with arrow keys and reports selection to assistive technology', async () => {
    render(<ReviewerApp onSignOut={vi.fn()} />)
    const queueTab = await screen.findByRole('tab', { name: 'Review queue' })
    const searchTab = screen.getByRole('tab', { name: 'Search' })
    expect(queueTab.getAttribute('aria-controls')).toBe('reviewer-panel-queue')

    fireEvent.keyDown(queueTab, { key: 'ArrowRight' })

    expect(searchTab.getAttribute('aria-selected')).toBe('true')
    expect(document.activeElement).toBe(searchTab)
    expect(await screen.findByRole('heading', { name: 'Search claims' })).toBeTruthy()
  })
})
