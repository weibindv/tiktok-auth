package uts.sdk.modules.tiktokAuth

import android.app.Activity
import android.content.Intent
import android.os.Bundle

/**
 * 回调 Activity（授权 redirect_uri 与分享结果共用）
 *
 * 两种场景都会走到这里：
 * 1. 登录：新版 TikTok App 用户点"继续"后直接打开 redirect_uri
 *    （https://code.hk.lingchuang.co?code=xxx&state=xxx，App Link），由本 Activity 拦截
 *    该 URL，解析结果并转交 TikTokAuthNative.handleWebAuthResult；
 * 2. 分享：TikTok 分享完成/取消后，显式拉起 ShareRequest.resultActivityFullPath 指向的
 *    本 Activity（extras 携带 _aweme_open_sdk_params_type=4 + error_code/error_msg 等），
 *    由 TikTokAuthNative.dispatchCallbackIntent 识别并分发到分享结果处理。
 *
 * App Links 验证（assetlinks.json 指纹与 APK 签名一致）是场景 1 能否送达的前提，
 * 详见《TikTok开发者后台配置清单.md》8.1 节。
 */
class TikTokAuthCallbackActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        TikTokAuthNative.dispatchCallbackIntent(this, intent)
        finish()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        TikTokAuthNative.dispatchCallbackIntent(this, intent)
        finish()
    }
}
