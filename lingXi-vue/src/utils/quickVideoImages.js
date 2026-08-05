export const QUICK_VIDEO_MIN_IMAGE_WIDTH = 300;
export const QUICK_VIDEO_MIN_IMAGE_HEIGHT = 300;

export function getQuickVideoImageDimensionError(width, height, filename = '图片') {
  const normalizedWidth = Number(width);
  const normalizedHeight = Number(height);
  if (
    !Number.isFinite(normalizedWidth)
    || !Number.isFinite(normalizedHeight)
    || normalizedWidth <= 0
    || normalizedHeight <= 0
  ) {
    return `${filename}：无法读取图片尺寸，请重新选择 PNG 或 JPG`;
  }
  if (
    normalizedWidth < QUICK_VIDEO_MIN_IMAGE_WIDTH
    || normalizedHeight < QUICK_VIDEO_MIN_IMAGE_HEIGHT
  ) {
    return `${filename}：图片分辨率至少为${QUICK_VIDEO_MIN_IMAGE_WIDTH}×${QUICK_VIDEO_MIN_IMAGE_HEIGHT}，当前为${normalizedWidth}×${normalizedHeight}`;
  }
  return '';
}

export function readQuickVideoImageDimensions(file) {
  return new Promise((resolve, reject) => {
    const objectUrl = URL.createObjectURL(file);
    const image = new Image();
    const cleanup = () => URL.revokeObjectURL(objectUrl);
    image.onload = () => {
      const dimensions = {
        width: image.naturalWidth,
        height: image.naturalHeight
      };
      cleanup();
      resolve(dimensions);
    };
    image.onerror = () => {
      cleanup();
      reject(new Error('Unable to decode image dimensions'));
    };
    image.src = objectUrl;
  });
}
