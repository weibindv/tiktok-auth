package uts.sdk.modules.tiktokAuth

import android.app.Activity
import android.content.Intent
import android.os.Bundle

/**
 * redirect_uri 回调 Activity
 *
 * 两种场景都会走到这里：
 * 1. 未安装 TikTok App 时，Chrome Custom Tab 网页授权完成后重定向到 redirect_uri；
 * 2. 已安装 TikTok App 时（OpenSDK 2.x 实际行为），用户点"继续"后 TikTok 直接打开
 *    redirect_uri（https://code.hk.lingchuang.co?code=xxx&state=xxx，App Link），
 *    由本 Activity 拦截该 URL，解析结果并转交 TikTokAuthNative。
 *
 * App Links 验证（assetlinks.json 指纹与 APK 签名一致）是场景 2 能否送达的前提，
 * 详见《TikTok开发者后台配置清单.md》8.1 节。
 */
class TikTokAuthCallbackActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        TikTokAuthNative.handleWebAuthResult(this, intent)
        finish()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        TikTokAuthNative.handleWebAuthResult(this, intent)
        finish()
    }
}
