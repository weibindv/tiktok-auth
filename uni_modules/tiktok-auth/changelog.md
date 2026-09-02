## 1.4.0（2026-09-02）
- 新增 TikTok 分享能力（Share Kit），与登录同属一个插件模块（uni_modules/tiktok-auth）：
  - 【Android】自研分享实现（官方 share AAR 未随 OpenSDK 提供）：按 ShareRequest Bundle
    协议（TYPE_SHARE_REQUEST=3）拉起 TikTok SystemShareActivity；媒体先写入 MediaStore
    （Android 10+ 用 RELATIVE_PATH/IS_PENDING 免存储权限，<10 用 DATA + 存储权限），
    支持本地路径（兼容 file:// 前缀）与远程 URL 自动下载；`clientKey` 必传
  - 【iOS】移植官方 Share Kit 源码（去 TikTokOpenSDKCore 跨模块 import，复用插件共享协议）：
    TikTokShareRequest/Response/Service/URLParams + TikTokShareNative 编排；媒体先保存到
    系统相册（PHPhotoLibrary .addOnly + NSPhotoLibraryAddUsageDescription，仅写入权限），
    再以 localIdentifier 组装 Universal Link 拉起 TikTok；clientKey 读 info.plist
  - 分享结果回调三重保险（沿用登录已验证模式）：原生 handleResponse → UTS 钩子 →
    App.vue 从 plus.runtime.arguments 取 URL 调 getShareResult(url) JS 兜底，
    shareResultDelivered 幂等去重
  - App.vue 回调分流：以 share_state / from_platform=tiktoksharesdk 专用参数识别分享回跳，
    与登录 Universal Link 回跳（code.hk.lingchuang.co 同域）互不误伤
  - 两端行为统一：未安装 TikTok 直接回调"未安装 TikTok"，不弹浏览器/商店引导
  - 支持图片/视频分享与绿幕（Green Screen）格式（绿幕仅限单个媒体）
- 限制：图片 1~35 张；视频 ≤12 个、3s~10min、mp4；双端媒体均需先写入系统相册/MediaStore
- iOS 新增依赖：Photos framework（config.json frameworks）；新增
  NSPhotoLibraryAddUsageDescription 权限说明
- JS 封装：common/tiktokShare.js 提供 Promise 式 tiktokShare()（默认 clientKey
  sbawwsw3d3nxrgc4b3，120s 超时兜底），冷启动结果经 uni._tiktokSharePending 暂存取回
- 修复【iOS】云打包报 `'TikTokShareMediaType' is ambiguous for type lookup in this context`：
  手写原生枚举与 interface.uts 导出的同名 UTS 类型在同一 Swift 模块内冲突（UTS 编译器会为
  导出的字符串字面量联合类型生成同名桥接符号），原生侧枚举改名为 TKShareMediaType
  （仅内部使用，JS/UTS 公共 API 不变）；已用 iOS SDK 对插件全部 Swift 源码做类型检查通过
## 1.3.0（2026-09-02）
- 移除网页授权兜底（不做向下兼容 web 登录）：
  - 【Android】authorize 前置检查 TikTok 是否安装（com.zhiliaoapp.musically /
    com.ss.android.ugc.trill），未安装直接回调"未安装 TikTok，请先安装 TikTok 应用后重试"，
    不再降级 Chrome Custom Tab；AuthRequest 的 autoAuthDisabled 强制 true 双保险
  - 【iOS】TikTokAuthService.handleRequest 未安装 TikTok 时直接构造错误响应
    （error_description=未安装提示），不再降级 ASWebAuthenticationSession；
    buildOpenURL 移除 isWebAuth 分支，一律走原生 App 授权
- 两端行为统一：已安装 → 拉起 TikTok 授权；未安装 → 提示未安装，不发起任何网页授权
## 1.2.0（2026-08-27）
- iOS Universal Link 回跳结果捕获加固：App.vue 注册 plus.runtime.on('arguments')
  事件 + onShow 延迟复查 + 全局暂存冷启动兜底；iOS index.uts 钩子签名回退官方声明
- 修复 iPhone 12 上 Can't find variable: forceWebAuth（移除 APP-IOS 条件编译，
  改运行时平台判断）
## 1.1.0（2026-08-25）
- 修复【Android】新版 TikTok App 点"继续"后授权结果不回调、误报"用户取消授权"：
  TikTok 2.x 实际通过 redirect_uri（App Link）返回结果（由 CallbackActivity 的
  onCreate/onNewIntent 接收），onActivityResult 仅收到 RESULT_CANCELED+null。
  新增 handleActivityResult 统一入口：兼容旧版 setResult 返回 + 新版 redirect 返回
  （1200ms 宽限期延迟判定），并修复回调被 clear() 竞态丢失、success/fail 重复触发问题
- 修复【iOS】网页授权拉不起来：presentationAnchor 改为遍历 connectedScenes 取
  keyWindow（原 keyWindow 在 iOS 13+ 场景化环境可能返回 nil）
- 【iOS】恢复官方推荐链路：已装 TikTok 优先拉起 App 授权（Universal Link 回跳），
  未装自动降级网页授权（common/tiktokLogin.js 不再强制 iOS 走网页授权）
- 【Android】Manifest queries 增加 CustomTabsService 声明，提升未装 TikTok 时
  网页授权降级的可靠性
- common/tiktokLogin.js 恢复 120s 超时兜底，避免授权流程异常时 Promise 永久挂起
## 1.0.0 (2026-08-20)

- 首个版本
- Android：基于 TikTok OpenSDK v2.3.1（aar 集成），支持 App 拉起 + WebAuth 降级
- iOS：基于 TikTok OpenSDK v2.3.1（Swift 源码混编），支持 App 拉起 + 网页授权
- 客户端获取 auth_code / code_verifier，服务端换 token
