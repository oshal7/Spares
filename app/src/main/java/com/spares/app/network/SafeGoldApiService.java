package com.spares.app.network;

import com.spares.app.network.model.SafeGoldBuyRequest;
import com.spares.app.network.model.SafeGoldBuyResponse;

import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.POST;

public interface SafeGoldApiService {

    @POST("v1/buy")
    Call<SafeGoldBuyResponse> buyGold(@Body SafeGoldBuyRequest request);
}
