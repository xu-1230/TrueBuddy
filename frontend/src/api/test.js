import service from './index'

// 登录：POST /system/auth/login，携带账号密码
export const login = (username, password) => {
  return service({
    url: '/system/auth/login',
    method: 'post',
    data: { username, password },
  })
}

// 访问需要登录才能调用的接口
export const ping = () => service({ url: '/test/ping', method: 'get' })
export const ping2 = () => service({ url: '/test2/ping', method: 'get' })