const { defineConfig } = require('@vue/cli-service')

module.exports = defineConfig({
  transpileDependencies: true,
  devServer: {
    port: 5173,
    proxy: {
      '/api': {
        target: 'http://localhost:8088',
        changeOrigin: true
      }
    },
    client: {
      overlay: {
        runtimeErrors: (error) => {
          if (error.message && error.message.includes('ResizeObserver')) return false
          return true
        }
      }
    }
  }
})
