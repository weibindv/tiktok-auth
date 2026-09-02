//
//  TikTokShareRequest+URLParams.swift
//  TikTok 分享 - 请求参数序列化（官方源码移植，去掉 TikTokOpenSDKCore import）
//

import Foundation

extension TikTokShareRequest: TikTokURLQueryParamsConvertible {

    //MARK: - TikTokURLQueryParamsConvertible
    public func convertToQueryParams() -> [URLQueryItem] {
        return [
            URLQueryItem(name: "request_id", value: self.requestID),
            URLQueryItem(name: "key", value: self.customConfig?.clientKey ?? TikTokInfo.clientKey),
            URLQueryItem(name: "share_format", value: String(self.shareFormat.rawValue)),
            URLQueryItem(name: "api_version", value: TikTokInfo.currentVersion),
            URLQueryItem(name: "third_app_version", value: Bundle.main.object(forInfoDictionaryKey: "CFBundleShortVersionString") as? String ?? ""),
            URLQueryItem(name: "media_type", value: String(self.mediaType.rawValue)),
            URLQueryItem(name: "media_paths", value: self.localIdentifiers?.joined(separator: ",") ?? ""),
            URLQueryItem(name: "bundle_id", value: TikTokInfo.sha512BundleId),
            URLQueryItem(name: "sdk_name", value: TikTokInfo.shareSDKName),
            URLQueryItem(name: "redirect_uri", value: self.redirectURI ?? ""),
            URLQueryItem(name: "state", value: self.state ?? ""),
        ]
    }
}
