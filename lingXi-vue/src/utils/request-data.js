/**
 * 识别 FormData 请求并移除全局 JSON Content-Type。
 * 浏览器必须自行写入带 boundary 的 multipart Content-Type。
 */
export function normalizeMultipartRequest(config) {
  const multipart = typeof FormData !== 'undefined'
    && config?.data instanceof FormData;
  if (!multipart) return false;

  const headers = config.headers;
  if (typeof headers?.setContentType === 'function') {
    headers.setContentType(undefined);
  } else if (headers) {
    delete headers['Content-Type'];
    delete headers['content-type'];
  }
  return true;
}
