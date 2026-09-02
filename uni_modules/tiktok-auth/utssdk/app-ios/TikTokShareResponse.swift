//
//  TikTokShareResponse.swift
//  TikTok 分享 - 响应与错误码（官方 Share Kit 源码移植，去掉 TikTokOpenSDKCore import）
//

import Foundation

/// 分享结果状态（TikTok 返回）
public enum TikTokShareResponseState: Int {
    /// Success
    case success = 20000
    /// unknown, maybe because the SDK version is too low
    case unknownError = 20001
    /// Param parsing error
    case paramInvalid = 20002
    /// Permission not granted
    case sharePermissionDenied = 20003
    /// User didn't not log in
    case userNotLogin = 20004
    /// No album permissions
    case noPhotoLibraryPermission = 20005
    /// Network issue
    case networkError = 20006
    /// Video length doesn't meet TikTok requrements
    case videoTimeLimitError = 20007
    /// Photo resolution doesn't meet TikTok requirements
    case photoResolutionError = 20008
    /// Timestamp checking failed
    case timeStampError = 20009
    /// Processing photo resources failed
    case handleMediaError = 20010
    /// Video resolution doesn't meet TikTok Requirements
    case videoResolutionError = 20011
    /// Unsupported video format
    case videoFormatError = 20012
    /// Sharing canceled by user
    case cancelled = 20013
    /// Another video is being uploaded
    case haveUploadedTask = 20014
    /// User saved the shared content as a draft
    case saveAsDraft = 20015
    /// Posting shared contents failed
    case publishFailed = 20016
    /// TikTok is not Installed on user's device
    case tiktokIsNotInstalled = 20019
    /// Downloading media from iCloud failed
    case mediaInICloudError = 21001
    /// Internal params parsing error
    case paramsParsingError = 21002
    /// Media resources do not exist
    case getMediaError = 21003
    /// Upgrade TikTok Version
    case upgradeTikTokVersion = 22000
    /// Logged in user different than other platform
    case differentUserLoggedIn = 22001
    /// User has no dm permissions
    case noDMPermission = 22002
    /// Share to DM failed
    case shareToDMFailed = 22003
    /// Share to DM parameter is missing
    case dmParameterMissing = 22004
    ///  dmSchema is invalid
    case dmSchemaInvalid = 22005
}

/// 分享错误码（TikTok 返回）
public enum TikTokShareResponseErrorCode: Int {
    case noError = 0
    case common = -1
    case cancelled = -2
    case failed = -3
    case denied = -4
    case unsupported = -5
    case missingParams = 10005
    case unknown = 100000
}

public class TikTokShareResponse: NSObject, TikTokBaseResponse {
    /// Response ID
    public var responseID: String?

    /// Request ID
    public var requestID: String?

    /// Request state
    public var state: String?

    /// Response result state
    public var shareState: TikTokShareResponseState = .success

    /// Error description
    public var errorDescription: String?

    /// Response error code
    public var errorCode: TikTokShareResponseErrorCode = .noError

    // MARK: - Public
    public init(fromURL url: URL, redirectURI: String) throws {
        guard let comps = URLComponents(url: url, resolvingAgainstBaseURL: false) else {
            throw TikTokResponseError.failToParseURL
        }
        guard let dict = comps.queryItems?.reduce(into: [String: String](), {
            $0[$1.name] = $1.value
        }) else {
            throw TikTokResponseError.failToParseURL
        }
        guard url.absoluteString.hasPrefix(redirectURI) else {
            throw TikTokResponseError.invalidRedirectURI
        }

        requestID        = dict["request_id"]
        responseID       = dict["response_id"]
        errorDescription = dict["error_description"]
        state            = dict["state"]

        if let state = dict["share_state"], let stateInt = Int(state) {
            shareState = TikTokShareResponseState(rawValue: stateInt) ?? .unknownError
        }
        if let error = (dict["error_code"] ?? dict["errCode"]), let errorInt = Int(error) {
            errorCode = TikTokShareResponseErrorCode(rawValue: errorInt) ?? .unknown
        }
    }
}
