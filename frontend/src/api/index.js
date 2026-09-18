import axios from "axios";
import { getToken } from "@/utils/auth";

const service = axios.create({
  // 走 Vite 代理，避免跨域；后端 context-path 为 /api
  baseURL: "/api",
  // 不设置默认 Content-Type：JSON 请求由 axios 自动设为 application/json，
  // FormData 请求交给浏览器自动生成 multipart/form-data（含 boundary）
});

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

// 响应拦截器：解包 CommonResult，code !== 0 视为失败
service.interceptors.response.use(
  (response) => {
    const res = response.data;
    if (res.code === 0) {
      return res.data;
    }
    const error = new Error(res.message || "请求失败");
    error.code = res.code;
    return Promise.reject(error);
  },
  (error) => {
    return Promise.reject(error);
  },
);

export default service;
