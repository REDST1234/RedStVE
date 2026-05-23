import { apiBaseUrl } from '../config/runtime'

type QueryValue = string | number | boolean | null | undefined

interface RequestOptions extends Omit<RequestInit, 'body'> {
  query?: Record<string, QueryValue>
  body?: BodyInit | Record<string, unknown> | null
}

const buildUrl = (path: string, query?: Record<string, QueryValue>): string => {
  const normalizedPath = path.startsWith('/') ? path : `/${path}`
  const base = `${apiBaseUrl}${normalizedPath}`
  if (!query) {
    return base
  }

  const searchParams = new URLSearchParams()
  Object.entries(query).forEach(([key, value]) => {
    if (value === null || value === undefined) {
      return
    }
    searchParams.append(key, String(value))
  })

  const queryString = searchParams.toString()
  return queryString ? `${base}?${queryString}` : base
}

const normalizeBody = (
  body: RequestOptions['body'],
  headers: Headers
): BodyInit | null | undefined => {
  if (body === null || body === undefined) {
    return body
  }

  if (body instanceof FormData || body instanceof URLSearchParams || body instanceof Blob) {
    return body
  }

  if (typeof body === 'string') {
    return body
  }

  headers.set('Content-Type', 'application/json')
  return JSON.stringify(body)
}

export async function request<T>(path: string, options: RequestOptions = {}): Promise<T> {
  const { query, body, headers: customHeaders, ...rest } = options
  const headers = new Headers(customHeaders)
  const response = await fetch(buildUrl(path, query), {
    ...rest,
    headers,
    body: normalizeBody(body, headers),
  })

  if (!response.ok) {
    const errorText = await response.text()
    throw new Error(`HTTP ${response.status}: ${errorText}`)
  }

  return (await response.json()) as T
}
