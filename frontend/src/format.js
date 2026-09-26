// Shared display helpers used across the customer and reviewer UIs.

export function statusLabel(status) {
  const labels = {
    PREFILLED: 'Ready to submit',
    SUBMITTED: 'Submitted',
    UNDER_REVIEW: 'Under review',
    APPROVED: 'Approved',
    REJECTED: 'Rejected',
    PAID: 'Processed',
  }
  return labels[status] || status
}

export function statusBadgeClass(status) {
  return {
    SUBMITTED: 'badge badge-blue',
    UNDER_REVIEW: 'badge badge-amber',
    APPROVED: 'badge badge-green',
    PAID: 'badge badge-green',
    REJECTED: 'badge badge-red',
    PREFILLED: 'badge badge-grey',
  }[status] || 'badge badge-grey'
}

export function money(amount, currency) {
  if (amount == null) return '—'
  const n = Number(amount)
  const value = Number.isNaN(n)
    ? amount
    : n.toLocaleString(undefined, { minimumFractionDigits: 2, maximumFractionDigits: 2 })
  return `${currency ? currency + ' ' : ''}${value}`
}
