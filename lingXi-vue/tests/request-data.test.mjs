import assert from 'node:assert/strict';
import test from 'node:test';

import { normalizeMultipartRequest } from '../src/utils/request-data.js';

test('clears the global JSON content type for FormData', () => {
  const values = [];
  const config = {
    data: new FormData(),
    headers: {
      setContentType(value) {
        values.push(value);
      }
    }
  };

  assert.equal(normalizeMultipartRequest(config), true);
  assert.deepEqual(values, [undefined]);
});

test('removes plain-object content type headers for FormData', () => {
  const config = {
    data: new FormData(),
    headers: {
      'Content-Type': 'application/json;charset=utf-8',
      'content-type': 'application/json'
    }
  };

  assert.equal(normalizeMultipartRequest(config), true);
  assert.equal('Content-Type' in config.headers, false);
  assert.equal('content-type' in config.headers, false);
});

test('leaves JSON requests unchanged', () => {
  const config = {
    data: { message: 'hello' },
    headers: { 'Content-Type': 'application/json;charset=utf-8' }
  };

  assert.equal(normalizeMultipartRequest(config), false);
  assert.equal(
    config.headers['Content-Type'],
    'application/json;charset=utf-8'
  );
});
