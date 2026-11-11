import { afterEach, expect, it, vi } from 'vitest'
import { getAppDeployUrlFromResponse, getAppPreviewUrl } from '../src/utils/appUrls'
afterEach(() => vi.unstubAllGlobals())
it('keeps deployment sharing URLs absolute when API requests use the same-origin proxy', () => {
  vi.stubGlobal('window', { location: { origin: 'https://app.example.com' } })
  expect(getAppDeployUrlFromResponse('http://localhost:8123/api/deploy/abc123/')).toBe(
    'https://app.example.com/api/deploy/abc123/',
  )
})
it('keeps preview routes under the API proxy for each generation type', () => {
  for (const type of ['html', 'multi_file', 'vue_project'])
    expect(getAppPreviewUrl('123', type)).toBe('/api/static/123/')
})
