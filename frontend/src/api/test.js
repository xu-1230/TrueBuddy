import service from './index'

// 登录已迁移到 auth.js；此处转出以兼容 TestView 的既有引用
export { login } from './auth'

// 访问需要登录才能调用的接口
export const ping = () => service({ url: '/test/ping', method: 'get' })
export const ping2 = () => service({ url: '/test2/ping', method: 'get' })
