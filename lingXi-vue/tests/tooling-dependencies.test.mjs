import assert from 'node:assert/strict';
import { createRequire } from 'node:module';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import test from 'node:test';
import beautify from 'js-beautify';
import autoImport from 'unplugin-auto-import/vite';

const require = createRequire(import.meta.url);
const projectDir = fileURLToPath(new URL('..', import.meta.url));

test('安全依赖升级后格式化工具仍支持文件匹配和 EditorConfig', () => {
  const beautifyRequire = createRequire(require.resolve('js-beautify'));
  const editorconfig = beautifyRequire('editorconfig');
  const files = beautifyRequire('glob').globSync('tests/*dependencies.test.mjs', { cwd: projectDir });
  assert.ok(files.some(file => path.resolve(projectDir, file) === fileURLToPath(import.meta.url)));
  const settings = editorconfig.parseFromFilesSync(path.join(projectDir, 'sample.js'), [{
    name: path.join(projectDir, '.editorconfig'),
    contents: Buffer.from('root=true\n[*.js]\nindent_style=space\nindent_size=2\n')
  }]);
  assert.equal(settings.indent_size, 2);
  assert.equal(beautify.js('let a={b:1}', settings), 'let a = {\n  b: 1\n}');
});

test('自动导入工具仍能使用安全版本的路径匹配接口', () => {
  const autoImportRequire = createRequire(require.resolve('unplugin-auto-import/vite'));
  const { minimatch } = autoImportRequire('minimatch');
  assert.equal(minimatch('src/views/chat.vue', 'src/**/*.vue'), true);
  assert.equal(minimatch('outside/chat.vue', 'src/**/*.vue'), false);
  assert.equal(autoImport({ imports: ['vue'], dts: false }).name, 'unplugin-auto-import');
});
