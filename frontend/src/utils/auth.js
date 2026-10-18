import Cookies from "js-cookie";

const TokenKey = "token";

export function getToken() {
  return Cookies.get(TokenKey);
}

export function setToken(token, expiresDays) {
  // 传天数（勾选「记住我」）才写持久 Cookie，否则维持会话级，关掉浏览器即失效
  const options = typeof expiresDays === "number" ? { expires: expiresDays } : undefined;
  return Cookies.set(TokenKey, token, options);
}

export function removeToken() {
  return Cookies.remove(TokenKey);
}
