package com.github.celkins.aws.lambda.proxy;

import com.amazonaws.serverless.proxy.internal.model.AwsProxyRequest;
import com.amazonaws.serverless.proxy.internal.model.AwsProxyResponse;
import com.amazonaws.services.lambda.runtime.Context;
import okhttp3.*;

import javax.annotation.Nullable;
import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

@SuppressWarnings("unused")
public final class LambdaProxyHandler {

    private static final Set<String> hopByHopHeaders = new HashSet<>(Arrays.asList(
            "connection",
            "keep-alive",
            "public",
            "proxy-authenticate",
            "transfer-encoding",
            "upgrade"
    ));

    private final OkHttpClient client = new OkHttpClient();

    public AwsProxyResponse handleRequest(AwsProxyRequest proxyRequest, Context context) throws Exception {
        return toAwsProxyResponse(this.client.newCall(toRequest(context, proxyRequest)).execute());
    }

    @SuppressWarnings("WeakerAccess") // Visible for testing
    static Request toRequest(Context context, AwsProxyRequest request) {
        Headers headers = Headers.of(forwardedHeaders(request.getHeaders()));

        return new Request.Builder()
                .url(url(context, request))
                .headers(headers)
                .method(request.getHttpMethod(), requestBody(headers, request.getBody()))
                .build();
    }

    @SuppressWarnings("WeakerAccess") // Visible for testing
    static HttpUrl url(Context context, AwsProxyRequest request) {
        //TODO Populate from a stage variable
        String baseUrl = "https://www.google.com/robots.txt";

        HttpUrl base = HttpUrl.parse(baseUrl);
        if (base != null) {
            HttpUrl.Builder url = base.newBuilder()
                    .query(request.getQueryString());

            String path = path(request.getRequestContext().getResourcePath(), request.getPathParameters());
            if (path != null) {
                // Trim leading slash, if any
                if (path.startsWith("/")) {
                    path = path.substring(1, path.length());
                }

                url.addEncodedPathSegments(path);
            }

            return url.build();
        } else {
            throw new RuntimeException("Error parsing base URL: " + baseUrl);
        }
    }

//    private static final Pattern catchAllPattern = Pattern.compile("(\\{(\\w+)\\+\\})");

    @SuppressWarnings("WeakerAccess") // Visible for testing
    static String path(String template, Map<String, String> params) {
        /*TODO
        if (template != null) {
            //TODO Replace catch-all path variable explicitly
            // e.g., /{proxy+} && proxy: foo => /foo

            // Replace remaining params
            return UriTemplate.fromTemplate(template)
                    .expand(upcast(params));
        }
        */
        return null;
    }

    @SuppressWarnings("WeakerAccess") // Visible for testing
    static <K, V> Map<K, Object> upcast(Map<K, V> map) {
        return map.entrySet().stream()
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    }

    @SuppressWarnings("WeakerAccess") // Visible for testing
    static <V> Map<String, V> forwardedHeaders(Map<String, V> headers) {
        return headers.entrySet().stream()
                .filter(e -> !hopByHopHeaders.contains(e.getKey().toLowerCase(Locale.US)))
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    }

    @SuppressWarnings("WeakerAccess") // Visible for testing
    static @Nullable RequestBody requestBody(Headers headers, @Nullable String body) {
        if (body != null) {
            //TODO Handle base64-encoded request body
            return RequestBody.create(contentType(headers), body);
        } else {
            return null;
        }
    }

    @SuppressWarnings("WeakerAccess") // Visible for testing
    static @Nullable MediaType contentType(Headers headers) {
        String contentType = headers.get("Content-Type");
        if (contentType != null) {
            return MediaType.parse(contentType);
        } else {
            return null;
        }
    }

    @SuppressWarnings("WeakerAccess") // Visible for testing
    static AwsProxyResponse toAwsProxyResponse(Response response) throws IOException {
        AwsProxyResponse proxyResponse = new AwsProxyResponse();

        proxyResponse.setHeaders(takeFirstValue(forwardedHeaders(response.headers().toMultimap())));

        ResponseBody body = response.body();
        if (body != null) {
            //TODO Handle binary response body
            proxyResponse.setBody(body.string());
        }

        return proxyResponse;
    }

    @SuppressWarnings("WeakerAccess") // Visible for testing
    static <K, V> Map<K, V> takeFirstValue(Map<K, List<V>> map) {
        return map.entrySet().stream()
                .filter(e -> !e.getValue().isEmpty())
                .collect(Collectors.toMap(Map.Entry::getKey, e -> e.getValue().get(0)));
    }
}
