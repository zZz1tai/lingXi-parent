import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import test from 'node:test';
import { runInNewContext } from 'node:vm';
import { createPinia, defineStore } from 'pinia';
import { h } from 'vue';
import { normalizeMultipartRequest } from '../src/utils/request-data.js';

// 隔离网络和浏览器依赖，直接执行已有模块逻辑，无需前端构建。
async function moduleSource(path) {
  return (await readFile(new URL(path, import.meta.url), 'utf8'))
    .replace(/^import .*$/gm, '')
    .replace(/^export default /gm, '')
    .replace(/^export /gm, '')
    .replace(/import\.meta\.env\.VITE_APP_BASE_API/g, "'/dev-api'")
    .replace(/import\.meta\.glob\([^\n]*\)/g, '{}');
}

test('路由接口或解析失败会拒绝 Promise，避免导航一直等待', async () => {
  const source = await moduleSource('../src/store/modules/permission.js');
  for (const getRouters of [
    () => Promise.reject(new Error('路由接口不可用')),
    () => Promise.resolve({ data: null })
  ]) {
    const useStore = runInNewContext(source, { defineStore, getRouters });
    await assert.rejects(useStore(createPinia()).generateRoutes());
  }
});

test('服务端登出失败仍清理本地令牌和上一个用户信息', async () => {
  let removed = false;
  const source = await moduleSource('../src/store/modules/user.js');
  const useStore = runInNewContext(source, {
    defineStore,
    getToken: () => 'old-token',
    logout: () => Promise.reject(new Error('网络中断')),
    removeToken: () => { removed = true; }
  });
  const store = useStore(createPinia());
  Object.assign(store, { id: 42, name: '旧用户', avatar: '/old.png', roles: ['admin'], permissions: ['*:*:*'] });
  await store.logOut();
  assert.equal(removed, true);
  for (const key of ['token', 'id', 'name', 'avatar']) assert.equal(store[key], '');
  assert.equal(store.roles.length, 0);
  assert.equal(store.permissions.length, 0);
});

test('动态路由加载失败会结束登录等待并返回保留完整地址的登录页', { timeout: 1000 }, async () => {
  let guard;
  let loggedOut = false;
  const relogin = { show: false };
  const store = {
    roles: [],
    getInfo: () => Promise.resolve(),
    logOut: () => { loggedOut = true; return Promise.resolve(); }
  };
  runInNewContext(await moduleSource('../src/permission.js'), {
    router: { beforeEach: fn => { guard = fn; }, afterEach() {} },
    NProgress: { configure() {}, start() {}, done() {} },
    getToken: () => 'expired-token',
    useUserStore: () => store,
    useSettingsStore: () => ({ setTitle() {} }),
    usePermissionStore: () => ({ generateRoutes: () => Promise.reject(new Error('菜单加载失败')) }),
    isRelogin: relogin,
    ElMessage: { error() {} }
  });
  const fullPath = '/ai/chat?session=abc&mode=history#latest';
  const destination = await new Promise(resolve => guard({ path: '/ai/chat', fullPath, meta: {} }, {}, resolve));
  assert.equal(loggedOut, true);
  assert.equal(relogin.show, false);
  assert.equal(destination.path, '/login');
  assert.equal(destination.query.redirect, fullPath);
});

test('防重请求不持久化敏感正文，浏览器禁用存储时仍可提交并阻止重复提交', async () => {
  let requestHandler;
  let requestErrorHandler;
  let removed = false;
  const service = {
    interceptors: {
      request: { use(fn, reject) { requestHandler = fn; requestErrorHandler = reject; } },
      response: { use() {} }
    }
  };
  runInNewContext(await moduleSource('../src/utils/request.js'), {
    axios: { defaults: { headers: {} }, create: () => service },
    sessionStorage: { removeItem(key) { removed = key === 'sessionObj'; throw new Error('禁用存储'); } },
    getToken: () => '',
    normalizeMultipartRequest,
    console: { log() {}, warn() {} }
  });
  assert.equal(removed, true);
  const config = { method: 'post', url: '/register', headers: {}, data: { password: '仅测试用值' } };
  assert.equal(requestHandler(config), config);
  await assert.rejects(requestHandler({ ...config }), /请勿重复提交/);
  const next = { ...config, data: { password: '另一个测试值' } };
  assert.equal(requestHandler(next), next);
  await assert.rejects(requestErrorHandler(new Error('请求准备失败')), /请求准备失败/);
});

test('用户导入反馈仅把换行标签转换为换行，其他内容作为文本节点', async () => {
  const source = await readFile(new URL('../src/views/system/user/index.vue', import.meta.url), 'utf8');
  let message;
  const handle = runInNewContext(
    source.slice(source.indexOf('const handleFileSuccess'), source.indexOf('/** 提交上传文件 */')) + '\nhandleFileSuccess;',
    {
      h,
      upload: {},
      proxy: { $refs: { uploadRef: { handleRemove() {} } }, $alert: value => { message = value; } },
      getList() {}
    }
  );
  handle({ msg: '成功<br/><img src=x onerror=alert(1)>' }, {});
  assert.equal(message.type, 'div');
  assert.equal(message.children, '成功\n<img src=x onerror=alert(1)>');
  assert.equal(message.props.innerHTML, undefined);
});
