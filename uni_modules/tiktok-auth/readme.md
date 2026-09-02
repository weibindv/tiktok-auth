# tiktok-auth

TikTok **授权登录 + 分享** uni-app 插件（Android / iOS），基于 **TikTok OpenSDK 官方 Android / iOS SDK（Share Kit）** 封装为 UTS 插件，登录与分享合并在同一插件模块内。

- **登录**：客户端仅获取 `authCode`，由你的服务端用 `authCode + codeVerifier + client_secret` 换取 `access_token`，符合 TikTok OAuth2 PKCE 流程。
- **分享**：将图片 / 视频分享到 TikTok（支持普通发布与 Green Screen 绿幕模式）。TikTok 只能读取系统相册 / MediaStore 中的媒体，插件原生层会先把媒体保存到相册，再拉起 TikTok。

> 适用于 uni-app（vue2 / vue3）App 端。不支持 H5、小程序、鸿蒙。

---

## 一、功能特性

- ✅ **登录**：已安装 TikTok App → 拉起 App 原生授权，结果经原生回调 + App 生命周期钩子 + JS 兜底三重通道返回；未安装 → 直接提示"未安装 TikTok"，**不做网页授权降级**
- ✅ **分享**（v1.4.0 新增）：Android 拉起 TikTok 发布页（SystemShareActivity），iOS 以 Universal Link 拉起 TikTok 分享；支持多图（1~35 张）、多视频（≤12 个、3s~10min、mp4）、绿幕模式
- ✅ 媒体自动预处理：本地绝对路径（兼容 `file://` 前缀）或远程 `https://` URL 均可，原生层自动下载 / 写入系统相册（iOS PHPhotoLibrary）/ MediaStore（Android），再发起分享
- ✅ 分享结果回调：原生 completion → UTS 钩子 → `App.vue` 从 `plus.runtime.arguments` 兜底，幂等去重；冷启动被 TikTok 回跳拉起也能取回结果
- ✅ 内置 PKCE（`code_verifier` 自动生成并在冷启动回跳时持久化恢复）
- ✅ 支持自定义 `scope` / `language` / `redirectUri`
- ✅ Android `queries` 已声明 TikTok 包名；iOS `LSApplicationQueriesSchemes` 已声明；iOS 已内置 `Photos.framework` 与相册写入权限说明

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
2. 配置 **Redirect URL（redirect_uri）**：例如 `https://code.hk.lingchuang.co`，需与代码中传入的 `redirectUri` 完全一致（登录与分享共用）。
3. 配置 **Android 包名**（`com.xxx.xxx`）与 **签名 SHA256 指纹**。
4. 配置 **iOS Bundle ID** 与 **URL Scheme**（值为你的 `clientKey`）。
5. 申请所需 **Scopes**（如 `user.info.basic`、`user.info.profile`）。
6. **开启 Share Kit 能力**（v1.4.0 分享必需）：在 App 的 Products / Share Kit 处申请开启，使用与登录**同一个 Client Key**。iOS 分享回跳与登录共用 Universal Link（`apple-app-site-association` 已部署则无需额外配置）。

---

## 四、插件配置（接入必做）

### Android

插件 `AndroidManifest.xml` 已注册一个用于拦截授权/分享回跳的 `TikTokAuthCallbackActivity`，其 `intent-filter` 拦截的 host 为：

```
android:host="https://your-domain.com/callback"
```

> ⚠️ **上架前的必改项**：示例中的 host 为作者演示域名。你需将两处对齐：
> 1. 把上表中的 `host` 改成你自己的 `redirect_uri` 域名（且 `intent-filter` 内 `data` **不要写 path**，Android 12+ 才执行 App Links 自动验证）。
> 2. 代码中 `tiktokLogin({ redirectUri })` 传入的 `redirectUri` 必须与该 host 同域。
> 3. 该域名需在 **TikTok 后台登记的 Redirect URL** 内，并做 Apple/Google 的 App Links / Universal Link 托管验证（`assetlinks.json` / `apple-app-site-association`）。

> Android 分享还需：设备已安装 TikTok、分享时传入 `clientKey`、分享媒体已可被 MediaStore 读取（插件自动处理）。

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

<!-- 分享：把媒体"保存到相册"的权限说明（v1.4.0 起内置，仅 Add-only 写入权限） -->
<key>NSPhotoLibraryAddUsageDescription</key>
<string>需要将视频/图片保存到系统相册，以便分享到 TikTok</string>
```

> ⚠️ **一键替换**：仓库根目录 `scripts/replace_tiktok_key.sh` 可一次性替换 iOS key 与 Android 回跳 host：
> ```bash
> bash scripts/replace_tiktok_key.sh <你的ClientKey> <你的redirect域名>
> # 例：bash scripts/replace_tiktok_key.sh axxxxxxxxxx code.example.com
> ```
> 若不替换，所有安装包会共用同一 Scheme 导致冲突、且授权校验失败。替换后请勿将含真实 key 的文件提交到公开仓库。

> iOS `Photos.framework` 已写入插件 `config.json` 的 `frameworks`，无需手动添加；App Store 审核时相册权限按 "Add photos only"（仅添加）声明。

---

## 五、安装

方式一：通过 HBuilderX 插件市场导入 `uni_modules/tiktok-auth`。

方式二：手动将 `tiktok-auth` 目录放到项目 `uni_modules/` 下。

---

## 六、使用方式

### 1. 引入

```js
import { tiktokLogin, tiktokShare } from '@/uni_modules/tiktok-auth'
// 推荐：分享走 Promise 封装（含超时/冷启动兜底）
import { tiktokShare, consumePendingShareResult } from '@/common/tiktokShare.js'
```

### 2. 发起授权登录

```js
// 注意：tiktokLogin 是【回调式】API（返回 void，非 Promise），不能用 .then
tiktokLogin({
  clientKey: '你的ClientKey',
  // Android 端 redirectUri 必须与插件 AndroidManifest 的 CallbackActivity host 同域
  // iOS 端 clientKey 必须与 info.plist 的 URL Scheme 一致
  redirectUri: 'https://your-domain.com/callback',
  scope: 'user.info.basic,user.info.profile',
  language: 'en',
  success: (res) => {
    console.log('authCode', res.authCode)
    console.log('codeVerifier', res.codeVerifier)
    // 把 authCode + codeVerifier 发到你后端，换取 access_token
    uni.request({
      url: 'https://your-server.com/api/tiktok/exchange',
      method: 'POST',
      data: { authCode: res.authCode, codeVerifier: res.codeVerifier }
    })
  },
  fail: (err) => {
    // err.errCode: -1 用户取消；-2 失败/未安装 TikTok；401 参数错误
    uni.showToast({ title: err.errMsg || '授权失败', icon: 'none' })
  }
})
```

> 自 v1.3.0 起**不再提供网页授权兜底**：未安装 TikTok 时两端直接回调"未安装 TikTok，请先安装 TikTok 应用后重试"，`autoAuthDisabled` 参数已无效（原生恒走 App 授权）。

### 3. 分享到 TikTok

```js
// 回调式
tiktokShare({
  clientKey: '你的ClientKey',      // Android 必传；iOS 走 Info.plist 可留空
  mediaType: 'video',             // 'video' | 'image'，paths 内媒体必须全部同类型
  paths: [
    'https://cdn.yours.com/promo/a.mp4',  // 远程 https URL（自动下载）
    '/storage/emulated/0/.../b.mp4'       // 或本地绝对路径（兼容 file:// 前缀）
  ],
  greenScreen: false,             // 可选：true=绿幕模式（仅支持 1 个媒体）
  state: 'share_001',             // 可选：透传标识，回调原样返回
  success: (res) => {
    // res.shareState: 20000 分享成功；20015/20016 已保存草稿
    console.log('shareState', res.shareState, 'requestId', res.requestId)
  },
  fail: (err) => {
    // err.errCode: -1 用户取消；-2 失败/未安装；401 参数错误
    uni.showToast({ title: err.errMsg || '分享失败', icon: 'none' })
  }
})

// 或 Promise 式（推荐，common/tiktokShare.js）
const res = await tiktokShare({
  mediaType: 'video',
  paths: ['https://cdn.yours.com/promo/a.mp4']
})
// res.code === 200 → 成功；否则 throw { code, message }（-1 取消 / -2 失败 / -3 超时）
```

> 说明：
> - 媒体会先由原生层保存到系统相册（iOS）/ MediaStore（Android），TikTok 只能读取相册中的资源；期间 App 会退到后台，属正常现象。
> - iOS 首次分享会弹"允许添加到照片"授权（仅写入，不读取相册）。
> - 媒体限制：图片 1~35 张；视频最多 12 个、单个 3s~10min、仅 mp4；绿幕模式仅支持 1 个媒体。
> - 未安装 TikTok 直接回调"未安装"，不弹商店/浏览器引导。
> - 冷启动兜底：若 App 在分享期间被杀，结果由 `App.vue` 缓存并广播 `tiktok:share:result`，业务页在 `onShow` 调 `consumePendingShareResult()` 取回。

### 4. 接口签名

```ts
// ── 登录 ──
type TikTokLoginOptions = {
  clientKey: string
  redirectUri: string
  scope?: string | null          // 默认 user.info.basic,user.info.profile
  language?: string | null       // 授权页语言
  autoAuthDisabled?: boolean | null // 已废弃：v1.3.0 起原生恒走 App 授权，此参数无效
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
  errCode: number        // -1 用户取消；-2 失败/未安装；401 参数错误
  errMsg: string
}

// ── 分享 ──
type TikTokShareOptions = {
  clientKey?: string | null      // Android 必传；iOS 走 Info.plist 可留空
  mediaType: 'video' | 'image'   // paths 内媒体全部同类型
  paths: string[]                // 本地绝对路径（兼容 file://）或 https URL
  redirectUri?: string | null    // 分享回跳 Universal Link，默认同登录域名
  greenScreen?: boolean | null   // true=绿幕模式（仅单媒体），默认 false
  state?: string | null          // 透传标识
  success?: (result: TikTokShareSuccessResult) => void
  fail?: (result: TikTokShareFailResult) => void
}

type TikTokShareSuccessResult = {
  shareState: number     // 20000 成功；20015/20016 已保存草稿
  requestId: string
  state: string
}

type TikTokShareFailResult = {
  errCode: number        // -1 用户取消；-2 失败/未安装；401 参数错误
  errMsg: string
  shareState?: number | null  // 原生分享状态码（20013 取消 / 20019 未安装 / 21003 媒体读取失败 等）
}
```

---

## 七、错误码

### 登录 / 分享通用

| errCode | 含义 | 排查方向 |
|---------|------|----------|
| 200 | 成功（分享时附带 `shareState`） | — |
| -1 | 用户取消 | TikTok 内点了取消/返回 |
| -2 | 拉起/解析/媒体准备失败，或未安装 TikTok | 见下「常见问题」 |
| -3 | JS 层超时（默认 120s，仅 Promise 封装） | 重试或检查 TikTok 是否正常返回 |
| 401 | 参数错误 | `clientKey` / `redirectUri` / `paths` 为空或非法 |

### 分享 `shareState`（原生状态码）

| shareState | 含义 |
|------------|------|
| 20000 | 分享成功 |
| 20013 | 用户取消（对应 fail errCode=-1） |
| 20015 / 20016 | 已保存草稿（按成功处理，可提示"已保存草稿"） |
| 20019 | 未安装 TikTok（对应 fail errCode=-2） |
| 21003 | 媒体读取/准备失败（对应 fail errCode=-2） |

---

## 八、常见问题（FAQ）

**Q1：点击授权后无反应 / 拉不起 TikTok？**
- 检查 TikTok App 是否安装；**未安装不会降级网页授权**，而是直接提示未安装。
- 确认 `clientKey` 与后台、包名、签名一致（Android 端签名指纹不匹配会导致 SDK 判 TikTok「未安装」）。
- UTS 原生改动后是否**重新制作了自定义基座/云打包**。

**Q2：登录返回 `-2` 但无授权页弹出？**
- 多为 `clientKey` / 包名 / 签名 / Bundle Id 与 TikTok 后台不匹配，或 `scope` 含后台未申请的权限。
- 查看原生日志 `TikTokAuth` 标签，会打印 `authError` 具体值（如 `invalid_client`、`E10001`）。

**Q3：分享点了没反应 / 一闪而过？**
- 确认**已安装 TikTok** 且版本支持分享（Android 需 TikTok ≥ 某版本支持 SystemShareActivity，iOS 同理）。
- 确认 TikTok 后台已开启 **Share Kit** 能力，且用同一 Client Key 发包。
- 媒体限制是否满足（视频 ≤12 个、3s~10min、mp4；图片 1~35 张；绿幕仅 1 个媒体）；远程 URL 需可公网直连下载。
- iOS 首次分享需授权"添加到照片"，拒绝后分享无法进行，需到系统设置开启。

**Q4：分享完成/取消后没有回到 App？**
- Android：TikTok 会显式拉起插件注册的 `TikTokAuthCallbackActivity`（带分享结果），无需配置；若被杀进程则冷启动回跳，业务页 `onShow` 调 `consumePendingShareResult()` 取回。
- iOS：分享回跳是 Universal Link，`redirect_uri`（默认 `https://code.hk.lingchuang.co`）必须已在 `apple-app-site-association` 放行（与登录共用域名即可，插件以 `share_state`/`from_platform=tiktoksharesdk` 参数区分登录与分享回跳）。

**Q5：iOS 提示 `canOpenURL` 被拒 / 检测不到 TikTok？**
- 已在 `info.plist` 声明 `LSApplicationQueriesSchemes`，无需额外处理；若自定义渠道版 TikTok，可补充对应 scheme。

---

## 九、兼容性与限制

- 仅支持 **uni-app App 端**（Android / iOS），不支持 H5、各平台小程序、鸿蒙。
- Android 最低 API 23；iOS 最低 12.0，仅 arm64 架构。
- 登录依赖：`androidx.browser:browser:1.3.0`、`com.google.code.gson:gson:2.9.1`（已写入 `config.json`，自动集成）。
- iOS 分享依赖：`Photos.framework`（已写入 `config.json` frameworks）；需 `NSPhotoLibraryAddUsageDescription`（已内置）。
- 分享媒体限制：图片 1~35 张；视频最多 12 个、单个 3s~10min、仅 mp4（含远程 URL 下载后校验）；绿幕模式仅支持 1 个媒体；媒体须为系统相册可读格式。
- 实现说明：iOS 分享为官方 Share Kit 源码移植（同 OpenSDK v2.3.1 家族）；Android 分享因官方 share AAR 未随 OpenSDK 提供，按 ShareRequest Bundle 协议（`TYPE_SHARE_REQUEST`）自研实现拉起发布页。

---

## 十、隐私与权限说明

- **登录**：仅用于 TikTok 授权登录，获取用户公开资料（头像、昵称、`open_id`）；客户端**仅持有 `auth_code`**，由你的服务端换取 `access_token`，插件不采集、不上传任何其他数据。
- **分享**：仅在用户**主动发起分享**时，将所选媒体保存到系统相册（iOS `PHPhotoLibraryAdd` / Android `MediaStore`）以完成分享，不读取相册既有内容、不读取任何用户隐私数据；媒体来源为用户传入的本地文件或远程 URL。
- iOS 相册权限为 **Add-only（仅添加）**，上架审核按 "Add photos only" 声明；不涉及广告、无其他系统权限申请。

---

## 十一、版本记录

- **v1.4.0**：新增 TikTok 分享（双端），与登录合并于同一插件；媒体先存相册/MediaStore 再拉起分享；结果回调三重保险 + 冷启动兜底；iOS 内置 Photos framework 与相册权限说明。
- **v1.3.0**：移除网页授权兜底，未安装 TikTok 直接提示，登录行为双端统一。
- **v1.1.0**：基于官方 OpenSDK 封装 UTS 插件；支持 App 原生授权；修复 Android 新版 TikTok 点"继续"误报取消、iOS 授权页拉不起等问题；内置 PKCE 与冷启动 codeVerifier 恢复。
- **v1.0.0**：首个版本。

---

## 十二、参考资料

- [TikTok Login Kit 文档](https://developers.tiktok.com/doc/login-kit-ios)
- [TikTok Share Kit iOS Quickstart](https://developers.tiktok.com/docs/en/share-kit-ios-quickstart-v2)
- [TikTok Share Kit Android Quickstart](https://developers.tiktok.com/docs/en/share-kit-android-quickstart-v2)
- [TikTok OpenSDK Android](https://github.com/tiktok/tiktok-opensdk-android)
- [TikTok OpenSDK iOS](https://github.com/tiktok/tiktok-opensdk-ios)
