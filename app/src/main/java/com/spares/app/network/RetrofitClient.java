package com.spares.app.network;

import com.spares.app.BuildConfig;

import java.util.concurrent.TimeUnit;

import okhttp3.OkHttpClient;
import okhttp3.logging.HttpLoggingInterceptor;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

public final class RetrofitClient {

    private static volatile SafeGoldApiService safeGoldService;
    private static volatile PdnApiService pdnService;

    private RetrofitClient() {}

    public static SafeGoldApiService safeGold() {
        if (safeGoldService == null) {
            synchronized (RetrofitClient.class) {
                if (safeGoldService == null) {
                    OkHttpClient client = baseClientBuilder()
                        .addInterceptor(chain -> chain.proceed(chain.request().newBuilder()
                            .addHeader("Authorization", "Bearer " + BuildConfig.SAFEGOLD_API_KEY)
                            .build()))
                        .build();
                    safeGoldService = new Retrofit.Builder()
                        .baseUrl(BuildConfig.SAFEGOLD_BASE_URL)
                        .client(client)
                        .addConverterFactory(GsonConverterFactory.create())
                        .build()
                        .create(SafeGoldApiService.class);
                }
            }
        }
        return safeGoldService;
    }

    public static PdnApiService pdn() {
        if (pdnService == null) {
            synchronized (RetrofitClient.class) {
                if (pdnService == null) {
                    pdnService = new Retrofit.Builder()
                        .baseUrl(BuildConfig.PDN_BASE_URL)
                        .client(baseClientBuilder().build())
                        .addConverterFactory(GsonConverterFactory.create())
                        .build()
                        .create(PdnApiService.class);
                }
            }
        }
        return pdnService;
    }

    private static OkHttpClient.Builder baseClientBuilder() {
        OkHttpClient.Builder builder = new OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS);
        if (BuildConfig.DEBUG) {
            HttpLoggingInterceptor logging = new HttpLoggingInterceptor();
            logging.setLevel(HttpLoggingInterceptor.Level.BASIC);
            builder.addInterceptor(logging);
        }
        return builder;
    }
}
