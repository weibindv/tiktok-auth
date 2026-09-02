//
//  TikTokShareRequest.swift
//  TikTok 分享 - 分享请求（官方 Share Kit 源码移植，去掉 TikTokOpenSDKCore 模块 import，
//  适配 UTS 插件单模块编译；协议与常量均与插件内 auth 核心共用同一份）
//

import Foundation
import UIKit

/// 分享的媒体类型
/// 注意：命名不能与 interface.uts 导出的 UTS 类型同名（TikTokShareMediaType），
/// 否则 UTS 编译器生成的桥接符号与手写 Swift 类型在同一模块内冲突，
/// 报 "ambiguous for type lookup in this context"（云打包 iOS 实测）。
public enum TKShareMediaType: Int {
    case image = 0
    case video
}

/// 分享格式：normal 原样分享；greenScreen 作为绿幕背景
public enum TikTokShareFormatType: Int {
    case normal = 0
    case greenScreen
}

public class TikTokShareRequest: NSObject, TikTokBaseRequest {
    public class CustomConfiguration: NSObject {
        let clientKey: String
        let callerUrlScheme: String
        public init(clientKey: String, callerUrlScheme: String) {
            self.clientKey = clientKey
            self.callerUrlScheme = callerUrlScheme
            super.init()
        }
    }

    /// 分享请求 ID
    public var requestID: String = UUID().uuidString

    /// 相册中媒体资源的 localIdentifier 列表（必须全为图片或全为视频）。
    /// - 图片：1~35 张
    /// - 视频：最多 12 个，总时长 3s~10min，仅 mp4
    public var localIdentifiers: [String]?

    /// 分享回跳地址：必须是 TikTok 后台注册的 Universal Link（与登录共用 https://code.hk.lingchuang.co）
    public var redirectURI: String?

    /// 分享格式
    public var shareFormat: TikTokShareFormatType = .normal

    /// 媒体类型
    public var mediaType: TKShareMediaType = .image

    /// 请求状态标识（原样返回给调用方）
    public var state: String? = nil

    /// 自定义 client key / caller scheme（正常情况无需设置，从 Info.plist 的 TikTokClientKey 读取）
    public var customConfig: CustomConfiguration? = nil

    public lazy var service: TikTokRequestResponseHandling = TikTokShareService()

    public init(localIdentifiers: [String], mediaType: TKShareMediaType, redirectURI: String) {
        self.localIdentifiers = localIdentifiers
        self.redirectURI = redirectURI
        self.mediaType = mediaType
    }

    // MARK: - Public
    @discardableResult
    public func send(_ completion: ((TikTokBaseResponse) -> Void)? = nil) -> Bool {
        guard isValid else { return false }
        TikTokAPI.add(request: self)
        return service.handleRequest(self, completion: completion)
    }

    // MARK: - Private
    private var isValid: Bool {
        guard let localIdentifiers = localIdentifiers, localIdentifiers.count > 0 else {
            return false
        }
        return true
    }

    deinit {
        TikTokAPI.remove(requestID: self.requestID)
    }
}
