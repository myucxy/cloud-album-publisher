package com.cloudalbum.publisher.android.data.repository;

import android.content.Context;

import com.cloudalbum.publisher.android.data.api.CloudAlbumApi;
import com.cloudalbum.publisher.android.data.model.ApiResult;
import com.cloudalbum.publisher.android.data.model.DevicePullResponse;
import com.cloudalbum.publisher.android.data.model.DeviceResponse;
import com.cloudalbum.publisher.android.data.model.DeviceSelfRegisterRequest;
import com.cloudalbum.publisher.android.data.model.DeviceTokenRequest;
import com.cloudalbum.publisher.android.data.model.DeviceTokenResponse;

import java.io.IOException;
import java.net.SocketTimeoutException;
import java.util.concurrent.TimeUnit;

import okhttp3.Interceptor;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import retrofit2.Call;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

public class CloudAlbumRepository {
    private final DeviceSessionRepository sessionRepository;

    public CloudAlbumRepository(Context context) {
        Context appContext = context.getApplicationContext();
        this.sessionRepository = new DeviceSessionRepository(appContext);
    }

    public DeviceSessionRepository getSessionRepository() {
        return sessionRepository;
    }

    public DeviceResponse selfRegisterCurrentDevice() throws IOException {
        CloudAlbumApi api = createApi(sessionRepository.getServerBaseUrl(), null);
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
        CloudAlbumApi api = createApi(sessionRepository.getServerBaseUrl(), null);
        ApiResult<DeviceTokenResponse> result = execute(api.createDeviceToken(new DeviceTokenRequest(sessionRepository.getDeviceUid())));
        if (result.getData() == null || result.getData().getAccessToken() == null || result.getData().getAccessToken().trim().isEmpty()) {
            throw new IOException(result.getMessage() == null ? "设备令牌为空，请先在后台完成绑定" : result.getMessage());
        }
        sessionRepository.saveDeviceAccessToken(result.getData().getAccessToken());
        sessionRepository.saveDeviceId(result.getData().getDeviceId());
        return result.getData();
    }

    public DevicePullResponse pullCurrent() throws IOException {
        CloudAlbumApi api = createApi(sessionRepository.getServerBaseUrl(), sessionRepository.getDeviceAccessToken());
        ApiResult<DevicePullResponse> result = execute(api.pullCurrent());
        return result.getData();
    }

    private CloudAlbumApi createApi(String baseUrl, final String bearerToken) {
        OkHttpClient.Builder clientBuilder = new OkHttpClient.Builder()
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .writeTimeout(15, TimeUnit.SECONDS)
                .addInterceptor(new Interceptor() {
                    @Override
                    public Response intercept(Chain chain) throws IOException {
                        Request request = chain.request();
                        if (bearerToken != null && !bearerToken.trim().isEmpty()) {
                            request = request.newBuilder()
                                    .header("Authorization", "Bearer " + bearerToken)
                                    .build();
                        }
                        return chain.proceed(request);
                    }
                });

        Retrofit retrofit = new Retrofit.Builder()
                .baseUrl(baseUrl + "/")
                .client(clientBuilder.build())
                .addConverterFactory(GsonConverterFactory.create())
                .build();

        return retrofit.create(CloudAlbumApi.class);
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
                throw new IOException(body.getMessage() == null ? "请求失败" : body.getMessage());
            }
            return body;
        } catch (SocketTimeoutException error) {
            throw new IOException("连接超时，请检查服务器地址和网络");
        }
    }

    private IOException httpError(int code) {
        if (code == 401) {
            return new IOException("鉴权失败，请重新绑定设备");
        }
        if (code == 403) {
            return new IOException("无权限访问该设备接口");
        }
        if (code == 404) {
            return new IOException("服务器地址无效或接口不存在");
        }
        if (code == 409) {
            return new IOException("设备尚未绑定，请先在后台设备管理中绑定");
        }
        return new IOException("HTTP " + code);
    }
}
