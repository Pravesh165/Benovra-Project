import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react'
import PolicyKnowledgeBase from './PolicyKnowledgeBase.jsx'

const api = vi.hoisted(() => ({
  fetchPolicyKnowledge: vi.fn(),
}))

vi.mock('./api.js', () => api)

const mockPolicies = [
  {
    benefitId: 1,
    type: 'PURCHASE_PROTECTION',
    name: 'Purchase Protection',
    description: 'Covers accidental damage or theft within 90 days.',
    perClaimLimit: 1000.0,
    coverageWindowExplanation: '90 days from purchase date',
    userEligibilityExplanation: 'Item purchased fully on eligible card.',
    eligibleCards: ['PLATINUM', 'GOLD'],
    eligibleCategories: ['ELECTRONICS', 'MERCHANDISE'],
    requiredDocuments: ['Itemized Store Receipt', 'Repair Estimate or Proof of Loss'],
    exclusions: ['Motor vehicles', 'Perishable items', 'Pre-existing damage'],
  },
  {
    benefitId: 2,
    type: 'TRAVEL_DELAY',
    name: 'Travel Delay Insurance',
    description: 'Reimburses expenses when trip is delayed over 6 hours.',
    perClaimLimit: 500.0,
    coverageWindowExplanation: 'Delays exceeding 6 hours',
    userEligibilityExplanation: 'Trip paid with covered card.',
    eligibleCards: ['PLATINUM'],
    eligibleCategories: ['AIRLINE'],
    requiredDocuments: ['Common Carrier Ticket', 'Statement of Delay Reason'],
    exclusions: ['Known delays before departure', 'War or civil unrest'],
  },
]

beforeEach(() => {
  vi.resetAllMocks()
  api.fetchPolicyKnowledge.mockResolvedValue(mockPolicies)
})

afterEach(cleanup)

describe('PolicyKnowledgeBase', () => {
  it('renders policies and loads structured data', async () => {
    render(<PolicyKnowledgeBase />)

    expect(await screen.findByText('Purchase Protection')).toBeTruthy()
    expect(screen.getByText('Travel Delay Insurance')).toBeTruthy()
    expect(screen.getAllByText('Coverage Window').length).toBe(2)
    expect(screen.getByText('90 days from purchase date')).toBeTruthy()
    expect(api.fetchPolicyKnowledge).toHaveBeenCalledWith('', '')
  })

  it('filters by search input and card product', async () => {
    render(<PolicyKnowledgeBase />)

    await screen.findByText('Purchase Protection')

    const searchInput = screen.getByLabelText('Search policies')
    fireEvent.change(searchInput, { target: { value: 'delay' } })

    await waitFor(() => {
      expect(api.fetchPolicyKnowledge).toHaveBeenCalledWith('delay', '')
    })

    const cardSelect = screen.getByLabelText('Filter by card product')
    fireEvent.change(cardSelect, { target: { value: 'PLATINUM' } })

    await waitFor(() => {
      expect(api.fetchPolicyKnowledge).toHaveBeenCalledWith('delay', 'PLATINUM')
    })
  })

  it('expands and collapses policy rules and exclusions', async () => {
    render(<PolicyKnowledgeBase />)

    await screen.findByText('Purchase Protection')

    const toggleBtns = screen.getAllByRole('button', { name: /View exclusions & required documents/i })
    expect(toggleBtns.length).toBe(2)

    fireEvent.click(toggleBtns[0])

    expect(await screen.findByText(/Motor vehicles/i)).toBeTruthy()
    expect(screen.getByText(/Itemized Store Receipt/i)).toBeTruthy()

    // Collapse
    const hideBtn = screen.getByRole('button', { name: /Hide policy rules & exclusions/i })
    fireEvent.click(hideBtn)

    expect(screen.queryByText(/Motor vehicles/i)).toBeNull()
  })
})
