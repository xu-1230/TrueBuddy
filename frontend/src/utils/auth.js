import Cookies from "js-cookie";

const TokenKey = "token";
const UserTypeKey = "userType";

export function getToken() {
  return Cookies.get(TokenKey);
}

export function setToken(token, expiresDays) {
  // 传天数（勾选「记住我」）才写持久 Cookie，否则维持会话级，关掉浏览器即失效
  const options = typeof expiresDays === "number" ? { expires: expiresDays } : undefined;
  return Cookies.set(TokenKey, token, options);
}

/** 登录响应携带的身份（0 普通用户 / 1 会员 / 2 管理员），供管理端判断入口权限 */
export function getUserType() {
  const raw = localStorage.getItem(UserTypeKey);
  if (raw === null || raw === "") return null;
  const value = Number(raw);
  return Number.isNaN(value) ? null : value;
}

export function setUserType(userType) {
  if (userType === null || userType === undefined) return;
  localStorage.setItem(UserTypeKey, String(userType));
}

export function removeToken() {
  localStorage.removeItem(UserTypeKey);
  return Cookies.remove(TokenKey);
}
