package com.cloudalbum.publisher.android.data.api;

import com.cloudalbum.publisher.android.data.model.ApiResult;
import com.cloudalbum.publisher.android.data.model.AppUpdateResponse;
import com.cloudalbum.publisher.android.data.model.DevicePullChunkResponse;
import com.cloudalbum.publisher.android.data.model.DevicePullResponse;
import com.cloudalbum.publisher.android.data.model.DeviceResponse;
import com.cloudalbum.publisher.android.data.model.DeviceSelfRegisterRequest;
import com.cloudalbum.publisher.android.data.model.DeviceTokenRequest;
import com.cloudalbum.publisher.android.data.model.DeviceTokenResponse;

import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.GET;
import retrofit2.http.Header;
import retrofit2.http.POST;
import retrofit2.http.Query;

public interface CloudAlbumApi {
    @POST("api/v1/devices/self/register")
    Call<ApiResult<DeviceResponse>> selfRegister(@Body DeviceSelfRegisterRequest request);

    @POST("api/v1/devices/self/token")
    Call<ApiResult<DeviceTokenResponse>> createDeviceToken(@Body DeviceTokenRequest request,
                                                          @Header("Authorization") String auth);

    @GET("api/v1/client-updates/check")
    Call<ApiResult<AppUpdateResponse>> checkUpdate(@Query("platform") String platform,
                                                   @Query("currentVersion") String currentVersion,
                                                   @Query("currentVersionCode") int currentVersionCode,
                                                   @Query("channel") String channel,
                                                   @Header("Authorization") String auth);

    @GET("api/v1/devices/pull/current")
    Call<ApiResult<DevicePullResponse>> pullCurrent(@Header("Authorization") String auth);

    @GET("api/v1/devices/pull/current/chunk")
    Call<ApiResult<DevicePullChunkResponse>> pullCurrentChunk(@Header("Authorization") String auth,
                                                              @Query("cursor") String cursor);
}
