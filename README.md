

# TikTok 登录授权插件 (tiktok-auth)

TikTok 登录授权插件是一个 uni-app 插件，用于在应用中集成 TikTok 社交登录功能。本插件支持 Android 和 iOS 双平台，提供完整的授权流程和回调处理。

## 功能特性

- **原生 SDK 集成**：使用 TikTok 官方 Open SDK，保证安全性和稳定性
- **PKCE 安全认证**：采用 Proof Key for Code Exchange 机制，防止授权码拦截攻击
- **平台支持**：完整支持 Android 和 iOS 平台
- **灵活授权**：支持自定义授权范围（scope）和多种授权方式
- **统一接口**：提供简洁的 UTS API，便于在 uni-app 项目中调用
- **错误处理**：完善的错误码体系，便于问题排查

## 环境要求

- HBuilderX 3.6.0+ 或更新的 IDE 版本
- TikTok Open SDK（已内置）
- Android 设备：Android 5.0（API 21）及以上版本
- iOS 设备：iOS 13.0 及以上版本
- 应用已安装 TikTok 客户端（部分功能需要）

## TikTok 开发者后台配置

使用本插件前，您需要先完成 TikTok 开发者账号的申请和应用的创建。

### 步骤一：创建开发者账号

1. 访问 [TikTok for Developers](https://developers.tiktok.com/) 官网
2. 注册并登录开发者账号
3. 完成开发者账号认证（根据提示提交相关资料）

### 步骤二：创建应用

1. 在开发者后台点击「My Apps」-「Create App」
2. 填写应用名称、描述等信息
3. 选择应用类型（建议选择「Consumer」或「Games」）
4. 提交审核并等待通过

### 步骤三：获取应用凭证

应用创建完成后，获取以下信息：

| 字段 | 说明 | 获取位置 |
|------|------|----------|
| Client Key | 应用客户端密钥 | 应用设置页面的「Basic Settings」 |
| Client Secret | 应用客户端密钥（注意保密） | 同上 |
| Redirect URI | 授权回调地址 | 「Products」-「TikTok Login」设置中 |

### 步骤四：配置授权范围

在「Products」-「TikTok Login」中，配置应用需要的授权范围：

| 权限 | scope 值 | 说明 |
|------|----------|------|
| 基本信息 | `user.info.basic` | 获取用户 ID、昵称等基本信息 |
| 头像 | `user.info.avatar` | 获取用户头像 |
| 视频 | `video.list` | 获取用户发布的视频列表 |
| 用户画像 | `user.info.profile` | 获取用户详细资料 |

## 插件配置（接入必做）

### Android 平台配置

#### 1. 修改 AndroidManifest.xml

在 `manifest.json` 中添加以下权限和组件配置：

```json
{
  "app-plus": {
    "android": {
      "permissions": [
        "<uses-permission android:name=\"android.permission.INTERNET\"/>"
      ]
    }
  }
}
```

#### 2. 配置授权回调 Activity

确保 `AndroidManifest.xml` 中包含以下 Activity 配置（插件已内置）：

```xml
<activity
    android:name=".TikTokAuthCallbackActivity"
    android:exported="true"
    android:launchMode="singleTask">
    <intent-filter>
        <action android:name="android.intent.action.VIEW" />
        <category android:name="android.intent.category.DEFAULT" />
        <category android:name="android.intent.category.BROWSABLE" />
        <data
            android:scheme="your-redirect-uri-scheme"
            android:host="your-redirect-uri-host" />
    </intent-filter>
</activity>
```

#### 3. 配置应用签名

在 TikTok 开发者后台添加应用的签名信息：

1. 获取应用的 SHA-1 指纹签名
2. 在应用设置页面的「Android Settings」中添加签名
3. 签名格式：`SHA1;包名`

> 注意：签名不匹配会导致授权失败，请确保开发者后台配置与实际应用签名一致。

### iOS 平台配置

#### 1. 配置 URL Scheme

在 `manifest.json` 中配置应用的 URL Scheme：

```json
{
  "app-plus": {
    "ios": {
      "urlschemes": [
        "tt1234567890"  // 替换为你的 TikTok Client Key 前缀
      ]
    }
  }
}
```

#### 2. 配置 LSApplicationQueriesSchemes

添加 TikTok 客户端查询白名单：

```json
{
  "app-plus": {
    "ios": {
      "schemes": {
        "LSApplicationQueriesSchemes": [
          "tiktokopensdk",
          "tiktoksharesdk",
          "snssdk1233",
          "snssdk1234"
        ]
      }
    }
  }
}
```

#### 3. 配置 Universal Links（可选）

如果需要支持 Universal Links 方式跳转：

1. 在 Apple Developer 后台为应用配置 Associated Domains
2. 在 `manifest.json` 中添加：

```json
{
  "app-plus": {
    "ios": {
      "schemes": {
        "NSExtension": {
          "NSExtensionPointIdentifier": "com.apple.developer.associated-domains",
          "NSExtensionAttributes": {
            "com.apple.developer.associated-domains": [
              "applinks:your-domain.com"
            ]
          }
        }
      }
    }
  }
}
```

3. 在 TikTok 开发者后台配置 Universal Links

## 安装

### 方法一：通过 HBuilderX 插件市场安装

1. 打开 HBuilderX
2. 菜单「工具」→「插件安装」→「插件市场」
3. 搜索「tiktok-auth」
4. 点击「导入插件」

### 方法二：手动安装

1. 下载插件 ZIP 包
2. 解压到项目 `uni_modules` 目录
3. 重命名为 `tiktok-auth`

### 方法三：Git 安装

在项目 `uni_modules` 目录执行：

```bash
git clone https://gitee.com/sugar_wei/tiktok-auth.git
```

## 使用方式

### 1. 引入插件

```javascript
// 在需要使用 TikTok 登录的页面引入
const tiktokAuth = uni.requireNativePlugin('tiktok-auth');
```

### 2. 检查 TikTok 是否安装

```javascript
// 检查 TikTok 客户端是否安装
const isInstalled = tiktokAuth.isTikTokInstalled();
console.log('TikTok 是否安装:', isInstalled);
```

### 3. 发起授权

```javascript
// 发起 TikTok 授权登录
tiktokAuth.authorize({
  clientKey: 'YOUR_CLIENT_KEY',      // 你的 Client Key
  redirectUri: 'YOUR_REDIRECT_URI',  // 回调地址
  scope: 'user.info.basic,user.info.avatar',  // 授权范围，多个用逗号分隔
  language: 'zh-CN',                 // 可选，语言偏好
  autoAuthDisabled: false            // 是否禁用自动授权
}, (result) => {
  // 授权回调
  if (result.code === 0) {
    // 授权成功
    const authCode = result.authCode;
    const codeVerifier = result.codeVerifier;
    console.log('授权码:', authCode);
    console.log('Code Verifier:', codeVerifier);
    
    // 将 authCode 发送到服务器换取访问令牌
    // codeVerifier 用于服务端验证（防止授权码被截获）
  } else {
    // 授权失败
    console.log('授权失败:', result.message);
  }
});
```

### 4. 服务端换取访问令牌

前端获取到 `authCode` 后，需要将以下信息发送到你的服务器：

```javascript
// 前端发送的请求数据
const requestData = {
  authCode: result.authCode,
  codeVerifier: result.codeVerifier  // 必须发送，用于服务端验证
};

// 服务端调用 TikTok OAuth 接口
POST https://open.tiktokapis.com/v2/oauth/token/
Content-Type: application/x-www-form-urlencoded

client_key=YOUR_CLIENT_KEY
&client_secret=YOUR_CLIENT_SECRET
&code=AUTH_CODE_FROM_FRONTEND
&grant_type=authorization_code
&code_verifier=CODE_VERIFIER_FROM_FRONTEND
```

服务端响应：

```json
{
  "access_token": "access_token_value",
  "refresh_token": "refresh_token_value",
  "expires_in": 86400,
  "open_id": "user_open_id",
  "scope": "user.info.basic,user.info.avatar",
  "token_type": "Bearer"
}
```

### 5. 获取用户信息

使用 `access_token` 调用用户信息接口：

```javascript
// 调用 TikTok 用户信息接口
GET https://open.tiktokapis.com/v2/user/info/
Authorization: Bearer ACCESS_TOKEN
fields: open_id,display_name,avatar_url,bio_description
```

### 6. 接口签名（如需）

对于需要签名的接口，使用插件提供的签名功能：

```javascript
// 对请求参数进行签名
const signature = tiktokAuth.sign({
  url: 'YOUR_API_URL',
  method: 'POST',
  params: {
    'access_token': accessToken,
    'open_id': openId
  }
});
```

## 错误码说明

| 错误码 | 常量名 | 说明 |
|--------|--------|------|
| 0 | `SUCCESS` | 授权成功 |
| -1 | `CANCEL` | 用户取消授权 |
| 1 | `ERROR_UNKNOWN` | 未知错误 |
| 2 | `ERROR_INVALID_PARAMS` | 参数错误 |
| 3 | `ERROR_CLIENT_KEY_INVALID` | Client Key 无效 |
| 4 | `ERROR_REDIRECT_URI_MISMATCH` | Redirect URI 不匹配 |
| 5 | `ERROR_SDK_NOT_INITIALIZED` | SDK 未初始化 |
| 6 | `ERROR_NETWORK_FAILED` | 网络错误 |
| 7 | `ERROR_TIKTOK_NOT_INSTALLED` | TikTok 未安装 |
| 8 | `ERROR_AUTH_FAILED` | 授权失败 |
| 9 | `ERROR_CODE_EXPIRED` | 授权码已过期 |
| 10 | `ERROR_CODE_VERIFY_FAILED` | Code Verifier 验证失败 |

## 常见问题（FAQ）

### Q1: 授权时提示 "redirect_uri mismatch"

检查以下配置：
1. 开发者后台的 Redirect URI 是否与代码中一致
2. Redirect URI 必须使用 HTTPS 或自定义 URL Scheme
3. iOS 平台需要在 `manifest.json` 中正确配置 URL Scheme

### Q2: 授权成功后无法获取用户信息

可能原因：
1. `access_token` 已过期，需要使用 `refresh_token` 刷新
2. 请求的 `scope` 未包含用户信息的权限
3. 服务端调用接口时网络问题

### Q3: Android 设备上授权没有弹窗

可能原因：
1. TikTok 客户端版本过低
2. 应用签名与开发者后台配置不一致
3. 未正确配置 AndroidManifest.xml 中的 Activity

### Q4: iOS 设备上授权没有反应

可能原因：
1. 未在 `manifest.json` 中配置 URL Scheme
2. LSApplicationQueriesSchemes 配置不完整
3. 设备未安装 TikTok 客户端（需要引导用户安装）

### Q5: 如何处理授权回调？

确保在 `App.vue` 的 `onLaunch` 中处理：

```javascript
export default {
  onLaunch: function(options) {
    // 处理通过 URL Scheme 唤起的情况
    if (options.query && options.query.state) {
      // 处理授权回调
    }
  }
}
```

### Q6: PKCE 是什么？为什么需要它？

PKCE（Proof Key for Code Exchange）是一种 OAuth 2.0 扩展机制，用于防止授权码被拦截攻击。本插件自动生成 `code_verifier` 和 `code_challenge`，您需要：
1. 将 `code_verifier` 保存到本地
2. 授权成功后，将 `code_verifier` 发送给服务器
3. 服务器使用 `code_verifier` 换取 `access_token`

## 兼容性与限制

### Android 平台

| 功能 | 支持情况 | 说明 |
|------|----------|------|
| 客户端授权 | ✅ 支持 | 需要安装 TikTok 客户端 |
| 网页授权 | ✅ 支持 | 作为客户端授权的备选方案 |
| Universal Links | ❌ 不支持 | Android 平台不适用 |
| 自动授权 | ✅ 支持 | 可通过 `autoAuthDisabled` 控制 |

### iOS 平台

| 功能 | 支持情况 | 说明 |
|------|----------|------|
| 客户端授权 | ✅ 支持 | 需要安装 TikTok 客户端 |
| 网页授权 | ✅ 支持 | 使用 ASWebAuthenticationSession |
| Universal Links | ✅ 支持 | iOS 9+ 支持，需要配置 |
| 自动授权 | ✅ 支持 | 可通过 `autoAuthDisabled` 控制 |

### 已知限制

1. **Android 11+ 兼容性**：部分设备需要添加 `queries` 配置
2. **iOS 14+ 权限**：首次授权会提示用户确认
3. **模拟器测试**：建议使用真机测试，模拟器可能无法正常工作
4. **多账号切换**：当前版本不支持账号切换功能
5. **离线授权**：不支持 refresh_token 离线刷新

## 隐私与权限说明

### 权限列表

| 权限 | 用途 | 是否必须 |
|------|------|----------|
| INTERNET | 网络通信，用于授权流程 | 必须 |
| ACCESS_NETWORK_STATE | 检测网络状态 | 可选 |
| ACCESS_WIFI_STATE | 检测 WiFi 状态 | 可选 |

### 隐私合规建议

1. **用户授权提示**：在发起授权前，建议向用户说明获取的信息用途
2. **隐私政策**：在应用的隐私政策中说明集成了 TikTok 登录功能
3. **数据使用**：获取的用户数据应符合 TikTok 开发者政策
4. **年龄限制**：如果应用有年龄限制，需要处理相应的授权逻辑

### GDPR 合规

对于面向欧洲用户的应用：
1. 确保用户在授权前了解数据收集范围
2. 提供数据删除机制（通过 TikTok 的数据删除 API）
3. 在隐私政策中说明数据处理方式

## 版本记录

### 1.2.0（2026-08-27）

- 🆕 新增 iOS 平台 Universal Links 支持
- 🐞 修复 Android 平台授权回调问题
- 🐞 修复 iOS 平台多次授权回调问题
- ⚡ 优化授权流程，提高成功率
- 📝 更新文档

### 1.1.0（2026-08-25）

- 🆕 新增 Android 平台支持
- 🆕 新增授权取消回调
- 🐞 修复 PKCE code_verifier 验证问题
- ⚡ 优化错误提示信息

### 1.0.0（2026-08-20）

- 🎉 初始版本发布
- ✅ iOS 平台基础授权功能
- ✅ 支持自定义授权范围
- ✅ 完整的错误处理

## 参与贡献

### 反馈问题

如发现问题或有任何建议，请通过以下方式反馈：

1. 在 [Gitee Issues](https://gitee.com/sugar_wei/tiktok-auth/issues) 提交问题
2. 描述问题现象、复现步骤和期望行为
3. 提供相关日志和设备信息

### 提交代码

1. Fork 本项目
2. 创建特性分支：`git checkout -b feature/your-feature`
3. 提交改动：`git commit -am 'Add some feature'`
4. 推送到分支：`git push origin feature/your-feature`
5. 提交 Pull Request

## 参考资料

- [TikTok for Developers](https://developers.tiktok.com/)
- [TikTok Login 文档](https://developers.tiktok.com/doc/TikTok-Login-Quickstart)
- [OAuth 2.0 with PKCE](https://datatracker.ietf.org/doc/html/rfc7636)
- [uni-app 插件开发文档](https://nativesupport.dcloud.net.cn/NativePlugin/README)
- [TikTok API Reference](https://developers.tiktok.com/doc/TikTok-API-Reference)

---

**版权声明**：本插件基于 MIT 协议开源，作者保留最终解释权。

**免责声明**：本插件仅供学习和研究使用，使用前请仔细阅读 TikTok 开发者协议和相关法律法规。

---

## 支持项目 (Support)

☕ 如果您觉得本插件对您有所帮助，欢迎打赏作者！


| 支付宝 (Alipay) | 微信支付 (WeChat) 
| :---: | :---: | 
| <img src="./static/alpay.png" width="200" alt="支付宝二维码" /> | <img src="./static/wechat.png" width="200" alt="微信二维码" /> 
