# tiktok-auth

TikTok 授权登录 uni-app 插件（Android / iOS），基于 **TikTok OpenSDK 官方 Android / iOS SDK** 封装为 UTS 插件。客户端仅获取 `authCode`，由你的服务端用 `authCode + codeVerifier + client_secret` 换取 `access_token`，符合 TikTok OAuth2 PKCE 流程。

> 适用于 uni-app（vue2 / vue3）App 端。不支持 H5、小程序、鸿蒙。

---

## 一、功能特性

- ✅ 已安装 TikTok App：拉起 App 原生授权，结果经 `onActivityResult`（Android）/ App 生命周期钩子（iOS）返回
- ✅ 未安装 TikTok App：自动降级 Chrome Custom Tab（Android）/ Safari（iOS）网页授权，结果经 `redirect_uri` 回跳拦截
- ✅ 内置 PKCE（`code_verifier` 自动生成并在 WebAuth 冷启动回跳时持久化恢复）
- ✅ 支持自定义 `scope` / `language` / `redirectUri`
- ✅ Android 11+ `<queries>` 已声明 TikTok 包名；iOS `LSApplicationQueriesSchemes` 已声明

---

## 二、环境要求

| 项目 | 要求 |
|------|------|
| HBuilderX | ≥ 4.25 |
| uni-app | ≥ 3.99 |
| Android | minSdk 23（Android 6.0+） |
| iOS | deploymentTarget 12.0+，仅 arm64 |
| 应用形式 | App（vue2 / vue3 / nvue） |

> ⚠️ UTS 原生代码改动后，**必须重新制作自定义基座或重新云打包**才能生效，热重载不更新原生层。

---

## 三、TikTok 开发者后台配置（必做）

在使用前，需到 [TikTok for Developers](https://developers.tiktok.com/) 完成：

1. 创建 App，获取 **Client Key**（即 `clientKey`）。
2. 配置 **Redirect URL（redirect_uri）**：例如 `https://your-domain.com/callback`，需与代码中传入的 `redirectUri` 完全一致。
3. 配置 **Android 包名**（`com.xxx.xxx`）与 **签名 SHA256 指纹**。
4. 配置 **iOS Bundle ID** 与 **URL Scheme**（值为你的 `clientKey`）。
5. 申请所需 **Scopes**（如 `user.info.basic`、`user.info.profile`）。

---

## 四、插件配置（接入必做）

### Android

插件 `AndroidManifest.xml` 已注册一个用于拦截网页授权回跳的 `TikTokAuthCallbackActivity`，其 `intent-filter` 拦截的 host 为：

```
android:host="https://your-domain.com/callback"
```

> ⚠️ **上架前的必改项**：示例中的 host 为作者演示域名。你需将两处对齐：
> 1. 把上表中的 `host` 改成你自己的 `redirect_uri` 域名（且 `intent-filter` 内 `data` **不要写 path**，Android 12+ 才执行 App Links 自动验证）。
> 2. 代码中 `tiktokLogin({ redirectUri })` 传入的 `redirectUri` 必须与该 host 同域。
> 3. 该域名需在 **TikTok 后台登记的 Redirect URL** 内，并做 Apple/Google 的 App Links / Universal Link 托管验证（`assetlinks.json` / `apple-app-site-association`）。

### iOS

插件 `info.plist` 使用占位符 `YOUR_TIKTOK_CLIENT_KEY`，**打包/上架前必须替换成你自己的 Client Key**（两处：SDK 读取的 `TikTokClientKey` 与回调 `CFBundleURLSchemes`，二者必须一致）：

```xml
<key>TikTokClientKey</key>
<string>YOUR_TIKTOK_CLIENT_KEY</string>

<!-- 授权回调 URL Scheme：必须为你的 Client Key -->
<key>CFBundleURLTypes</key>
<array>
  <dict>
    <key>CFBundleURLSchemes</key>
    <array>
      <string>YOUR_TIKTOK_CLIENT_KEY</string>
    </array>
  </dict>
</array>

<!-- 用于检测 TikTok App 是否安装 -->
<key>LSApplicationQueriesSchemes</key>
<array>
  <string>snssdk1233</string>
  <string>snssdk1180</string>
  <string>tiktokopensdk</string>
</array>
```

> ⚠️ **一键替换**：仓库根目录 `scripts/replace_tiktok_key.sh` 可一次性替换 iOS key 与 Android 回跳 host：
> ```bash
> bash scripts/replace_tiktok_key.sh <你的ClientKey> <你的redirect域名>
> # 例：bash scripts/replace_tiktok_key.sh axxxxxxxxxx code.example.com
> ```
> 若不替换，所有安装包会共用同一 Scheme 导致冲突、且授权校验失败。替换后请勿将含真实 key 的文件提交到公开仓库。

---

## 五、安装

方式一：通过 HBuilderX 插件市场导入 `uni_modules/tiktok-auth`。

方式二：手动将 `tiktok-auth` 目录放到项目 `uni_modules/` 下。

---

## 六、使用方式

### 1. 引入

```js
import { tiktokLogin } from '@/uni_modules/tiktok-auth'
```

### 2. 发起授权

```js
tiktokLogin({
  clientKey: '你的ClientKey',
  // Android 端 redirectUri 必须与插件 AndroidManifest 的 CallbackActivity host 同域
  // iOS 端 clientKey 必须与 info.plist 的 URL Scheme 一致
  redirectUri: 'https://your-domain.com/callback',
  scope: 'user.info.basic,user.info.profile',
  language: 'en',
  // autoAuthDisabled: true 可强制走网页授权（默认 false，优先拉起 App）
}).then((res) => {
  console.log('authCode', res.authCode)
  console.log('codeVerifier', res.codeVerifier)
  // 把 authCode + codeVerifier 发到你后端，换取 access_token
  uni.request({
    url: 'https://your-server.com/api/tiktok/exchange',
    method: 'POST',
    data: { authCode: res.authCode, codeVerifier: res.codeVerifier }
  })
}).catch((err) => {
  // err.errCode: -1 用户取消；-2 失败；401 参数错误
  uni.showToast({ title: err.errMsg || '授权失败', icon: 'none' })
})
```

### 3. 接口签名

```ts
type TikTokLoginOptions = {
  clientKey: string
  redirectUri: string
  scope?: string | null          // 默认 user.info.basic,user.info.profile
  language?: string | null       // 授权页语言
  autoAuthDisabled?: boolean | null // true=强制网页授权，默认 false
  success?: (result: TikTokLoginSuccessResult) => void
  fail?: (result: TikTokLoginFailResult) => void
}

type TikTokLoginSuccessResult = {
  authCode: string       // 提交后端换取 token
  codeVerifier: string   // PKCE，需随 authCode 一起提交后端
  state: string          // 防 CSRF，可校验与发起时一致
  permissions: string[]
}

type TikTokLoginFailResult = {
  errCode: number        // -1 用户取消；-2 失败；401 参数错误
  errMsg: string
}
```

---

## 七、错误码

| errCode | 含义 | 排查方向 |
|---------|------|----------|
| 200 | 成功 | — |
| -1 | 用户取消授权 | TikTok 内点了取消/返回 |
| -2 | 拉起/解析失败 | 见下「常见问题」 |
| 401 | 参数错误 | `clientKey` / `redirectUri` 为空 |

---

## 八、常见问题（FAQ）

**Q1：点击授权后无反应 / 白屏，拉不起 TikTok？**
- 检查 TikTok App 是否安装；未安装会降级网页授权，需 `redirect_uri` 的 App Links / Universal Link 配置正确。
- 确认 `clientKey` 与后台、包名、签名一致（Android 端签名指纹不匹配会导致 SDK 判 TikTok「未安装」）。
- UTS 原生改动后是否**重新制作了自定义基座/云打包**。

**Q2：返回 `-2` 但无授权页弹出？**
- 多为 `clientKey` / 包名 / 签名 / Bundle Id 与 TikTok 后台不匹配，或 `scope` 含后台未申请的权限。
- 查看原生日志 `TikTokAuth` 标签，会打印 `authError` 具体值（如 `invalid_client`、`E10001`）。

**Q3：网页授权完成后无法回跳到 App？**
- Android：`redirect_uri` 域名需与 `AndroidManifest.xml` 中 `CallbackActivity` 的 `intent-filter` host 一致，且该域名已做 App Links 验证（`assetlinks.json` 可访问）。
- iOS：`redirect_uri` 需配置为 Universal Link，并在 `apple-app-site-association` 中放行。

**Q4：iOS 提示 `canOpenURL` 被拒 / 检测不到 TikTok？**
- 已在 `info.plist` 声明 `LSApplicationQueriesSchemes`，无需额外处理；若自定义渠道版 TikTok，可补充对应 scheme。

---

## 九、兼容性与限制

- 仅支持 **uni-app App 端**（Android / iOS），不支持 H5、各平台小程序、鸿蒙。
- Android 最低 API 23；iOS 最低 12.0，仅 arm64 架构。
- 依赖：`androidx.browser:browser:1.3.0`、`com.google.code.gson:gson:2.9.1`（已写入 `config.json`，自动集成）。
- 基于 TikTok OpenSDK `tiktok-opensdk-android` / `tiktok-opensdk-ios` 官方源码封装。

---

## 十、隐私与权限说明

- 本插件仅用于 TikTok 授权登录，获取用户公开资料（头像、昵称、`open_id`）。
- 授权完成后客户端**仅持有 `auth_code`**，需由你的服务端换取 `access_token`，插件不采集、不上传任何其他数据。
- 不涉及广告、不涉及额外系统权限申请。

---

## 十一、版本记录

- **v1.1.0**：基于官方 OpenSDK 封装 UTS 插件；支持 App 原生授权 + 网页降级；内置 PKCE 与冷启动 codeVerifier 恢复。

---

## 十二、参考资料

- [TikTok Login Kit 文档](https://developers.tiktok.com/doc/login-kit-ios)
- [TikTok OpenSDK Android](https://github.com/tiktok/tiktok-opensdk-android)
- [TikTok OpenSDK iOS](https://github.com/tiktok/tiktok-opensdk-ios)
