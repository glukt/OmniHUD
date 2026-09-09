package com.osrscopilot;

import java.io.IOException;
import okhttp3.OkHttpClient;

/**
 * Shared test helper: an {@link OkHttpClient} whose every call fails immediately with an
 * {@link IOException}, so UI tests that build monster/shop cards never touch the live OSRS Wiki
 * (portraits just fall back to the placeholder silhouette).
 */
final class OfflineHttp
{
    private OfflineHttp() {}

    static OkHttpClient client()
    {
        return new OkHttpClient.Builder()
            .addInterceptor(chain -> { throw new IOException("offline test stub - no real HTTP in unit tests"); })
            .build();
    }
}
