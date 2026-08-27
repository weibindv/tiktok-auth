//
//  TikTokAuthNative.swift
//  TikTok 授权登录插件 - iOS 原生逻辑（UTS 混编）
//
//  Client Key 由 Info.plist 的 TikTokClientKey 提供（SDK 内部读取）。
//  授权结果经 TikTokURLHandler.handleOpenURL（App 收到 URL Scheme 回调时）触发。
//

import Foundation
import UIKit

@objc(TikTokAuthNative)
public class TikTokAuthNative: NSObject {

    /// 持有授权请求回调（send 的 completion），openURL 后由 TikTok SDK 触发
    static private var authCallback: ((String) -> Void)? = nil

    /// 强持有当前授权请求。
    /// TikTokAPI 内部以 NSMapTable(strongToWeakObjects) 弱引用持有 request，
    /// 若不强持有，authorize 返回后 req 即被释放，回调中将无法读取 req.pkce.codeVerifier。
    /// 官方 demo（ScopeEditViewController）同样以属性持有 authRequest。
    static private var pendingRequest: TikTokAuthRequest? = nil

    /// 结果是否已投递。原生 continueUserActivity 钩子与 JS 兜底（getWebAuthResult）
    /// 两条路径都可能触发回调，用此标记保证只回调 UTS 一次，避免重复调登录接口。
    static private var resultDelivered: Bool = false

    /// 发起 TikTok 授权登录
    /// - Parameters:
    ///   - redirectURI: 授权回调地址（TikTok 后台注册的 redirect_uri）
    ///   - scope: 权限范围，逗号分隔
    ///   - isWebAuth: true=强制网页授权；false=优先拉起 TikTok App
    ///   - callback: 结果回调（JSON 字符串）
    /// - Returns: 是否成功发起
    /// 注意：参数使用无外部标签（_），UTS 编译器生成的 Swift 调用为无标签形式
    @objc
    static public func authorize(_ redirectURI: String, _ scope: String, _ isWebAuth: Bool, _ callback: @escaping (String) -> Void) -> Bool {
        authCallback = callback
        resultDelivered = false

        var scopes = Set<String>()
        for s in scope.split(separator: ",") {
            let t = s.trimmingCharacters(in: .whitespaces)
            if !t.isEmpty { scopes.insert(String(t)) }
        }

        let req = TikTokAuthRequest(scopes: scopes, redirectURI: redirectURI)
        req.isWebAuth = isWebAuth
        req.state = UUID().uuidString
        pendingRequest = req

        // 闭包弱引用 req：req 由 pendingRequest 强持有，回调期间必然存活，
        // 同时避免 req → service → completion 闭包 → req 的循环引用
        let sent = req.send { [weak req] response in
            defer { pendingRequest = nil }
            NSLog("TikTokAuth native completion fired: response=\(response)")
            if TikTokAuthNative.resultDelivered { return }
            TikTokAuthNative.resultDelivered = true
            guard let req = req else {
                callback(self.jsonString(["code": -2, "message": "授权请求已释放"]))
                return
            }
            guard let authResp = response as? TikTokAuthResponse else {
                callback(self.jsonString(["code": -2, "message": "未知授权响应"]))
                return
            }
            let hasCode = !(authResp.authCode?.isEmpty ?? true)
            let isCancel = (authResp.errorCode == .cancelled)
            var dict: [String: Any] = [
                "code": (authResp.errorCode == .noError && hasCode) ? 200 : -2,
                "authCode": authResp.authCode ?? "",
                "state": authResp.state ?? "",
                "message": authResp.errorDescription ?? (authResp.error ?? "")
            ]
            if isCancel {
                dict["code"] = -1
            }
            // PKCE：code_verifier 由 TikTokAuthRequest.pkce 内部生成，
            // 必须随 authCode 一并返回给调用方，服务端以 authCode + code_verifier 换取 access_token
            // （参考官方 ScopeEditViewController.fetchAccessToken）
            dict["codeVerifier"] = req.pkce.codeVerifier
            if let perms = authResp.grantedPermissions {
                // 逗号分隔字符串，UTS 侧 split 解析
                dict["permissions"] = perms.joined(separator: ",")
            }
            callback(self.jsonString(dict))
        }

        if !sent {
            pendingRequest = nil
            callback("{\"code\":-2,\"message\":\"发起 TikTok 授权失败\"}")
        }
        return sent
    }

    /// App 收到 URL Scheme 回调时调用（由 UTSiOSHookProxy.applicationOpenURLOptions 转发）
    @objc
    static public func handleOpenURL(_ url: URL) -> Bool {
        return TikTokURLHandler.handleOpenURL(url)
    }

    /// App 通过 Universal Link 续接时调用
    /// （由 UTSiOSHookProxy.applicationContinueUserActivityRestorationHandler 转发，
    ///  对应官方 AppDelegate 的 application(_:continue:restorationHandler:)，
    ///  WebAuth / 网页授权回跳场景必须实现）
    @objc
    static public func handleContinueUserActivity(_ userActivity: NSUserActivity?) -> Bool {
        guard let activity = userActivity else { return false }
        NSLog("TikTokAuth handleContinueUserActivity webpageURL=\(String(describing: activity.webpageURL))")
        return TikTokURLHandler.handleOpenURL(activity.webpageURL)
    }

    /// JS 兜底入口（iOS）：
    /// Universal Link 回跳时，若 UTS 的 continueUserActivity 钩子未被运行时转发，
    /// App.vue 从 plus.runtime.arguments 取到回调 URL 后调用本方法，
    /// 借 SDK 解析出 authCode，并取回授权时生成的 codeVerifier（PKCE 必需）。
    @objc
    static public func getWebAuthResult(_ url: String) -> String {
        if resultDelivered {
            return "{\"code\":-2,\"message\":\"结果已投递\"}"
        }
        guard let parsed = URL(string: url) else {
            return "{\"code\":-2,\"message\":\"无效回调 URL\"}"
        }
        let verifier = pendingRequest?.pkce.codeVerifier ?? ""
        let redirectURI = pendingRequest?.redirectURI ?? ""
        NSLog("TikTokAuth getWebAuthResult url=\(url) verifierEmpty=\(verifier.isEmpty) redirectURI=\(redirectURI)")
        do {
            let response = try TikTokAuthResponse(fromURL: parsed, redirectURI: redirectURI, fromWeb: false)
            resultDelivered = true
            pendingRequest = nil
            authCallback = nil
            let hasCode = !(response.authCode?.isEmpty ?? true)
            var dict: [String: Any] = [
                "code": (response.errorCode == .noError && hasCode) ? 200 : -2,
                "authCode": response.authCode ?? "",
                "state": response.state ?? "",
                "message": response.errorDescription ?? (response.error ?? "")
            ]
            if response.errorCode == .cancelled {
                dict["code"] = -1
            }
            dict["codeVerifier"] = verifier
            if let perms = response.grantedPermissions {
                dict["permissions"] = perms.joined(separator: ",")
            }
            return jsonString(dict)
        } catch {
            return "{\"code\":-2,\"message\":\"解析回调失败: \(error)\"}"
        }
    }

    /// 清空持有的回调（App 被杀等场景兜底）
    @objc
    static public func clearCallback() {
        authCallback = nil
        pendingRequest = nil
    }

    static private func jsonString(_ dict: [String: Any]) -> String {
        do {
            let data = try JSONSerialization.data(withJSONObject: dict, options: [])
            if let json = String(data: data, encoding: .utf8) {
                return json
            }
        } catch {}
        return "{\"code\":-2,\"message\":\"序列化失败\"}"
    }
}
