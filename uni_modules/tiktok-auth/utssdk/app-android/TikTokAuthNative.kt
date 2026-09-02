package uts.sdk.modules.tiktokAuth

import android.app.Activity
import android.content.ComponentName
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.util.Log
import com.tiktok.open.sdk.auth.AuthApi
import com.tiktok.open.sdk.auth.AuthRequest
import com.tiktok.open.sdk.auth.AuthResponse
import com.tiktok.open.sdk.auth.utils.PKCEUtils
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileInputStream
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
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
 *  - 未安装 TikTok：不做网页授权兜底，直接回调"未安装 TikTok"错误提示。
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

    /** 发起 TikTok 授权，返回 true=已拉起授权页或本次调用已消费（错误已回调） */
    fun authorize(
        activity: Activity,
        clientKey: String,
        redirectUri: String,
        scope: String,
        language: String?,
        autoAuthDisabled: Boolean, // 已忽略：内部强制 true（不做网页授权兜底）
        callback: (String) -> Unit
    ): Boolean {
        // 需求变更：不做网页授权兜底（不做向下兼容 web 登录）。
        // 未安装 TikTok 直接回调错误，不再降级 Chrome Custom Tab 网页授权。
        if (!isTikTokInstalled(activity)) {
            clear()
            callback(errorJson(-2, "未安装 TikTok，请先安装 TikTok 应用后重试"))
            return true
        }
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
            // autoAuthDisabled 强制 true：即使 SDK 内部再遇到未安装场景也不降级网页授权
            val request = AuthRequest(clientKey, scope, redirectUri, verifier, true, state, language)
            AuthApi(activity).authorize(request, AuthApi.AuthMethod.TikTokApp)
        } catch (e: Throwable) {
            clear()
            callback(errorJson(-2, e.message ?: "拉起 TikTok 授权失败"))
            false
        }
    }

    /** 检测是否安装 TikTok（国际版包名 + 部分地区版本），未安装返回 null */
    private fun installedTikTokPackage(context: Context): String? {
        val packages = listOf(
            "com.zhiliaoapp.musically", // 国际版 TikTok
            "com.ss.android.ugc.trill"  // TikTok（部分地区包名）
        )
        return packages.firstOrNull { pkg ->
            try {
                context.packageManager.getPackageInfo(pkg, 0)
                true
            } catch (e: Throwable) {
                false
            }
        }
    }

    /** 检测是否安装 TikTok（国际版包名 + 部分地区版本），未安装返回 false */
    private fun isTikTokInstalled(context: Context): Boolean =
        installedTikTokPackage(context) != null

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

    /**
     * CallbackActivity 统一分流入口：
     * - extras 带 `_aweme_open_sdk_params_type=4` → 分享结果（TikTok 分享完成后显式
     *   拉起 resultActivityFullPath 指定的本 Activity 投递 Bundle 结果）
     * - 其余（intent.data 为 https 回调 URL）→ 授权结果（登录 redirect_uri App Link）
     */
    fun dispatchCallbackIntent(activity: Activity, intent: Intent): String {
        val shareType = intent.extras?.getInt(KEY_SHARE_TYPE, -1) ?: -1
        return if (shareType == TYPE_SHARE_RESPONSE) {
            handleShareResultIntent(intent)
        } else {
            handleWebAuthResult(activity, intent)
        }
    }

    // =====================================================================
    //  TikTok 分享（自研实现）
    //
    //  官方 tiktok-open-sdk-share 未随插件打包（libs 仅 core/auth 两个 aar），
    //  这里按官方 Share Kit v2.3.1 的 Bundle 键值协议直接构造 Intent 拉起
    //  TikTok 的 SystemShareActivity：
    //  1. 媒体必须先写入公共 MediaStore（TikTok 只能读取公共存储/相册中的资源，
    //     与 iOS 端"先保存到相册再分享"策略一致）；
    //  2. 请求 Bundle 键值（与官方 ShareRequest.toBundle 输出完全一致）：
    //     _bytedance_params_type=3、_aweme_open_sdk_params_client_key、
    //     AWEME_EXTRA_IMAGE/VIDEO_MESSAGE_PATH(ArrayList<String>)、
    //     _aweme_open_sdk_params_share_format(0/1)、
    //     _aweme_open_sdk_params_caller_package、_aweme_open_sdk_params_caller_local_entry；
    //  3. 结果：TikTok 分享完成/取消后，显式拉起 resultActivityFullPath 指定的
    //     TikTokAuthCallbackActivity，extras 携带 _aweme_open_sdk_params_type=4 +
    //     state/error_code/error_msg/sub_error_code（见 handleShareResultIntent）。
    // =====================================================================

    /** 分享结果回调（UTS 闭包） */
    private var shareCallback: ((String) -> Unit)? = null

    /** 分享结果是否已回调 UTS：保证 success/fail 只触发一次 */
    @Volatile
    private var shareResultDelivered = false

    // Share Kit Bundle 键值（与官方 tiktok-open-sdk-share/constants 对齐）
    private const val TYPE_SHARE_REQUEST = 3
    private const val TYPE_SHARE_RESPONSE = 4
    private const val KEY_BASE_TYPE = "_bytedance_params_type"
    private const val KEY_SDK_NAME = "_aweme_params_caller_open_sdk_name"
    private const val KEY_SDK_VERSION = "_aweme_params_caller_open_sdk_version"
    private const val KEY_SHARE_TYPE = "_aweme_open_sdk_params_type"
    private const val KEY_CLIENT_KEY = "_aweme_open_sdk_params_client_key"
    private const val KEY_SHARE_FORMAT = "_aweme_open_sdk_params_share_format"
    private const val KEY_CALLER_PKG = "_aweme_open_sdk_params_caller_package"
    private const val KEY_CALLER_ENTRY = "_aweme_open_sdk_params_caller_local_entry"
    private const val KEY_STATE = "_aweme_open_sdk_params_state"
    private const val KEY_ERROR_CODE = "_aweme_open_sdk_params_error_code"
    private const val KEY_ERROR_MSG = "_aweme_open_sdk_params_error_msg"
    private const val KEY_SUB_ERROR_CODE = "_aweme_open_sdk_params_sub_error_code"
    private const val KEY_IMAGE_PATH = "AWEME_EXTRA_IMAGE_MESSAGE_PATH"
    private const val KEY_VIDEO_PATH = "AWEME_EXTRA_VIDEO_MESSAGE_PATH"
    private const val SHARE_SYSTEM_ACTIVITY = "com.ss.android.ugc.aweme.share.SystemShareActivity"
    private const val RESULT_ACTIVITY = "uts.sdk.modules.tiktokAuth.TikTokAuthCallbackActivity"
    private const val SHARE_SDK_NAME_VALUE = "TikTok-Open-Android-SDK-Share"
    private const val SHARE_SDK_VERSION_VALUE = "2.3.1"

    /**
     * 发起 TikTok 分享，返回 true=本次调用已消费（同步错误也回调后返回 true）
     * @param mediaType "video" / "image"
     * @param pathsJson JSON 数组字符串（本地绝对路径或 https URL）
     * @param callback 结果回调（JSON 字符串）
     */
    fun shareFiles(
        activity: Activity,
        clientKey: String,
        mediaType: String,
        pathsJson: String,
        greenScreen: Boolean,
        state: String,
        callback: (String) -> Unit
    ): Boolean {
        shareCallback = callback
        shareResultDelivered = false
        val tiktokPkg = installedTikTokPackage(activity)
        if (tiktokPkg == null) {
            deliverShare("{\"code\":-2,\"shareState\":20019,\"message\":\"未安装 TikTok，请先安装 TikTok 应用后重试\"}")
            return true
        }

        val isVideo = mediaType == "video"
        val paths = try {
            val arr = JSONArray(pathsJson)
            (0 until arr.length()).map { arr.getString(it) }
        } catch (e: Throwable) {
            emptyList()
        }
        if (paths.isEmpty()) {
            deliverShare("{\"code\":-2,\"shareState\":20002,\"message\":\"分享媒体路径不能为空\"}")
            return true
        }
        if (greenScreen && paths.size > 1) {
            deliverShare("{\"code\":-2,\"shareState\":20002,\"message\":\"绿幕模式仅支持分享单个媒体\"}")
            return true
        }

        // 后台准备媒体（远程下载/读取本地文件 → 写入 MediaStore），完成后主线程拉起 TikTok
        Thread {
            val uris = prepareMediaUris(activity, paths, isVideo)
            mainHandler.post {
                if (uris == null || uris.isEmpty()) {
                    deliverShare("{\"code\":-2,\"shareState\":21003,\"message\":\"媒体文件不存在或保存失败，请检查存储权限\"}")
                    return@post
                }
                val sent = launchShare(activity, clientKey, tiktokPkg, uris, isVideo, greenScreen, state)
                if (!sent) {
                    deliverShare("{\"code\":-2,\"shareState\":20002,\"message\":\"拉起 TikTok 分享失败\"}")
                }
            }
        }.start()
        return true
    }

    /** 把输入路径（本地文件 / https URL）统一写入公共 MediaStore，返回 content Uri 列表 */
    private fun prepareMediaUris(activity: Activity, paths: List<String>, isVideo: Boolean): List<Uri>? {
        val out = ArrayList<Uri>()
        for (raw in paths) {
            try {
                val input: InputStream? = if (raw.startsWith("http://") || raw.startsWith("https://")) {
                    val conn = URL(raw).openConnection() as HttpURLConnection
                    conn.connectTimeout = 20000
                    conn.readTimeout = 120000
                    conn.instanceFollowRedirects = true
                    conn.inputStream
                } else {
                    // 本地绝对路径（兼容 file:// 前缀，plus.io 解析出的路径常见带此前缀）
                    val localPath = if (raw.startsWith("file://")) {
                        Uri.parse(raw).path ?: raw
                    } else {
                        raw
                    }
                    val f = File(localPath)
                    if (!f.exists()) return null
                    FileInputStream(f)
                }
                if (input == null) return null
                input.use { ins ->
                    val uri = insertToMediaStore(activity, ins, raw, isVideo)
                        ?: return null
                    out.add(uri)
                }
            } catch (e: Throwable) {
                Log.e("TikTokAuth", "prepareMedia error: ${e.message}")
                return null
            }
        }
        return out
    }

    /** 写入 MediaStore：Android 10+ 用 RELATIVE_PATH（免存储权限）；<10 用 DATA（需 WRITE_EXTERNAL_STORAGE） */
    private fun insertToMediaStore(activity: Activity, ins: InputStream, source: String, isVideo: Boolean): Uri? {
        val resolver = activity.contentResolver
        val collection = if (isVideo) MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        else MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        val ext = guessExt(source, isVideo)
        val name = "tiktok_share_${UUID.randomUUID().toString().substring(0, 8)}.$ext"
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, name)
            put(MediaStore.MediaColumns.MIME_TYPE, if (isVideo) "video/mp4" else guessImageMime(ext))
            if (Build.VERSION.SDK_INT >= 29) {
                put(
                    MediaStore.MediaColumns.RELATIVE_PATH,
                    (if (isVideo) Environment.DIRECTORY_MOVIES else Environment.DIRECTORY_PICTURES) + "/TikTokShare"
                )
                val pendingKey = if (isVideo) MediaStore.Video.Media.IS_PENDING else MediaStore.Images.Media.IS_PENDING
                put(pendingKey, 1)
            } else {
                val dir = File(
                    Environment.getExternalStoragePublicDirectory(
                        if (isVideo) Environment.DIRECTORY_MOVIES else Environment.DIRECTORY_PICTURES
                    ),
                    "TikTokShare"
                )
                if (!dir.exists() && !dir.mkdirs()) return null
                put(MediaStore.MediaColumns.DATA, File(dir, name).absolutePath)
            }
        }
        val uri = resolver.insert(collection, values) ?: return null
        try {
            resolver.openOutputStream(uri)?.use { os -> ins.copyTo(os) }
                ?: run { resolver.delete(uri, null, null); return null }
            if (Build.VERSION.SDK_INT >= 29) {
                val pendingKey = if (isVideo) MediaStore.Video.Media.IS_PENDING else MediaStore.Images.Media.IS_PENDING
                val done = ContentValues().apply { put(pendingKey, 0) }
                resolver.update(uri, done, null, null)
            }
            // 双保险：授予 TikTok 读取权限（MediaStore 公共 URI 不授权 TikTok 也能按自身权限读取）
            for (pkg in listOf("com.zhiliaoapp.musically", "com.ss.android.ugc.trill")) {
                try {
                    activity.grantUriPermission(pkg, uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                } catch (e: Throwable) {
                }
            }
            return uri
        } catch (e: Throwable) {
            Log.e("TikTokAuth", "insertToMediaStore error: ${e.message}")
            try { resolver.delete(uri, null, null) } catch (e2: Throwable) { }
            return null
        }
    }

    private fun guessExt(source: String, isVideo: Boolean): String {
        val lower = source.substringBefore('?').toLowerCase()
        val candidates = if (isVideo) listOf("mp4", "mov", "3gp", "webm") else listOf("jpg", "jpeg", "png", "webp")
        val hit = candidates.firstOrNull { lower.endsWith(".$it") }
        return hit ?: if (isVideo) "mp4" else "jpg"
    }

    private fun guessImageMime(ext: String): String = when (ext) {
        "png" -> "image/png"
        "webp" -> "image/webp"
        else -> "image/jpeg"
    }

    /** 构造拉起 TikTok 分享页的 Intent（与官方 ShareApi.share 完全一致的 Bundle/Intent 形态） */
    private fun launchShare(
        activity: Activity,
        clientKey: String,
        tiktokPkg: String,
        uris: List<Uri>,
        isVideo: Boolean,
        greenScreen: Boolean,
        state: String
    ): Boolean {
        val pathKey = if (isVideo) KEY_VIDEO_PATH else KEY_IMAGE_PATH
        val bundle = Bundle().apply {
            putInt(KEY_BASE_TYPE, TYPE_SHARE_REQUEST)
            putString(KEY_SDK_NAME, SHARE_SDK_NAME_VALUE)
            putString(KEY_SDK_VERSION, SHARE_SDK_VERSION_VALUE)
            putString(KEY_CLIENT_KEY, clientKey)
            putStringArrayList(pathKey, ArrayList(uris.map { it.toString() }))
            putInt(KEY_SHARE_FORMAT, if (greenScreen) 1 else 0)
            putString(KEY_CALLER_PKG, activity.packageName)
            putString(KEY_CALLER_ENTRY, RESULT_ACTIVITY)
            if (state.isNotEmpty()) putString(KEY_STATE, state)
        }
        val intent = Intent().apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            component = ComponentName(tiktokPkg, SHARE_SYSTEM_ACTIVITY)
            putExtras(bundle)
            type = if (isVideo) "video/*" else "image/*"
            action = if (uris.size > 1) Intent.ACTION_SEND_MULTIPLE else Intent.ACTION_SEND
        }
        return try {
            activity.startActivity(intent)
            true
        } catch (e: Throwable) {
            Log.e("TikTokAuth", "launchShare error: ${e.message}")
            false
        }
    }

    /** 解析 TikTok 分享结果 Bundle（type=4）并回调 UTS；返回 JSON（App 侧暂不使用返回值） */
    private fun handleShareResultIntent(intent: Intent): String {
        val extras = intent.extras ?: return "{}"
        val state = extras.getString(KEY_STATE) ?: ""
        val errorCode = extras.getInt(KEY_ERROR_CODE, 0)
        val subErrorCode = extras.getInt(KEY_SUB_ERROR_CODE, 0)
        val errorMsg = extras.getString(KEY_ERROR_MSG) ?: ""
        Log.e("TikTokAuth", "handleShareResultIntent errorCode=$errorCode sub=$subErrorCode msg=$errorMsg state=$state")
        val json = if (errorCode == 0) {
            JSONObject()
                .put("code", 200)
                .put("shareState", subErrorCode)
                .put("message", errorMsg.ifEmpty { "分享成功" })
                .put("requestId", "")
                .put("state", state)
                .toString()
        } else {
            val cancel = isShareCancel(errorCode, subErrorCode, errorMsg)
            JSONObject()
                .put("code", if (cancel) -1 else -2)
                .put("shareState", subErrorCode)
                .put("message", errorMsg.ifEmpty { shareStateMessage(subErrorCode) })
                .put("state", state)
                .toString()
        }
        deliverShare(json)
        return json
    }

    private fun isShareCancel(errorCode: Int, subErrorCode: Int, errorMsg: String): Boolean {
        if (errorCode == -3) return true // BaseError.CANCELLED
        if (subErrorCode == 20013) return true
        return errorMsg.contains("cancel", ignoreCase = true)
    }

    private fun shareStateMessage(subErrorCode: Int): String = when (subErrorCode) {
        20013 -> "用户取消分享"
        20015, 20016 -> "已保存到草稿"
        10011 -> "Client Key 与包名签名不匹配，请检查 TikTok 开发者后台配置"
        20002 -> "参数解析错误"
        20005 -> "TikTok 无相册权限"
        20006 -> "TikTok 网络异常"
        20008 -> "图片不符合要求"
        20010 -> "处理图片资源失败"
        20012 -> "视频格式不支持"
        20019 -> "未安装 TikTok，请先安装 TikTok 应用后重试"
        else -> "分享失败($subErrorCode)"
    }

    /** 分享结果只回调 UTS 一次 */
    private fun deliverShare(json: String) {
        val cb = shareCallback
        if (cb == null || shareResultDelivered) return
        shareResultDelivered = true
        shareCallback = null
        cb(json)
    }

    /** 清空分享回调（发起新请求前重置） */
    private fun clearShare() {
        shareCallback = null
        shareResultDelivered = false
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
