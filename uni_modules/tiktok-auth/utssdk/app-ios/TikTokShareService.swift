//
//  TikTokShareService.swift
//  TikTok 分享 - 请求处理/响应分发（官方 Share Kit 源码移植并裁剪：
//  未安装 TikTok 不再弹 SFSafariViewController 引导下载，直接回调错误，
//  与登录"不做网页兜底"的交互策略保持一致）
//

import Foundation
import UIKit

fileprivate let USER_CANCELED_SHARE = "User canceled share"
fileprivate let TIKTOK_NOT_INSTALLED = "未安装 TikTok，请先安装 TikTok 应用后重试"

class TikTokShareService: NSObject, TikTokRequestResponseHandling {
    private(set) var completion: ((TikTokBaseResponse) -> Void)?
    private(set) var request: TikTokShareRequest?
    private let urlOpener: TikTokURLOpener

    init(urlOpener: TikTokURLOpener = UIApplication.shared) {
        self.urlOpener = urlOpener
    }

    //MARK: - TikTokRequestHandling
    func handleRequest(_ request: TikTokBaseRequest, completion: ((TikTokBaseResponse) -> Void)?) -> Bool {
        guard let shareRequest = request as? TikTokShareRequest else { return false }
        self.completion = completion
        self.request = shareRequest
        // 未安装 TikTok：不引导下载、不弹网页，直接回调错误（走 completion 链路，
        // UTS 侧能拿到自定义 message 并 toast）
        if !urlOpener.isTikTokInstalled() {
            if let errURL = constructErrorURL(errorCode: String(TikTokShareResponseErrorCode.common.rawValue),
                                              shareState: String(TikTokShareResponseState.tiktokIsNotInstalled.rawValue),
                                              errorDescription: TIKTOK_NOT_INSTALLED) {
                return handleResponseURL(url: errURL)
            }
            return false
        }
        guard let url = buildOpenURL(from: request) else { return false }
        (urlOpener as? UIApplication)?.open(url, options: [:]) { [weak self] success in
            if !success, let cancelURL = self?.constructErrorURL(errorCode: String(TikTokShareResponseErrorCode.cancelled.rawValue),
                                                                 shareState: String(TikTokShareResponseState.cancelled.rawValue),
                                                                 errorDescription: USER_CANCELED_SHARE) {
                _ = self?.handleResponseURL(url: cancelURL)
            }
        }
        return true
    }

    func buildOpenURL(from req: TikTokBaseRequest) -> URL? {
        guard let shareReq = req as? TikTokShareRequest else { return nil }
        guard let baseURL = URL(string: "\(TikTokInfo.universalLink)\(TikTokInfo.universalLinkSharePath)") else { return nil }
        guard var urlComps = URLComponents(url: baseURL, resolvingAgainstBaseURL: false) else { return nil }
        urlComps.queryItems = shareReq.convertToQueryParams()
        return urlComps.url
    }

    // MARK: - TikTokResponseHandling
    @discardableResult
    func handleResponseURL(url: URL) -> Bool {
        guard let res = try? TikTokShareResponse(fromURL: url, redirectURI: request?.redirectURI ?? "") else { return false }
        return handleResponse(res)
    }

    @discardableResult
    func handleResponse(_ response: TikTokShareResponse) -> Bool {
        guard let closure = completion else { return false }
        closure(response)
        return true
    }

    //MARK: - Construct error URL
    private func constructErrorURL(errorCode: String, shareState: String, errorDescription: String) -> URL? {
        guard let request = request else { return nil }
        guard let url = URL(string: request.redirectURI ?? "") else { return nil }
        guard var urlComps = URLComponents(url: url, resolvingAgainstBaseURL: false) else { return nil }
        urlComps.queryItems = [
            URLQueryItem(name: "request_id", value: request.requestID),
            URLQueryItem(name: "error_code", value: errorCode),
            URLQueryItem(name: "share_state", value: shareState),
            URLQueryItem(name: "error_description", value: errorDescription),
            URLQueryItem(name: "from_platform", value: TikTokInfo.shareScheme)
        ]
        return urlComps.url
    }
}
