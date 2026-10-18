import Cookies from "js-cookie";

const TokenKey = "token";
const RefreshTokenKey = "refreshToken";

export function getToken() {
  return Cookies.get(TokenKey);
}

export function setToken(token, expiresDays) {
  // 传天数（勾选「记住我」）才写持久 Cookie，否则维持会话级，关掉浏览器即失效
  const options = typeof expiresDays === "number" ? { expires: expiresDays } : undefined;
  return Cookies.set(TokenKey, token, options);
}

export function getRefreshToken() {
  return Cookies.get(RefreshTokenKey);
}

export function setRefreshToken(token, expiresDays) {
  const options = typeof expiresDays === "number" ? { expires: expiresDays } : undefined;
  return Cookies.set(RefreshTokenKey, token, options);
}

/** 退出登录 / 刷新失败：访问令牌与刷新令牌必须一起清 */
export function removeToken() {
  Cookies.remove(RefreshTokenKey);
  return Cookies.remove(TokenKey);
}
