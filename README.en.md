# TikTok Login Authorization Plugin (tiktok-auth)

The TikTok Login Authorization Plugin is a uni-app plugin designed to integrate TikTok social login functionality into applications. This plugin supports both Android and iOS platforms, providing a complete authorization flow and callback handling.

## Features

- **Native SDK Integration**: Uses the official TikTok Open SDK to ensure security and stability
- **PKCE Secure Authentication**: Adopts the Proof Key for Code Exchange mechanism to prevent authorization code interception attacks
- **Platform Support**: Full support for Android and iOS platforms
- **Flexible Authorization**: Supports custom authorization scopes and multiple authorization methods
- **Unified Interface**: Provides a concise UTS API for easy invocation in uni-app projects
- **Error Handling**: Comprehensive error code system for troubleshooting

## Environment Requirements

- HBuilderX 3.6.0+ or newer IDE version
- TikTok Open SDK (Built-in)
- Android Device: Android 5.0 (API 21) and above
- iOS Device: iOS 13.0 and above
- TikTok Client must be installed on the device (required for some features)

## TikTok Developer Console Configuration

Before using this plugin, you need to complete the application of a TikTok developer account and create an application.

### Step 1: Create a Developer Account

1. Visit the [TikTok for Developers](https://developers.tiktok.com/) official website
2. Register and log in to your developer account
3. Complete developer account verification (submit relevant materials as prompted)

### Step 2: Create an Application

1. Click "My Apps" - "Create App" in the developer console
2. Fill in application name, description, and other information
3. Select application type (Recommend selecting "Consumer" or "Games")
4. Submit for review and wait for approval

### Step 3: Obtain Application Credentials

After the application is created, obtain the following information:

| Field | Description | Location |
|------|------|----------|
| Client Key | Application Client Key | "Basic Settings" on the Application Settings page |
| Client Secret | Application Client Secret (Keep Confidential) | Same as above |
| Redirect URI | Authorization Callback URL | "Products" - "TikTok Login" settings |

### Step 4: Configure Authorization Scopes

In "Products" - "TikTok Login", configure the authorization scopes required by the application:

| Permission | Scope Value | Description |
|------|----------|------|
| Basic Information | `user.info.basic` | Retrieve user ID, nickname, and other basic info |
| Avatar | `user.info.avatar` | Retrieve user avatar |
| Videos | `video.list` | Retrieve list of videos published by the user |
| User Profile | `user.info.profile` | Retrieve detailed user profile |

## Plugin Configuration (Required for Integration)

### Android Platform Configuration

#### 1. Modify AndroidManifest.xml

Add the following permissions and component configurations in `manifest.json`:

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

#### 2. Configure Authorization Callback Activity

Ensure `AndroidManifest.xml` includes the following Activity configuration (built-in with the plugin):

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

#### 3. Configure Application Signature

Add the application signature information in the TikTok developer console:

1. Obtain the app's SHA-1 fingerprint signature
2. Add the signature in "Android Settings" on the Application Settings page
3. Signature format: `SHA1;Package Name`

> Note: Mismatched signatures will cause authorization failure. Ensure the developer console configuration matches the actual application signature.

### iOS Platform Configuration

#### 1. Configure URL Scheme

Configure the application's URL Scheme in `manifest.json`:

```json
{
  "app-plus": {
    "ios": {
      "urlschemes": [
        "tt1234567890"  // Replace with your TikTok Client Key prefix
      ]
    }
  }
}
```

#### 2. Configure LSApplicationQueriesSchemes

Add TikTok client query whitelist:

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

#### 3. Configure Universal Links (Optional)

If supporting Universal Links redirection is required:

1. Configure Associated Domains for the application in the Apple Developer console
2. Add in `manifest.json`:

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

3. Configure Universal Links in the TikTok developer console

## Installation

### Method 1: Install via HBuilderX Plugin Market

1. Open HBuilderX
2. Menu "Tools" → "Plugin Installation" → "Plugin Market"
3. Search for "tiktok-auth"
4. Click "Import Plugin"

### Method 2: Manual Installation

1. Download the plugin ZIP package
2. Extract to the project `uni_modules` directory
3. Rename to `tiktok-auth`

### Method 3: Git Installation

Execute in the project `uni_modules` directory:

```bash
git clone https://gitee.com/sugar_wei/tiktok-auth.git
```

## Usage

### 1. Import Plugin

```javascript
// Import on the page where TikTok login is needed
const tiktokAuth = uni.requireNativePlugin('tiktok-auth');
```

### 2. Check if TikTok is Installed

```javascript
// Check if TikTok client is installed
const isInstalled = tiktokAuth.isTikTokInstalled();
console.log('Is TikTok Installed:', isInstalled);
```

### 3. Initiate Authorization

```javascript
// Initiate TikTok authorization login
tiktokAuth.authorize({
  clientKey: 'YOUR_CLIENT_KEY',      // Your Client Key
  redirectUri: 'YOUR_REDIRECT_URI',  // Callback URL
  scope: 'user.info.basic,user.info.avatar',  // Authorization scope, separate multiple with commas
  language: 'zh-CN',                 // Optional, language preference
  autoAuthDisabled: false            // Whether to disable auto authorization
}, (result) => {
  // Authorization callback
  if (result.code === 0) {
    // Authorization successful
    const authCode = result.authCode;
    const codeVerifier = result.codeVerifier;
    console.log('Auth Code:', authCode);
    console.log('Code Verifier:', codeVerifier);
    
    // Send authCode to server to exchange for access token
    // codeVerifier is used for server-side verification (to prevent code interception)
  } else {
    // Authorization failed
    console.log('Authorization Failed:', result.message);
  }
});
```

### 4. Server-Side Exchange for Access Token

After the frontend obtains the `authCode`, send the following information to your server:

```javascript
// Request data sent by frontend
const requestData = {
  authCode: result.authCode,
  codeVerifier: result.codeVerifier  // Must be sent, used for server verification
};

// Server calls TikTok OAuth interface
POST https://open.tiktokapis.com/v2/oauth/token/
Content-Type: application/x-www-form-urlencoded

client_key=YOUR_CLIENT_KEY
&client_secret=YOUR_CLIENT_SECRET
&code=AUTH_CODE_FROM_FRONTEND
&grant_type=authorization_code
&code_verifier=CODE_VERIFIER_FROM_FRONTEND
```

Server Response:

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

### 5. Retrieve User Information

Use `access_token` to call the user information interface:

```javascript
// Call TikTok User Information Interface
GET https://open.tiktokapis.com/v2/user/info/
Authorization: Bearer ACCESS_TOKEN
fields: open_id,display_name,avatar_url,bio_description
```

### 6. Interface Signature (If Required)

For interfaces requiring signatures, use the signature function provided by the plugin:

```javascript
// Sign request parameters
const signature = tiktokAuth.sign({
  url: 'YOUR_API_URL',
  method: 'POST',
  params: {
    'access_token': accessToken,
    'open_id': openId
  }
});
```

## Error Code Reference

| Error Code | Constant Name | Description |
|--------|--------|------|
| 0 | `SUCCESS` | Authorization Successful |
| -1 | `CANCEL` | User Canceled Authorization |
| 1 | `ERROR_UNKNOWN` | Unknown Error |
| 2 | `ERROR_INVALID_PARAMS` | Invalid Parameters |
| 3 | `ERROR_CLIENT_KEY_INVALID` | Invalid Client Key |
| 4 | `ERROR_REDIRECT_URI_MISMATCH` | Redirect URI Mismatch |
| 5 | `ERROR_SDK_NOT_INITIALIZED` | SDK Not Initialized |
| 6 | `ERROR_NETWORK_FAILED` | Network Error |
| 7 | `ERROR_TIKTOK_NOT_INSTALLED` | TikTok Not Installed |
| 8 | `ERROR_AUTH_FAILED` | Authorization Failed |
| 9 | `ERROR_CODE_EXPIRED` | Authorization Code Expired |
| 10 | `ERROR_CODE_VERIFY_FAILED` | Code Verifier Verification Failed |

## Frequently Asked Questions (FAQ)

### Q1: Prompt "redirect_uri mismatch" during authorization

Check the following configurations:
1. Does the Redirect URI in the developer console match the one in the code?
2. Redirect URI must use HTTPS or a custom URL Scheme
3. iOS platform needs correct URL Scheme configuration in `manifest.json`

### Q2: Unable to retrieve user information after successful authorization

Possible reasons:
1. `access_token` has expired, need to refresh using `refresh_token`
2. Requested `scope` does not include permissions for user information
3. Network issues when server calls interface

### Q3: No popup for authorization on Android device

Possible reasons:
1. TikTok client version is too low
2. Application signature is inconsistent with developer console configuration
3. Activity in AndroidManifest.xml is not configured correctly

### Q4: No response for authorization on iOS device

Possible reasons:
1. URL Scheme not configured in `manifest.json`
2. LSApplicationQueriesSchemes configuration is incomplete
3. TikTok client not installed on device (need to guide user to install)

### Q5: How to handle authorization callbacks?

Ensure processing in `onLaunch` of `App.vue`:

```javascript
export default {
  onLaunch: function(options) {
    // Handle cases triggered via URL Scheme
    if (options.query && options.query.state) {
      // Handle authorization callback
    }
  }
}
```

### Q6: What is PKCE and why is it needed?

PKCE (Proof Key for Code Exchange) is an OAuth 2.0 extension mechanism used to prevent authorization code interception attacks. This plugin automatically generates `code_verifier` and `code_challenge`. You need to:
1. Save `code_verifier` locally
2. After successful authorization, send `code_verifier` to the server
3. Server uses `code_verifier` to exchange for `access_token`

## Compatibility and Limitations

### Android Platform

| Feature | Support Status | Description |
|------|----------|------|
| Client Authorization | ✅ Supported | Requires TikTok client installation |
| Web Authorization | ✅ Supported | As an alternative to client authorization |
| Universal Links | ❌ Not Supported | Not applicable on Android platform |
| Auto Authorization | ✅ Supported | Controllable via `autoAuthDisabled` |

### iOS Platform

| Feature | Support Status | Description |
|------|----------|------|
| Client Authorization | ✅ Supported | Requires TikTok client installation |
| Web Authorization | ✅ Supported | Uses ASWebAuthenticationSession |
| Universal Links | ✅ Supported | iOS 9+ supported, requires configuration |
| Auto Authorization | ✅ Supported | Controllable via `autoAuthDisabled` |

### Known Limitations

1. **Android 11+ Compatibility**: Some devices require adding `queries` configuration
2. **iOS 14+ Permissions**: Users will be prompted for confirmation on first authorization
3. **Simulator Testing**: Recommended to test on real devices, simulators may not work properly
4. **Multi-Account Switching**: Current version does not support account switching feature
5. **Offline Authorization**: Does not support offline refresh_token refresh

## Privacy and Permission Statement

### Permission List

| Permission | Purpose | Required |
|------|------|----------|
| INTERNET | Network communication, used for authorization flow | Required |
| ACCESS_NETWORK_STATE | Detect network status | Optional |
| ACCESS_WIFI_STATE | Detect WiFi status | Optional |

### Privacy Compliance Suggestions

1. **User Authorization Prompt**: Before initiating authorization, recommend explaining to the user the purpose of obtaining information
2. **Privacy Policy**: State in the application's privacy policy that TikTok login functionality is integrated
3. **Data Usage**: Obtained user data must comply with TikTok Developer Policies
4. **Age Restrictions**: If the application has age restrictions, handle corresponding authorization logic

### GDPR Compliance

For applications targeting European users:
1. Ensure users understand the scope of data collection before authorization
2. Provide data deletion mechanisms (via TikTok's data deletion API)
3. Explain data processing methods in the privacy policy

## Version History

### 1.2.0 (2026-08-27)

- 🆕 Added Universal Links support for iOS platform
- 🐞 Fixed authorization callback issues on Android platform
- 🐞 Fixed multiple authorization callback issues on iOS platform
- ⚡ Optimized authorization flow, improved success rate
- 📝 Updated documentation

### 1.1.0 (2026-08-25)

- 🆕 Added Android platform support
- 🆕 Added authorization cancellation callback
- 🐞 Fixed PKCE code_verifier verification issue
- ⚡ Optimized error message prompts

### 1.0.0 (2026-08-20)

- 🎉 Initial version release
- ✅ Basic authorization functionality for iOS platform
- ✅ Supports custom authorization scope
- ✅ Complete error handling

## Contributing

### Reporting Issues

If you discover issues or have suggestions, please feedback via the following methods:

1. Submit issues at [Gitee Issues](https://gitee.com/sugar_wei/tiktok-auth/issues)
2. Describe problem phenomena, reproduction steps, and expected behavior
3. Provide relevant logs and device information

### Submitting Code

1. Fork this project
2. Create a feature branch: `git checkout -b feature/your-feature`
3. Commit changes: `git commit -am 'Add some feature'`
4. Push to branch: `git push origin feature/your-feature`
5. Submit Pull Request

## References

- [TikTok for Developers](https://developers.tiktok.com/)
- [TikTok Login Documentation](https://developers.tiktok.com/doc/TikTok-Login-Quickstart)
- [OAuth 2.0 with PKCE](https://datatracker.ietf.org/doc/html/rfc7636)
- [uni-app Plugin Development Documentation](https://nativesupport.dcloud.net.cn/NativePlugin/README)
- [TikTok API Reference](https://developers.tiktok.com/doc/TikTok-API-Reference)

---

**Copyright Notice**: This plugin is open source under the MIT License. The author reserves the right of final interpretation.

**Disclaimer**: This plugin is for learning and research purposes only. Please read the TikTok Developer Agreement and relevant laws and regulations carefully before use.

---

## Support

☕ If you find this plugin helpful is an encouragement!


| Alipay | WeChat Pay | Buy Me a Coffee |
| :---: | :---: | :---: |
| <img src="./static/alpay.png" width="200" alt="Alipay QR Code" /> | <img src="./static/wechat.png" width="200" alt="WeChat QR Code" /> | <img src="https://cdn.buymeacoffee.com/buttons/v2/default-green.png" width="200" alt="Buy Me a Coffee QR placeholder" /> |
