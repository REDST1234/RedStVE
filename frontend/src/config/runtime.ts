const normalizeBool = (value: string | undefined): boolean => {
  if (!value) {
    return false
  }
  return value.trim().toLowerCase() === 'true'
}

export const isMockMode = normalizeBool(import.meta.env.VITE_MOCK_MODE)

const apiBase = import.meta.env.VITE_API_BASE || '/api'
const mockApiBase = import.meta.env.VITE_MOCK_API_BASE || '/mock-api'

// Unified API base selector. Business code should not hardcode "/api".
export const apiBaseUrl = isMockMode ? mockApiBase : apiBase
