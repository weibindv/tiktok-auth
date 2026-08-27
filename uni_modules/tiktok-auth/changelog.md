## 1.2.0（2026-08-27）
0.01
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
