import assert from 'node:assert/strict';
import test from 'node:test';

import { getQuickVideoImageDimensionError } from '../src/utils/quickVideoImages.js';

test('accepts the minimum quick-video image resolution', () => {
  assert.equal(getQuickVideoImageDimensionError(300, 300, 'frame.png'), '');
});

test('rejects an image when either dimension is below 300 pixels', () => {
  assert.equal(
    getQuickVideoImageDimensionError(240, 178, 'frame.png'),
    'frame.png：图片分辨率至少为300×300，当前为240×178'
  );
  assert.match(
    getQuickVideoImageDimensionError(299, 720, 'portrait.png'),
    /至少为300×300/
  );
});

test('rejects unreadable dimensions', () => {
  assert.equal(
    getQuickVideoImageDimensionError(undefined, 720, 'broken.png'),
    'broken.png：无法读取图片尺寸，请重新选择 PNG 或 JPG'
  );
});
