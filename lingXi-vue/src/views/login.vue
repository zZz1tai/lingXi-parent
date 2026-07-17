<template>
  <div class="login">
    <section class="login-brand" aria-label="灵犀终端管理平台介绍">
      <span class="brand-mark">LINGXI · OPERATIONS</span>
      <h1>让终端、销售与任务<br>始终保持同一节奏</h1>
      <p>面向渠道运营团队的一体化数据工作台，快速掌握设备状态、销售趋势和执行进度。</p>
      <div class="brand-metrics"><span><strong>实时</strong>业务洞察</span><span><strong>统一</strong>终端管理</span><span><strong>智能</strong>任务协同</span></div>
    </section>
    <el-form ref="loginRef" :model="loginForm" :rules="loginRules" class="login-form">
      <span class="form-eyebrow">欢迎回来</span>
      <h3 class="title">登录灵犀工作台</h3>
      <p class="form-subtitle">请输入账户信息继续</p>
      <el-form-item prop="username">
        <el-input v-model="loginForm.username" type="text" size="large" auto-complete="off" placeholder="账号">
          <template #prefix><svg-icon icon-class="user" class="el-input__icon input-icon" /></template>
        </el-input>
      </el-form-item>
      <el-form-item prop="password">
        <el-input v-model="loginForm.password" type="password" size="large" auto-complete="off" placeholder="密码"
          @keyup.enter="handleLogin">
          <template #prefix><svg-icon icon-class="password" class="el-input__icon input-icon" /></template>
        </el-input>
      </el-form-item>
      <el-form-item prop="code" v-if="captchaEnabled">
        <el-input v-model="loginForm.code" size="large" auto-complete="off" placeholder="验证码" style="width: 63%"
          @keyup.enter="handleLogin">
          <template #prefix><svg-icon icon-class="validCode" class="el-input__icon input-icon" /></template>
        </el-input>
        <div class="login-code">
          <img :src="codeUrl" @click="getCode" class="login-code-img" />
        </div>
      </el-form-item>
      <el-checkbox v-model="loginForm.rememberMe" style="margin:0px 0px 25px 0px;">记住密码</el-checkbox>
      <el-form-item style="width:100%;">
        <el-button :loading="loading" size="large" type="primary" style="width:100%;" @click.prevent="handleLogin">
          <span v-if="!loading">登 录</span>
          <span v-else>登 录 中...</span>
        </el-button>
        <div style="float: right;" v-if="register">
          <router-link class="link-type" :to="'/register'">立即注册</router-link>
        </div>
      </el-form-item>
    </el-form>
    <!--  底部  -->
    <div class="el-login-footer">
      <span>Copyright © 2025 LingXi Sell All Rights Reserved.</span>
    </div>
  </div>
</template>

<script setup>
import { getCodeImg } from "@/api/login";
import Cookies from "js-cookie";
import { encrypt, decrypt } from "@/utils/jsencrypt";
import useUserStore from '@/store/modules/user'

const userStore = useUserStore()
const route = useRoute();
const router = useRouter();
const { proxy } = getCurrentInstance();

const loginForm = ref({
  username: "admin",
  password: "admin123",
  rememberMe: false,
  code: "",
  uuid: ""
});

const loginRules = {
  username: [{ required: true, trigger: "blur", message: "请输入您的账号" }],
  password: [{ required: true, trigger: "blur", message: "请输入您的密码" }],
  code: [{ required: true, trigger: "change", message: "请输入验证码" }]
};

const codeUrl = ref("");
const loading = ref(false);
// 验证码开关
const captchaEnabled = ref(true);
// 注册开关
const register = ref(false);
const redirect = ref(undefined);

watch(route, (newRoute) => {
  redirect.value = newRoute.query && newRoute.query.redirect;
}, { immediate: true });

function handleLogin() {
  proxy.$refs.loginRef.validate(valid => {
    if (valid) {
      loading.value = true;
      // 勾选了需要记住密码设置在 cookie 中设置记住用户名和密码
      if (loginForm.value.rememberMe) {
        Cookies.set("username", loginForm.value.username, { expires: 30 });
        Cookies.set("password", encrypt(loginForm.value.password), { expires: 30 });
        Cookies.set("rememberMe", loginForm.value.rememberMe, { expires: 30 });
      } else {
        // 否则移除
        Cookies.remove("username");
        Cookies.remove("password");
        Cookies.remove("rememberMe");
      }
      // 调用action的登录方法
      userStore.login(loginForm.value).then(() => {
        const query = route.query;
        const otherQueryParams = Object.keys(query).reduce((acc, cur) => {
          if (cur !== "redirect") {
            acc[cur] = query[cur];
          }
          return acc;
        }, {});
        router.push({ path: redirect.value || "/", query: otherQueryParams });
      }).catch(() => {
        loading.value = false;
        // 重新获取验证码
        if (captchaEnabled.value) {
          getCode();
        }
      });
    }
  });
}

function getCode() {
  getCodeImg().then(res => {
    captchaEnabled.value = res.captchaEnabled === undefined ? true : res.captchaEnabled;
    if (captchaEnabled.value) {
      codeUrl.value = "data:image/gif;base64," + res.img;
      loginForm.value.uuid = res.uuid;
    }
  });
}

function getCookie() {
  const username = Cookies.get("username");
  const password = Cookies.get("password");
  const rememberMe = Cookies.get("rememberMe");
  loginForm.value = {
    username: username === undefined ? loginForm.value.username : username,
    password: password === undefined ? loginForm.value.password : decrypt(password),
    rememberMe: rememberMe === undefined ? false : Boolean(rememberMe)
  };
}

getCode();
getCookie();
</script>

<style lang='scss' scoped>
.login {
  display: flex;
  justify-content: space-between;
  align-items: center;
  height: 100%;
  padding: clamp(32px, 7vw, 112px);
  background: #0b2539;
  overflow: hidden;
  position: relative;
  &::before, &::after { content: ""; position: absolute; border-radius: 50%; filter: blur(2px); pointer-events: none; }
  &::before { width: 560px; height: 560px; left: -180px; bottom: -260px; background: radial-gradient(circle, rgba(20,184,166,.28), transparent 68%); }
  &::after { width: 640px; height: 640px; right: -260px; top: -300px; background: radial-gradient(circle, rgba(56,189,248,.16), transparent 68%); }
}
.login-brand { position: relative; z-index: 1; max-width: 620px; color: #fff; }
.brand-mark, .form-eyebrow { color: #5eead4; font-size: 12px; font-weight: 700; letter-spacing: .16em; }
.login-brand h1 { margin: 24px 0; font-size: clamp(36px, 4vw, 60px); line-height: 1.16; letter-spacing: -.045em; text-wrap: balance; }
.login-brand p { max-width: 540px; color: #b9cbd8; font-size: 16px; line-height: 1.9; }
.brand-metrics { display: flex; gap: 34px; margin-top: 44px; color: #9fb5c5; }
.brand-metrics span { display: flex; flex-direction: column; gap: 6px; }
.brand-metrics strong { color: #fff; font-size: 20px; }

.title {
  margin: 8px 0 6px;
  color: var(--lx-navy);
  font-size: 27px;
  letter-spacing: -.03em;
}
.form-subtitle { margin: 0 0 28px; color: var(--lx-muted); }

.login-form {
  position: relative;
  z-index: 1;
  border-radius: 20px;
  background: #ffffff;
  width: min(440px, 100%);
  padding: 42px 42px 28px;
  box-shadow: 0 30px 80px rgba(0, 13, 28, .3);

  .el-input {
    height: 40px;

    input {
      height: 40px;
    }
  }

  .input-icon {
    height: 39px;
    width: 14px;
    margin-left: 0px;
  }
}

.login-tip {
  font-size: 13px;
  text-align: center;
  color: #bfbfbf;
}

.login-code {
  width: 33%;
  height: 40px;
  float: right;

  img {
    cursor: pointer;
    vertical-align: middle;
  }
}

.el-login-footer {
  height: 40px;
  line-height: 40px;
  position: fixed;
  bottom: 0;
  width: 100%;
  text-align: center;
  color: #9fb5c5;
  font-family: Arial;
  font-size: 12px;
  letter-spacing: 1px;
}

@media (max-width: 900px) {
  .login { justify-content: center; padding: 24px; }
  .login-brand { display: none; }
  .login-form { padding: 34px 26px 20px; }
  .el-login-footer { position: absolute; }
}

.login-code-img {
  height: 40px;
  padding-left: 12px;
}
</style>
