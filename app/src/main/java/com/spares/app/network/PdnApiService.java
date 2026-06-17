package com.spares.app.network;

import com.spares.app.network.model.PdnRequest;

import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.POST;

public interface PdnApiService {

    @POST("v1/pdn")
    Call<Void> notifyDebit(@Body PdnRequest request);
}
