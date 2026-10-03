// Thin wrapper around the backend API. The auth token is kept in localStorage
// and attached as a Bearer header on the scoped /api calls.

const TOKEN_KEY = 'bae.token'
const MEMBER_KEY = 'bae.cardMemberId'
const ROLE_KEY = 'bae.role'

export function getToken() {
  return localStorage.getItem(TOKEN_KEY)
}

export function getCardMemberId() {
  return localStorage.getItem(MEMBER_KEY)
}

export function getRole() {
  return localStorage.getItem(ROLE_KEY)
}

export function saveSession(token, cardMemberId, role) {
  localStorage.setItem(TOKEN_KEY, token)
  localStorage.setItem(MEMBER_KEY, cardMemberId)
  localStorage.setItem(ROLE_KEY, role || 'CARD_MEMBER')
}

export function clearSession() {
  localStorage.removeItem(TOKEN_KEY)
  localStorage.removeItem(MEMBER_KEY)
  localStorage.removeItem(ROLE_KEY)
}

async function readError(res) {
  try {
    const body = await res.json()
    return body.detail || body.title || `Request failed (${res.status})`
  } catch {
    return `Request failed (${res.status})`
  }
}

function authHeaders() {
  const token = getToken()
  return token ? { Authorization: `Bearer ${token}` } : {}
}

// Thrown when the session is missing/expired so the UI can bounce to login.
export class SessionExpiredError extends Error {}

async function guard(res) {
  if (res.status === 401 || res.status === 403) {
    throw new SessionExpiredError('Your session has expired. Please sign in again.')
  }
  if (!res.ok) throw new Error(await readError(res))
  return res.json()
}

export async function login(cardMemberId) {
  const res = await fetch('/api/auth/login', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ cardMemberId }),
  })
  if (!res.ok) throw new Error(await readError(res))
  return res.json()
}

// ---- Card member (customer) ----
export async function fetchMyClaims(status) {
  const url = status ? `/api/me/claims?status=${encodeURIComponent(status)}` : '/api/me/claims'
  const res = await fetch(url, { headers: authHeaders() })
  return guard(res)
}

export async function updateClaimDraft(id, draftData) {
  const res = await fetch(`/api/me/claims/${id}`, {
    method: 'PUT',
    headers: { ...authHeaders(), 'Content-Type': 'application/json' },
    body: JSON.stringify(draftData),
  })
  return guard(res)
}

export async function switchClaimBenefit(claimId, benefitId) {
  const res = await fetch(`/api/me/claims/${claimId}/switch-benefit?benefitId=${encodeURIComponent(benefitId)}`, {
    method: 'POST',
    headers: authHeaders(),
  })
  return guard(res)
}

export async function fetchMatchedBenefits(transactionId) {
  const res = await fetch(`/api/benefits/${transactionId}`, {
    headers: authHeaders(),
  })
  if (!res.ok) throw new Error(await readError(res))
  return res.json()
}

export async function submitClaim(id) {
  const res = await fetch(`/api/me/claims/${id}/submit`, {
    method: 'POST',
    headers: authHeaders(),
  })
  return guard(res)
}

export async function fetchClaimDocuments(claimId) {
  const res = await fetch(`/api/me/claims/${claimId}/documents`, { headers: authHeaders() })
  return guard(res)
}

export async function fetchClaimDocumentChecklist(claimId) {
  const res = await fetch(`/api/me/claims/${claimId}/documents/checklist`, { headers: authHeaders() })
  return guard(res)
}

export async function uploadClaimDocument(claimId, file, documentType) {
  const form = new FormData()
  form.append('file', file)
  form.append('documentType', documentType)

  const res = await fetch(`/api/me/claims/${claimId}/documents`, {
    method: 'POST',
    headers: authHeaders(),
    body: form,
  })
  return guard(res)
}

export async function deleteClaimDocument(claimId, documentId) {
  const res = await fetch(`/api/me/claims/${claimId}/documents/${documentId}`, {
    method: 'DELETE',
    headers: authHeaders(),
  })
  return guard(res)
}

export async function fetchClaimDocumentContent(claimId, documentId) {
  const res = await fetch(`/api/me/claims/${claimId}/documents/${documentId}/content`, { headers: authHeaders() })
  if (res.status === 401 || res.status === 403) throw new SessionExpiredError('Your session has expired. Please sign in again.')
  if (!res.ok) throw new Error(await readError(res))
  return res.blob()
}

// ---- Reviewer (admin) ----
export async function fetchAdminClaims(filters = {}) {
  const params = new URLSearchParams()
  for (const [k, v] of Object.entries(filters)) {
    if (v) params.append(k, v)
  }
  const qs = params.toString()
  const res = await fetch(`/api/admin/claims${qs ? `?${qs}` : ''}`, { headers: authHeaders() })
  return guard(res)
}

export async function decideClaim(id, decision, reason) {
  const res = await fetch(`/api/admin/claims/${id}/decision`, {
    method: 'POST',
    headers: { ...authHeaders(), 'Content-Type': 'application/json' },
    body: JSON.stringify({ decision, reason }),
  })
  return guard(res)
}

export async function fetchAdminClaimDocuments(claimId) {
  const res = await fetch(`/api/admin/claims/${claimId}/documents`, { headers: authHeaders() })
  return guard(res)
}

export async function fetchExpiringClaims(withinDays = 7) {
  const res = await fetch(`/api/admin/claims/expiring?withinDays=${encodeURIComponent(withinDays)}`, { headers: authHeaders() })
  return guard(res)
}

export async function fetchClaimAudit(claimId) {
  const res = await fetch(`/api/claims/${claimId}/audit`, { headers: authHeaders() })
  return guard(res)
}

export async function fetchAdminClaimDocumentContent(claimId, documentId) {
  const res = await fetch(`/api/admin/claims/${claimId}/documents/${documentId}/content`, { headers: authHeaders() })
  if (res.status === 401 || res.status === 403) throw new SessionExpiredError('Your session has expired. Please sign in again.')
  if (!res.ok) throw new Error(await readError(res))
  return res.blob()
}

export async function fetchMetrics() {
  const res = await fetch('/api/metrics', { headers: authHeaders() })
  if (!res.ok) throw new Error(await readError(res))
  return res.json()
}

export async function fetchTransactions(status) {
  const query = status ? `?status=${encodeURIComponent(status)}` : ''
  const res = await fetch(`/api/transactions${query}`, { headers: authHeaders() })
  return guard(res)
}
