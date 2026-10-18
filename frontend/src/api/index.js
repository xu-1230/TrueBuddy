import axios from "axios";
import {
  getRefreshToken,
  getToken,
  removeToken,
  setRefreshToken,
  setToken,
} from "@/utils/auth";

const baseURL = "/api";

const service = axios.create({
  // 走 Vite 代理，避免跨域；后端 context-path 为 /api
  baseURL,
  // 不设置默认 Content-Type：JSON 请求由 axios 自动设为 application/json，
  // FormData 请求交给浏览器自动生成 multipart/form-data（含 boundary）
});

// 刷新令牌专用实例：不挂拦截器，避免刷新请求自身 401 时递归刷新
const refreshClient = axios.create({ baseURL });

/** 并发 401 只发一次刷新请求，其余请求等待同一个 Promise */
let refreshPromise = null;

function clearAuth() {
  removeToken();
}

function isUnauthorized(error) {
  // 安全框架返回 HTTP 401；业务异常则是 HTTP 200 + code=401
  return error?.response?.status === 401 || error?.code === 401;
}

function businessError(res) {
  const error = new Error(res.message || "请求失败");
  error.code = res.code;
  return error;
}

/**
 * 用 refreshToken 换新令牌；成功后写回新的 accessToken / refreshToken。
 * 并发调用共享同一个 Promise。
 */
function refreshAccessToken() {
  if (refreshPromise) return refreshPromise;

  const refreshToken = getRefreshToken();
  if (!refreshToken) {
    return Promise.reject(Object.assign(new Error("登录已过期，请重新登录"), { code: 401 }));
  }

  refreshPromise = refreshClient
    .post("/system/auth/refresh", { refreshToken })
    .then((response) => {
      const res = response.data;
      if (res.code !== 0) throw businessError(res);
      setToken(res.data.accessToken);
      setRefreshToken(res.data.refreshToken);
      return res.data.accessToken;
    })
    .catch((error) => {
      // 只有明确失效才清空；网络抖动保留令牌，稍后仍可重试
      if (isUnauthorized(error)) clearAuth();
      throw error;
    })
    .finally(() => {
      refreshPromise = null;
    });

  return refreshPromise;
}

/**
 * 401 后尝试刷新并重放原请求。
 * 登录 / 刷新接口自身不重试；每个请求只重放一次。
 */
async function retryAfterRefresh(error, config) {
  const url = typeof config?.url === "string" ? config.url : "";
  const isAuthRequest = url.includes("/system/auth/login") || url.includes("/system/auth/refresh");

  if (!config || isAuthRequest || config._retry || !getRefreshToken()) {
    if (!isAuthRequest) clearAuth();
    throw error;
  }

  config._retry = true;
  await refreshAccessToken();
  return service(config);
}

// 请求拦截器：自动携带 Token
service.interceptors.request.use(
  (config) => {
    const token = getToken();
    if (token) {
      config.headers.Authorization = `Bearer ${token}`;
    }
    return config;
  },
  (error) => {
    return Promise.reject(error);
  },
);

// 响应拦截器：解包 CommonResult；401 先刷新再重放
service.interceptors.response.use(
  (response) => {
    const res = response.data;
    if (res.code === 0) {
      return res.data;
    }
    const error = businessError(res);
    if (res.code === 401) {
      return retryAfterRefresh(error, response.config);
    }
    return Promise.reject(error);
  },
  (error) => {
    if (error?.response?.status === 401) {
      return retryAfterRefresh(error, error.config);
    }
    return Promise.reject(error);
  },
);

export default service;
