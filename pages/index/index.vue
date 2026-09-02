<template>
	<view class="content">
		<image class="logo" src="/static/logo.png"></image>
		<text class="title">{{statusText}}</text>

		<!-- TikTok 授权登录按钮 -->
		<button class="login-btn" type="primary" @click="onTikTokLogin">TikTok 登录</button>

		<!-- 授权结果展示（调试用） -->
		<view v-if="result" class="result">
			<text class="result-label">授权结果：</text>
			<text class="result-value">{{result}}</text>
		</view>
	</view>
</template>

<script>
	// 引入 UTS 插件导出的授权方法
	import { tiktokLogin } from '@/uni_modules/tiktok-auth'

	export default {
		data() {
			return {
				title: 'TikTok 授权示例',
				statusText: '点击按钮使用 TikTok 登录',
				result: '',
				// 幂等标记：原生 onActivityResult / onAppActivityResult 钩子
				// 与 App.vue 的 Universal Link 兜底两条路径都可能触发回调，
				// 用此标记防止重复调用登录接口
				_tiktokDone: false
			}
		},
		onLoad() {
			// iOS 网页授权（Universal Link）回跳兜底：
			// 若 UTS 的原生 continueUserActivity 钩子未转发，App.vue 会从
			// plus.runtime.arguments 取到回调 URL 并 emit 事件，这里消费它。
			// #ifdef APP-PLUS
			uni.$on('tiktok:auth:result', this.onTikTokAuthResult)
			// #endif
		},
		onUnload() {
			// #ifdef APP-PLUS
			uni.$off('tiktok:auth:result', this.onTikTokAuthResult)
			// #endif
		},
		methods: {
			// 兜底回调（来自 App.vue 的 Universal Link 转发）
			onTikTokAuthResult(url) {
				if (this._tiktokDone) return
				this._tiktokDone = true
				console.log('TikTok auth result from scheme:', url)
				// 也可调用 tiktokLogin 的内部兜底解析，这里简单提示
				this.statusText = '授权已完成（回跳）'
			},

			// 点击 TikTok 登录
			onTikTokLogin() {
				// 重置幂等标记：原生钩子与 App.vue Universal Link 兜底两条路径都可能触发，
				// 防止重复调登录接口
				this._tiktokDone = false
				this.result = ''
				uni.showLoading({
					title: '授权中...',
					icon: 'none'
				})
				// 重要：tiktokLogin 是【回调式】API（返回 void，非 Promise），
				// 结果通过 options.success / options.fail 返回，不要用 .then。
				// clientKey/redirectUri/scope 以后端 tiktokConfig 下发为准；
				// Android 端 redirectUri 必须与插件 AndroidManifest.xml 的
				// CallbackActivity intent-filter（https://code.hk.lingchuang.co）一致，
				// 否则网页授权降级时收不到回调；iOS 端 clientKey 以插件 Info.plist 的 TikTokClientKey 为准
				let data = {
					clientKey: 'sbawwsw3d3nxrgc4b3',
					redirectUri: 'https://code.hk.lingchuang.co',
					scope: 'user.info.basic,user.info.profile',
					language: 'en',
					success: (res) => {
						this._tiktokDone = true
						uni.hideLoading()
						console.log('tiktokLogin success:', res)
						this.tiktokToServer({
							authCode: res.authCode,
							codeVerifier: res.codeVerifier
						})
					},
					fail: (err) => {
						this._tiktokDone = true
						uni.hideLoading()
						console.error('tiktokLogin error:', err)
						this.statusText = '授权失败：' + (err.errMsg || '未知错误')
						uni.showToast({
							title: err.errMsg || '授权失败',
							icon: 'none'
						})
					}
				}
				try {
					// tiktokLogin 返回 void（回调式），成功/失败在上面的 success/fail 中处理
					tiktokLogin(data)
				} catch (e) {
					console.error('tiktokLogin invoke crash:', e)
					uni.hideLoading()
					uni.showToast({
						title: 'TikTok 授权模块不可用，请重新制作自定义基座',
						icon: 'none'
					})
				}
			},

			// 将 authCode + codeVerifier 发送到你的后端，由后端用 client_secret 换取 access_token
			tiktokToServer(params) {
				console.log('tiktokToServer:', params)
				this.statusText = '授权成功，正在换取用户信息...'
				this.result = JSON.stringify(params)
				// 示例：替换为你的真实后端地址
				// uni.request({
				// 	url: 'https://your-server.com/api/tiktok/exchange',
				// 	method: 'POST',
				// 	data: {
				// 		authCode: params.authCode,
				// 		codeVerifier: params.codeVerifier
				// 	},
				// 	success: (r) => {
				// 		console.log('exchange success:', r.data)
				// 		this.statusText = '登录成功'
				// 	},
				// 	fail: (e) => {
				// 		console.error('exchange fail:', e)
				// 		this.statusText = '换取 token 失败'
				// 	}
				// })
			}
		}
	}
</script>

<style>
	.content {
		display: flex;
		flex-direction: column;
		align-items: center;
		justify-content: center;
		padding-top: 200rpx;
	}

	.logo {
		height: 200rpx;
		width: 200rpx;
		margin-bottom: 50rpx;
	}

	.title {
		font-size: 36rpx;
		color: #8f8f94;
		margin-bottom: 60rpx;
		padding: 0 40rpx;
		text-align: center;
	}

	.login-btn {
		width: 80%;
		margin-bottom: 40rpx;
	}

	.result {
		width: 90%;
		background: #f5f5f5;
		border-radius: 12rpx;
		padding: 24rpx;
		word-break: break-all;
	}

	.result-label {
		font-size: 28rpx;
		color: #333;
		font-weight: bold;
	}

	.result-value {
		font-size: 24rpx;
		color: #666;
	}
</style>
