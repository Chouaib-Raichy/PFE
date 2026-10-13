import { test } from 'node:test'
import assert from 'node:assert/strict'
import { formatDate, formatDateTime } from '../src/lib/format.ts'

const MISSING: (string | null | undefined)[] = [null, undefined, '', 'not-a-date']

test('formatDate: date absente ou invalide -> « Date indisponible », jamais 01 janv. 1970', () => {
  for (const value of MISSING) {
    assert.equal(formatDate(value), 'Date indisponible')
  }
})

test('formatDateTime: date absente ou invalide -> « Date indisponible », jamais 01 janv. 1970', () => {
  for (const value of MISSING) {
    assert.equal(formatDateTime(value), 'Date indisponible')
  }
})

test('formatDateTime: un horodatage ISO valide est formaté avec son année', () => {
  assert.match(formatDateTime('2026-10-12T15:30:00'), /2026/)
})
