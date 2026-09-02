//
//  TikTokShareNative.swift
//  TikTok 分享 - iOS 原生编排（UTS 混编入口）
//
//  分享链路：
//  1. 保存媒体：JS 传入本地文件路径或远程 URL（video/image），先保存到系统相册
//     （TikTok 只能读取相册中的资源，PHAsset localIdentifier 是官方要求的输入），
//     保存成功后取得各资源的 localIdentifier；
//  2. 拉起 TikTok：TikTokShareRequest.send 打开 TikTok 分享/发布页（Universal Link）；
//  3. 结果回跳：TikTok 把结果 redirect 到 redirect_uri（https://code.hk.lingchuang.co），
//     由 UTSiOSHookProxy 钩子 → TikTokURLHandler → TikTokAPI → TikTokShareService
//     分发到 completion 回调；若钩子未触发，App.vue 可从 plus.runtime.arguments
//     取到回调 URL 再调 getShareResult(url) 兜底（与登录 getWebAuthResult 同理）。
//
//  媒体保存至相册需 NSPhotoLibraryAddUsageDescription（插件 info.plist 已配）。
//

import Foundation
import UIKit
import Photos

@objc(TikTokShareNative)
public class TikTokShareNative: NSObject {

    /// 分享结果回调（UTS 闭包）
    static private var shareCallback: ((String) -> Void)? = nil

    /// 强持有当前分享请求。TikTokAPI 以弱引用持有 request，不强持有会被释放
    /// 导致回调中拿不到 requestID/redirectURI。
    static private var pendingShareRequest: TikTokShareRequest? = nil

    /// 结果是否已投递：原生 completion 与 JS 兜底（getShareResult）都可能触发，保证只回调一次
    static private var shareResultDelivered: Bool = false

    // MARK: - 入口

    /// 分享媒体文件（本地路径或远程 URL），先保存到相册再拉起 TikTok
    /// - Parameters:
    ///   - redirectURI: 分享回跳 Universal Link（与登录共用）
    ///   - mediaType: "video" / "image"
    ///   - pathsJson: JSON 数组字符串，如 ["https://cdn.xxx/a.mp4"] 或 ["/var/.../a.mp4"]
    ///   - greenScreen: true=绿幕模式（仅支持单个媒体）
    ///   - state: 请求状态标识（可选，原样回传）
    ///   - callback: 结果回调（JSON 字符串）
    /// - Returns: true=已消费本次调用（同步错误也回调后返回 true）
    @objc
    static public func shareFiles(_ redirectURI: String, _ mediaType: String, _ pathsJson: String,
                                  _ greenScreen: Bool, _ state: String,
                                  _ callback: @escaping (String) -> Void) -> Bool {
        shareCallback = callback
        shareResultDelivered = false
        pendingShareRequest = nil

        // 1. 未安装 TikTok 直接报错（不做网页/App Store 兜底）
        if !UIApplication.shared.isTikTokInstalled() {
            deliver("{\"code\":-2,\"shareState\":20019,\"message\":\"未安装 TikTok，请先安装 TikTok 应用后重试\"}")
            return true
        }

        // 2. 解析路径列表
        var paths: [String] = []
        do {
            if let data = pathsJson.data(using: .utf8),
               let arr = try JSONSerialization.jsonObject(with: data) as? [String] {
                paths = arr
            }
        } catch {}
        guard !paths.isEmpty else {
            deliver("{\"code\":-2,\"shareState\":20002,\"message\":\"分享媒体路径不能为空\"}")
            return true
        }

        // 3. 准备媒体：解析远程 URL / 本地路径
        prepareMedia(paths: paths, mediaType: mediaType) { localUrls in
            DispatchQueue.main.async {
                guard let urls = localUrls, !urls.isEmpty else {
                    TikTokShareNative.deliver("{\"code\":-2,\"shareState\":21003,\"message\":\"媒体文件不存在或下载失败\"}")
                    return
                }
                // 4. 保存到相册拿 localIdentifier
                TikTokShareNative.saveToPhotoLibrary(urls: urls, isVideo: mediaType == "video") { localIds in
                    DispatchQueue.main.async {
                        guard let ids = localIds, !ids.isEmpty else {
                            TikTokShareNative.deliver("{\"code\":-2,\"shareState\":21003,\"message\":\"保存到相册失败，请检查相册权限\"}")
                            return
                        }
                        // 5. 发起分享
                        TikTokShareNative.doSend(redirectURI: redirectURI, localIds: ids,
                                    isVideo: mediaType == "video",
                                    greenScreen: greenScreen, state: state)
                    }
                }
            }
        }
        return true
    }

    // MARK: - 媒体准备（远程下载 / 本地路径）

    /// 把输入路径统一成本地文件 URL 数组；远程地址先下载到 tmp。
    static private func prepareMedia(paths: [String], mediaType: String,
                                     done: @escaping ([URL]?) -> Void) {
        var results: [URL] = []
        let group = DispatchGroup()
        var failed = false

        for raw in paths {
            group.enter()
            if raw.hasPrefix("http://") || raw.hasPrefix("https://") {
                guard let url = URL(string: raw) else {
                    failed = true; group.leave(); continue
                }
                let ext = (url.pathExtension.isEmpty ? (mediaType == "video" ? "mp4" : "jpg") : url.pathExtension)
                let dest = FileManager.default.temporaryDirectory
                    .appendingPathComponent("tiktok_share_\(UUID().uuidString).\(ext)")
                let task = URLSession.shared.downloadTask(with: url) { tmpUrl, _, err in
                    defer { group.leave() }
                    guard err == nil, let tmpUrl = tmpUrl,
                          (try? FileManager.default.moveItem(at: tmpUrl, to: dest)) != nil else {
                        failed = true
                        return
                    }
                    results.append(dest)
                }
                task.resume()
            } else {
                // 本地绝对路径
                let url = URL(fileURLWithPath: raw)
                if FileManager.default.fileExists(atPath: raw) {
                    results.append(url)
                } else {
                    failed = true
                }
                group.leave()
            }
        }
        group.notify(queue: .main) {
            done(failed ? nil : results)
        }
    }

    // MARK: - 保存到相册

    /// 把本地文件保存进系统相册，返回各资源的 localIdentifier（顺序与输入一致）。
    /// 需要 NSPhotoLibraryAddUsageDescription；权限未授权时返回 nil。
    static private func saveToPhotoLibrary(urls: [URL], isVideo: Bool,
                                           done: @escaping ([String]?) -> Void) {
        let requestAddOnly: (() -> Void) = {
            if #available(iOS 14, *) {
                PHPhotoLibrary.requestAuthorization(for: .addOnly) { status in
                    if status == .authorized || status == .limited {
                        performSave(urls: urls, isVideo: isVideo, done: done)
                    } else {
                        done(nil)
                    }
                }
            } else {
                PHPhotoLibrary.requestAuthorization { status in
                    if status == .authorized {
                        performSave(urls: urls, isVideo: isVideo, done: done)
                    } else {
                        done(nil)
                    }
                }
            }
        }
        if Thread.isMainThread { requestAddOnly() } else { DispatchQueue.main.async(execute: requestAddOnly) }
    }

    static private func performSave(urls: [URL], isVideo: Bool, done: @escaping ([String]?) -> Void) {
        var placeholders: [PHObjectPlaceholder] = []
        PHPhotoLibrary.shared().performChanges({
            for url in urls {
                let creation = PHAssetCreationRequest.forAsset()
                let opts = PHAssetResourceCreationOptions()
                opts.shouldMoveFile = false
                if isVideo {
                    creation.addResource(with: .video, fileURL: url, options: opts)
                } else {
                    creation.addResource(with: .photo, fileURL: url, options: opts)
                }
                if let ph = creation.placeholderForCreatedAsset {
                    placeholders.append(ph)
                }
            }
        }, completionHandler: { success, err in
            guard success, placeholders.count == urls.count else {
                done(nil)
                return
            }
            done(placeholders.map { $0.localIdentifier })
        })
    }

    // MARK: - 发送

    static private func doSend(redirectURI: String, localIds: [String],
                               isVideo: Bool, greenScreen: Bool, state: String) {
        let req = TikTokShareRequest(
            localIdentifiers: localIds,
            mediaType: isVideo ? .video : .image,
            redirectURI: redirectURI
        )
        req.shareFormat = greenScreen ? .greenScreen : .normal
        req.state = state.isEmpty ? nil : state
        pendingShareRequest = req
        shareResultDelivered = false

        let sent = req.send { [weak req] response in
            defer { pendingShareRequest = nil }
            guard let req = req else { return }
            if shareResultDelivered { return }
            guard let shareResp = response as? TikTokShareResponse else {
                deliver("{\"code\":-2,\"message\":\"未知分享响应\"}")
                return
            }
            deliver(shareRespToJson(shareResp, requestID: req.requestID))
        }
        if !sent {
            pendingShareRequest = nil
            deliver("{\"code\":-2,\"shareState\":20002,\"message\":\"分享参数无效或拉起 TikTok 失败\"}")
        }
    }

    // MARK: - JS 兜底桥

    /// JS 兜底入口：App.vue 从 plus.runtime.arguments 取到分享回跳 URL 后调用，
    /// 借 SDK 解析出分享结果（原生 completion 钩子未触发时走这条路）。
    @objc
    static public func getShareResult(_ url: String) -> String {
        if shareResultDelivered {
            return "{\"code\":-2,\"message\":\"结果已投递\"}"
        }
        guard let parsed = URL(string: url) else {
            return "{\"code\":-2,\"message\":\"无效回调 URL\"}"
        }
        let redirectURI = pendingShareRequest?.redirectURI ?? ""
        NSLog("TikTokShare getShareResult url=\(url) redirectURI=\(redirectURI)")
        do {
            let response = try TikTokShareResponse(fromURL: parsed, redirectURI: redirectURI)
            shareResultDelivered = true
            let requestID = pendingShareRequest?.requestID ?? ""
            pendingShareRequest = nil
            shareCallback = nil
            return shareRespToJson(response, requestID: requestID)
        } catch {
            return "{\"code\":-2,\"message\":\"解析分享回调失败: \(error)\"}"
        }
    }

    /// 清空持有的回调（App 被杀等场景兜底）
    @objc
    static public func clearCallback() {
        shareCallback = nil
        pendingShareRequest = nil
    }

    // MARK: - 工具

    static private func shareRespToJson(_ resp: TikTokShareResponse, requestID: String) -> String {
        var code: Int
        if resp.errorCode == .noError {
            code = 200
        } else if resp.errorCode == .cancelled {
            code = -1
        } else {
            code = -2
        }
        let dict: [String: Any] = [
            "code": code,
            "shareState": resp.shareState.rawValue,
            "errorCode": resp.errorCode.rawValue,
            "message": resp.errorDescription ?? shareStateMessage(resp.shareState),
            "requestId": resp.requestID ?? requestID,
            "responseId": resp.responseID ?? "",
            "state": resp.state ?? ""
        ]
        return jsonString(dict)
    }

    static private func shareStateMessage(_ state: TikTokShareResponseState) -> String {
        switch state {
        case .success: return "分享成功"
        case .cancelled: return "用户取消分享"
        case .saveAsDraft: return "已保存到草稿"
        case .tiktokIsNotInstalled: return "未安装 TikTok，请先安装 TikTok 应用后重试"
        case .sharePermissionDenied: return "TikTok 未授权分享"
        case .userNotLogin: return "TikTok 未登录"
        case .noPhotoLibraryPermission: return "TikTok 无相册权限"
        case .videoTimeLimitError: return "视频时长不符合要求"
        case .photoResolutionError: return "图片分辨率不符合要求"
        case .videoResolutionError: return "视频分辨率不符合要求"
        case .videoFormatError: return "视频格式不支持"
        case .mediaInICloudError: return "从 iCloud 下载媒体失败"
        case .getMediaError: return "媒体资源不存在"
        case .publishFailed: return "发布失败"
        case .networkError: return "网络异常"
        default: return "分享失败(\(state.rawValue))"
        }
    }

    static private func deliver(_ json: String) {
        guard let cb = shareCallback else { return }
        shareCallback = nil
        shareResultDelivered = true
        cb(json)
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
