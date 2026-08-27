<script>
	export default {
		onLaunch: function() {
			console.log('App Launch')
		},
		onShow: function() {
			// #ifdef APP-PLUS
			// iOS 网页授权（Universal Link）回跳兜底：
			// 冷启动/从后台回来时，若授权通过 Universal Link 回跳，
			// 回调 URL 可在 plus.runtime.arguments 取到，转发给登录页消费。
			const args = plus.runtime.arguments
			if (args && typeof args === 'string' && args.indexOf('code=') > -1) {
				console.log('TikTok auth scheme from runtime.args:', args)
				uni.$emit('tiktok:auth:result', args)
				plus.runtime.arguments = ''
			}
			// #endif
			console.log('App Show')
		},
		onHide: function() {
			console.log('App Hide')
		}
	}
</script>

<style>
	/*每个页面公共css */
</style>
