package uts.sdk.modules.tiktokAuth

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.os.Handler
import android.os.Looper
import com.tiktok.open.sdk.auth.AuthApi
import com.tiktok.open.sdk.auth.AuthRequest
import com.tiktok.open.sdk.auth.AuthResponse
import com.tiktok.open.sdk.auth.utils.PKCEUtils
import org.json.JSONObject
import java.util.UUID

/**
 * TikTok 授权登录原生逻辑（混编 Kotlin，供 UTS index 调用）
 *
 * 授权链路（TikTok OpenSDK 2.x 实际行为，与官方 demo-auth 一致）：
 *  - 发起：AuthApi.authorize 内部 startActivityForResult 拉起 TikTok 授权页；
 *  - 返回：新版 TikTok App 用户点"继续"后【不 setResult】，而是打开
 *    redirect_uri（https App Link），由 Android 解析到带对应 intent-filter 的
 *    TikTokAuthCallbackActivity（onCreate/onNewIntent）接收授权码。
 *    此时调用方 onActivityResult 只会收到 RESULT_CANCELED + data=null，
 *    绝不能直接判定为"用户取消"。
 *  - 旧版 TikTok App 通过 setResult + extras 返回（_bytedance_params_type=2），
 *    onActivityResult 可直接解析，两种形态都要兼容。
 *  - 未安装 TikTok：SDK 自动降级 Chrome Custom Tab 网页授权，重定向
 *    redirect_uri，同样由 CallbackActivity 拦截。
 */
object TikTokAuthNative {

    /** 授权结果回调（UTS 闭包）：App 拉起与网页授权两条链路共用 */
    private var webAuthCallback: ((String) -> Unit)? = null
    private var pendingCodeVerifier: String? = null
    private var pendingRedirectUri: String = ""

    /** redirect_uri 回调（CallbackActivity）是否已收到结果：用于阻断 cancel 兜底误报 */
    @Volatile
    private var redirectResultReceived = false

    /** 最终结果是否已回调 UTS：保证 success/fail 只触发一次 */
    @Volatile
    private var resultDelivered = false

    private val mainHandler = Handler(Looper.getMainLooper())
    private var cancelFallbackRunnable: Runnable? = null

    /**
     * onActivityResult 收到"无数据取消"后，等待 redirect_uri 回调的宽限期。
     * TikTok 点"继续"后系统需要先把 redirect intent 路由到 CallbackActivity，
     * 宽限期内收到则正常 success；超时未收到（用户真按返回键取消 / App Links
     * 验证失败回调丢失）才回调 fail。真取消场景用户感知延迟即此值。
     */
    private const val CANCEL_GRACE_MS = 1200L

    private const val PREFS_NAME = "tiktok_auth_store"
    private const val KEY_VERIFIER = "pending_code_verifier"

    /**
     * codeVerifier 只在 authorize 时生成一次，之后从内存/本地存储读取：
     * - 网页授权期间 App 进入后台，很可能被系统回收进程（内存静态变量丢失）；
     *   授权完成 scheme 冷启动回跳时，必须能从本地存储恢复，否则拿不到 codeVerifier。
     * - 存储保留到下次 authorize 覆盖，不清空，保证冷启动兜底路径也能读到。
     */
    private fun prefs(activity: Activity): SharedPreferences =
        activity.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private fun resolveVerifier(activity: Activity): String {
        pendingCodeVerifier?.let { return it }
        return prefs(activity).getString(KEY_VERIFIER, "") ?: ""
    }

    /** 发起 TikTok 授权，返回 true=已拉起授权页 */
    fun authorize(
        activity: Activity,
        clientKey: String,
        redirectUri: String,
        scope: String,
        language: String?,
        autoAuthDisabled: Boolean,
        callback: (String) -> Unit
    ): Boolean {
        webAuthCallback = callback
        pendingRedirectUri = redirectUri
        redirectResultReceived = false
        resultDelivered = false
        cancelFallbackRunnable?.let { mainHandler.removeCallbacks(it) }
        cancelFallbackRunnable = null
        val verifier = PKCEUtils.generateCodeVerifier()
        pendingCodeVerifier = verifier
        // 持久化：防止授权期间 App 进程被回收、冷启动回跳时内存变量丢失
        prefs(activity).edit().putString(KEY_VERIFIER, verifier).apply()
        return try {
            // state：官方建议自定义随机值防 CSRF（回调中校验 state 与请求时一致），
            // 传 null 时 SDK 不生成，建议显式生成
            val state = UUID.randomUUID().toString()
            val request = AuthRequest(clientKey, scope, redirectUri, verifier, autoAuthDisabled, state, language)
            AuthApi(activity).authorize(request, AuthApi.AuthMethod.TikTokApp)
        } catch (e: Throwable) {
            clear()
            callback(errorJson(-2, e.message ?: "拉起 TikTok 授权失败"))
            false
        }
    }

    /**
     * TikTokApp 模式：onActivityResult 统一入口（UTS 侧 UTSAndroid.onAppActivityResult 回调后调用）。
     *
     * 三种结果形态：
     * 1. 旧版 TikTok setResult 返回 extras → 同步解析立即回调；
     * 2. 新版 TikTok 点"继续"→ redirect_uri App Link 返回（onActivityResult 仅收到
     *    RESULT_CANCELED+null）→ 挂起等待 CallbackActivity 的回调，宽限期内等到则由
     *    webAuthCallback 链路回调 success，本方法不再回调；超时未收到才报取消/失败；
     * 3. 用户按返回键真取消 → 同 2 形态，宽限期后报取消（延迟约 1.2s）。
     */
    fun handleActivityResult(
        activity: Activity,
        intent: Intent?,
        redirectUri: String,
        resultCode: Int,
        callback: (String) -> Unit
    ) {
        val verifier = resolveVerifier(activity)
        android.util.Log.e(
            "TikTokAuth",
            "handleActivityResult resultCode=$resultCode ok=${Activity.RESULT_OK} intent=$intent data=${intent?.data} extrasKeys=${intent?.extras?.keySet()}"
        )
        // 1. 先尝试同步解析（兼容 setResult 返回数据的 TikTok 版本）
        val response = try {
            intent?.let { AuthApi(activity).getAuthResponseFromIntent(it, redirectUri) }
        } catch (e: Throwable) {
            android.util.Log.e("TikTokAuth", "parse from intent error: ${e.message}")
            null
        }
        if (response != null) {
            android.util.Log.e("TikTokAuth", "handleActivityResult: parsed from intent, authCode=${response.authCode}")
            clear()
            deliverOnce(callback, responseJson(response, verifier))
            return
        }
        // 2. 无数据：可能是 TikTok 正在通过 redirect_uri（App Link）返回，也可能是真取消。
        //    延迟判定，给 CallbackActivity 接收 redirect 的机会。
        android.util.Log.e(
            "TikTokAuth",
            "handleActivityResult: no parseable data, wait ${CANCEL_GRACE_MS}ms for redirect callback"
        )
        val runnable = Runnable {
            cancelFallbackRunnable = null
            if (!redirectResultReceived && !resultDelivered) {
                android.util.Log.e("TikTokAuth", "handleActivityResult: grace expired, redirect not received")
                clear()
                val json = if (resultCode != Activity.RESULT_OK) {
                    errorJson(-1, "用户取消授权")
                } else {
                    errorJson(-2, "未获取到授权结果")
                }
                deliverOnce(callback, json)
            }
        }
        cancelFallbackRunnable = runnable
        mainHandler.postDelayed(runnable, CANCEL_GRACE_MS)
    }

    /**
     * 兜底入口：App 通过自定义 scheme（dramaplayhouse://）被拉起、回调未经过
     * CallbackActivity 时，由 UTS/JS 侧把重构成的 HTTPS 回调 URL 传进来，
     * 借 SDK 解析出 authCode，并取回授权时生成的 codeVerifier。
     * 注意：不触发 webAuthCallback、不清空 pending，避免与插件 Promise 重复回调。
     */
    fun getWebAuthResult(activity: Activity, url: String): String {
        val verifier = resolveVerifier(activity)
        return try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
            val response = AuthApi(activity).getAuthResponseFromIntent(intent, pendingRedirectUri)
            if (response == null) errorJson(-2, "未获取到授权结果")
            else responseJson(response, verifier)
        } catch (e: Throwable) {
            errorJson(-2, e.message ?: "解析授权结果异常")
        }
    }

    /**
     * redirect_uri 回调统一入口：
     * - 网页授权（Chrome Custom Tab）完成后重定向；
     * - 新版 TikTok App 点"继续"后通过 App Link 返回。
     * 由 TikTokAuthCallbackActivity 的 onCreate/onNewIntent 调用，解析并触发 UTS 回调。
     */
    fun handleWebAuthResult(activity: Activity, intent: Intent): String {
        // 标记 redirect 结果已到达，阻断 handleActivityResult 的 cancel 兜底
        redirectResultReceived = true
        cancelFallbackRunnable?.let {
            mainHandler.removeCallbacks(it)
            cancelFallbackRunnable = null
        }
        val verifier = resolveVerifier(activity)
        android.util.Log.e(
            "TikTokAuth",
            "handleWebAuthResult data=${intent.data} pendingRedirectUri=$pendingRedirectUri starts=${intent.data?.toString()?.startsWith(pendingRedirectUri)}"
        )
        val json = try {
            val response = AuthApi(activity).getAuthResponseFromIntent(intent, pendingRedirectUri)
            if (response == null) errorJson(-2, "未获取到授权结果")
            else responseJson(response, verifier)
        } catch (e: Throwable) {
            errorJson(-2, e.message ?: "解析授权结果异常")
        }
        val cb = webAuthCallback
        clear()
        // 先取回调再 clear，避免回调丢失；deliverOnce 保证只回调一次
        cb?.let { deliverOnce(it, json) }
        return json
    }

    /** 保证最终结果只回调 UTS 一次（success/fail 不重复触发） */
    private fun deliverOnce(callback: (String) -> Unit, json: String) {
        if (resultDelivered) return
        resultDelivered = true
        callback(json)
    }

    private fun responseJson(response: AuthResponse, verifier: String): String {
        val json = JSONObject()
        // getAuthError() 非空 = 授权失败（E10008 为用户取消）
        val error = response.authError
        if (error != null && error.isNotEmpty()) {
            json.put("code", if (error == "E10008") -1 else -2)
            json.put("errCode", error)
            json.put("message", response.authErrorDescription ?: error)
            return json.toString()
        }
        json.put("code", 200)
        json.put("authCode", response.authCode ?: "")
        json.put("codeVerifier", verifier)
        json.put("state", response.state ?: "")
        // grantedPermissions 为逗号分隔的字符串，原样输出，UTS 侧 split 解析
        json.put("permissions", response.grantedPermissions ?: "")
        return json.toString()
    }

    private fun errorJson(code: Int, message: String): String {
        return JSONObject()
            .put("code", code)
            .put("message", message)
            .toString()
    }

    private fun clear() {
        webAuthCallback = null
        pendingCodeVerifier = null
        pendingRedirectUri = ""
    }
}
