import axios, { AxiosError, InternalAxiosRequestConfig } from 'axios';

const BASE_URL = process.env.REACT_APP_API_URL || 'http://localhost:8080';

const httpService = axios.create({
  baseURL: BASE_URL,
});

let isRefreshing = false;
let failedQueue: Array<{
  resolve: (token: string) => void;
  reject: (error: any) => void;
}> = [];

const processQueue = (error: any, token: string | null = null) => {
  failedQueue.forEach((promise) => {
    if (token) {
      promise.resolve(token);
    } else {
      promise.reject(error);
    }
  });
  failedQueue = [];
};

httpService.interceptors.request.use(
  (config: InternalAxiosRequestConfig) => {
    const storage = localStorage.getItem('rememberMe') === 'true' ? localStorage : sessionStorage;
    const token = storage.getItem('accessToken') || localStorage.getItem('accessToken');
    if (token && config.headers) {
      config.headers.Authorization = 'Bearer ' + token;
    }
    return config;
  },
  (error: AxiosError) => Promise.reject(error)
);

httpService.interceptors.response.use(
  (response) => response,
  async (error: AxiosError) => {
    const originalRequest = error.config as InternalAxiosRequestConfig & { _retry?: boolean };

    if (error.response?.status === 401 && !originalRequest._retry) {
      if (isRefreshing) {
        return new Promise((resolve, reject) => {
          failedQueue.push({ resolve, reject });
        }).then((token) => {
          originalRequest.headers.Authorization = 'Bearer ' + token;
          return httpService(originalRequest);
        });
      }

      originalRequest._retry = true;
      isRefreshing = true;

      const storage = localStorage.getItem('rememberMe') === 'true' ? localStorage : sessionStorage;
      const refreshToken = storage.getItem('refreshToken') || localStorage.getItem('refreshToken');

      if (!refreshToken) {
        localStorage.clear();
        sessionStorage.clear();
        window.location.href = '/login';
        return Promise.reject(error);
      }

      try {
        const res = await axios.post(BASE_URL + '/api/v1/auth/refresh', { refreshToken });
        const newAccessToken = res.data.data.accessToken;

        if (localStorage.getItem('rememberMe') === 'true') {
          localStorage.setItem('accessToken', newAccessToken);
        } else {
          sessionStorage.setItem('accessToken', newAccessToken);
        }

        processQueue(null, newAccessToken);
        originalRequest.headers.Authorization = 'Bearer ' + newAccessToken;
        return httpService(originalRequest);
      } catch (refreshError) {
        processQueue(refreshError, null);
        localStorage.clear();
        sessionStorage.clear();
        window.location.href = '/login';
        return Promise.reject(refreshError);
      } finally {
        isRefreshing = false;
      }
    }

    return Promise.reject(error);
  }
);

export default httpService;
