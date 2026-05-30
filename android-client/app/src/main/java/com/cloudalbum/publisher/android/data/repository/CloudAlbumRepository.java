package com.cloudalbum.publisher.android.data.repository;

import android.content.Context;

import com.cloudalbum.publisher.android.BuildConfig;
import com.cloudalbum.publisher.android.data.api.CloudAlbumApi;
import com.cloudalbum.publisher.android.data.model.ApiResult;
import com.cloudalbum.publisher.android.data.model.AppUpdateResponse;
import com.cloudalbum.publisher.android.data.model.DevicePullChunkResponse;
import com.cloudalbum.publisher.android.data.model.DevicePullResponse;
import com.cloudalbum.publisher.android.data.model.DeviceResponse;
import com.cloudalbum.publisher.android.data.model.DeviceSelfRegisterRequest;
import com.cloudalbum.publisher.android.data.model.DeviceTokenRequest;
import com.cloudalbum.publisher.android.data.model.DeviceTokenResponse;

import java.io.IOException;
import java.net.SocketTimeoutException;
import java.util.concurrent.TimeUnit;

import okhttp3.OkHttpClient;
import retrofit2.Call;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

public class CloudAlbumRepository {
    private static final int CODE_UNAUTHORIZED = 401;
    private static final int CODE_DEVICE_NOT_FOUND = 404;
    private static final int CODE_DEVICE_NOT_BOUND = 409;

    public static final OkHttpClient SHARED_CLIENT = new OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(15, TimeUnit.SECONDS)
            .build();

    private final DeviceSessionRepository sessionRepository;
    private final Object apiLock = new Object();
    private volatile CloudAlbumApi cachedApi;
    private volatile String cachedBaseUrl;

    public CloudAlbumRepository(Context context) {
        Context appContext = context.getApplicationContext();
        this.sessionRepository = new DeviceSessionRepository(appContext);
    }

    public DeviceSessionRepository getSessionRepository() {
        return sessionRepository;
    }

    public DeviceResponse selfRegisterCurrentDevice() throws IOException {
        CloudAlbumApi api = resolveApi(sessionRepository.getServerBaseUrl());
        DeviceSelfRegisterRequest request = new DeviceSelfRegisterRequest(
                sessionRepository.getDeviceUid(),
                sessionRepository.getDeviceType(),
                sessionRepository.getDeviceName()
        );
        ApiResult<DeviceResponse> result = execute(api.selfRegister(request));
        if (result.getData() != null) {
            sessionRepository.saveDeviceId(result.getData().getId());
        }
        return result.getData();
    }

    public DeviceTokenResponse issueDeviceToken() throws IOException {
        CloudAlbumApi api = resolveApi(sessionRepository.getServerBaseUrl());
        ApiResult<DeviceTokenResponse> result = execute(api.createDeviceToken(
                new DeviceTokenRequest(sessionRepository.getDeviceUid()), null));
        if (result.getData() == null || result.getData().getAccessToken() == null || result.getData().getAccessToken().trim().isEmpty()) {
            throw new IOException(result.getMessage() == null ? "设备令牌为空，请先在后台完成绑定" : result.getMessage());
        }
        sessionRepository.saveDeviceAccessToken(result.getData().getAccessToken());
        sessionRepository.saveDeviceId(result.getData().getDeviceId());
        return result.getData();
    }

    public AppUpdateResponse checkAndroidUpdate() throws IOException {
        CloudAlbumApi api = resolveApi(sessionRepository.getServerBaseUrl());
        ApiResult<AppUpdateResponse> result = execute(api.checkUpdate(
                "android",
                BuildConfig.VERSION_NAME,
                BuildConfig.VERSION_CODE,
                "stable",
                null));
        AppUpdateResponse data = result.getData();
        if (data != null && data.getDownloadUrl() != null && !data.getDownloadUrl().isEmpty()) {
            String url = data.getDownloadUrl().trim();
            if (!url.startsWith("http://") && !url.startsWith("https://")) {
                String base = sessionRepository.getServerBaseUrl();
                if (base != null && !base.isEmpty()) {
                    String normalizedBase = base.endsWith("/") ? base.substring(0, base.length() - 1) : base;
                    String normalizedPath = url.startsWith("/") ? url : "/" + url;
                    data.setDownloadUrl(normalizedBase + normalizedPath);
                }
            }
        }
        return data;
    }

    public DevicePullResponse pullCurrent() throws IOException {
        String token = bearerToken();
        CloudAlbumApi api = resolveApi(sessionRepository.getServerBaseUrl());
        ApiResult<DevicePullResponse> result = execute(api.pullCurrent(token));
        return result.getData();
    }

    public DevicePullChunkResponse pullCurrentChunk(String cursor) throws IOException {
        String token = bearerToken();
        CloudAlbumApi api = resolveApi(sessionRepository.getServerBaseUrl());
        ApiResult<DevicePullChunkResponse> result = execute(api.pullCurrentChunk(token, cursor));
        return result.getData();
    }

    private String bearerToken() {
        String token = sessionRepository.getDeviceAccessToken();
        return (token != null && !token.trim().isEmpty()) ? "Bearer " + token : null;
    }

    private CloudAlbumApi resolveApi(String baseUrl) {
        CloudAlbumApi existing = cachedApi;
        if (existing != null && baseUrl.equals(cachedBaseUrl)) {
            return existing;
        }
        synchronized (apiLock) {
            if (cachedApi != null && baseUrl.equals(cachedBaseUrl)) {
                return cachedApi;
            }
            Retrofit retrofit = new Retrofit.Builder()
                    .baseUrl(baseUrl + "/")
                    .client(SHARED_CLIENT)
                    .addConverterFactory(GsonConverterFactory.create())
                    .build();
            CloudAlbumApi api = retrofit.create(CloudAlbumApi.class);
            cachedApi = api;
            cachedBaseUrl = baseUrl;
            return api;
        }
    }

    private <T> ApiResult<T> execute(Call<ApiResult<T>> call) throws IOException {
        try {
            retrofit2.Response<ApiResult<T>> response = call.execute();
            if (!response.isSuccessful()) {
                throw httpError(response.code());
            }
            ApiResult<T> body = response.body();
            if (body == null) {
                throw new IOException("响应为空");
            }
            if (body.getCode() != 200) {
                throw new ApiException(body.getCode(), body.getMessage() == null ? "请求失败" : body.getMessage(), true);
            }
            return body;
        } catch (SocketTimeoutException error) {
            throw new IOException("连接超时，请检查服务器地址和网络");
        }
    }

    private IOException httpError(int code) {
        if (code == 401) {
            return new ApiException(code, "鉴权失败，请重新绑定设备", false);
        }
        if (code == 403) {
            return new IOException("无权限访问该设备接口");
        }
        if (code == 404) {
            return new IOException("服务器地址无效或接口不存在");
        }
        if (code == 409) {
            return new ApiException(code, "设备尚未绑定，请先在后台设备管理中绑定", false);
        }
        return new IOException("HTTP " + code);
    }

    public static boolean isDeviceSessionInvalid(Throwable error) {
        if (!(error instanceof ApiException)) {
            return false;
        }
        ApiException apiException = (ApiException) error;
        int code = apiException.getCode();
        if (apiException.isApiResultCode() && code == CODE_DEVICE_NOT_FOUND) {
            return true;
        }
        return code == CODE_UNAUTHORIZED || code == CODE_DEVICE_NOT_BOUND;
    }

    public static class ApiException extends IOException {
        private final int code;
        private final boolean apiResultCode;

        public ApiException(int code, String message, boolean apiResultCode) {
            super(message);
            this.code = code;
            this.apiResultCode = apiResultCode;
        }

        public int getCode() {
            return code;
        }

        public boolean isApiResultCode() {
            return apiResultCode;
        }
    }
}
