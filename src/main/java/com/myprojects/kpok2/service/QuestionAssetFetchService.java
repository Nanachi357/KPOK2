package com.myprojects.kpok2.service;

import com.myprojects.kpok2.model.AssetFetchStatus;
import com.myprojects.kpok2.model.QuestionContentLocation;
import com.myprojects.kpok2.model.dto.ParsedQuestionAssetDto;
import com.myprojects.kpok2.model.dto.ParsedTestQuestionDto;
import com.myprojects.kpok2.service.mapper.QuestionContentFingerprintUtils;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.openqa.selenium.Cookie;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.WebDriver;
import org.springframework.stereotype.Service;

import java.net.InetAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/** Downloads authenticated question images, hashes them, and rewrites rich HTML. */
@Slf4j
@Service
public class QuestionAssetFetchService {

    private static final int MAX_ASSET_BYTES = 10 * 1024 * 1024;
    private static final int MAX_REDIRECTS = 3;
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(20);

    private final HttpClient httpClient;

    public QuestionAssetFetchService() {
        this(HttpClient.newBuilder()
                .connectTimeout(REQUEST_TIMEOUT)
                .followRedirects(HttpClient.Redirect.NEVER)
                .build());
    }

    QuestionAssetFetchService(HttpClient httpClient) {
        this.httpClient = httpClient;
    }

    public void prepareAssets(
            List<ParsedTestQuestionDto> questions, WebDriver driver) {
        if (questions == null || questions.isEmpty()) {
            return;
        }

        RequestContext requestContext = requestContext(driver);
        Map<String, FetchResult> fetchedByUrl = new HashMap<>();
        for (ParsedTestQuestionDto question : questions) {
            List<ParsedQuestionAssetDto> assets = Optional.ofNullable(question.getAssets())
                    .orElseGet(List::of);
            for (ParsedQuestionAssetDto asset : assets) {
                FetchResult result = fetchedByUrl.computeIfAbsent(
                        safe(asset.getSourceUrl()),
                        url -> fetch(url, requestContext));
                apply(asset, result);
            }
            rewriteHtml(question, assets);
            question.setCanonicalContentFingerprint(
                    QuestionContentFingerprintUtils.canonicalFingerprint(question));
            question.setRevisionContentFingerprint(
                    QuestionContentFingerprintUtils.revisionFingerprint(question));
            enrichCorrectAnswerIdentity(question);
        }
    }

    private FetchResult fetch(String sourceUrl, RequestContext requestContext) {
        if (sourceUrl.isBlank()) {
            return FetchResult.failure("Image URL is missing");
        }
        if (sourceUrl.startsWith("data:")) {
            return decodeDataUri(sourceUrl);
        }

        try {
            URI uri = URI.create(sourceUrl);
            for (int redirect = 0; redirect <= MAX_REDIRECTS; redirect++) {
                validateRemoteUri(uri);
                HttpRequest.Builder request = HttpRequest.newBuilder(uri)
                        .timeout(REQUEST_TIMEOUT)
                        .GET()
                        .header("Accept", "image/*")
                        .header("User-Agent", requestContext.userAgent());
                if (!requestContext.cookieHeaderFor(uri).isBlank()) {
                    request.header("Cookie", requestContext.cookieHeaderFor(uri));
                }
                if (!requestContext.referer().isBlank()) {
                    request.header("Referer", requestContext.referer());
                }

                HttpResponse<InputStream> response = httpClient.send(
                        request.build(), HttpResponse.BodyHandlers.ofInputStream());
                try (InputStream body = response.body()) {
                    if (response.statusCode() >= 300 && response.statusCode() < 400) {
                        String location = response.headers().firstValue("location")
                                .orElseThrow(() -> new IllegalStateException(
                                        "Image redirect has no destination"));
                        uri = uri.resolve(location);
                        continue;
                    }
                    if (response.statusCode() < 200 || response.statusCode() >= 300) {
                        throw new IllegalStateException(
                                "Image request returned HTTP " + response.statusCode());
                    }
                    var declaredLength = response.headers()
                            .firstValueAsLong("content-length");
                    if (declaredLength.isPresent()
                            && declaredLength.getAsLong() > MAX_ASSET_BYTES) {
                        throw new IllegalArgumentException(
                                "Image exceeds the 10 MB limit");
                    }

                    byte[] content = body.readNBytes(MAX_ASSET_BYTES + 1);
                    validateContentLength(content);
                    String mediaType = normalizedMediaType(response.headers()
                            .firstValue("content-type").orElse("application/octet-stream"));
                    validateImageMediaType(mediaType);
                    return FetchResult.stored(
                            sha256(content),
                            mediaType,
                            fileName(uri, mediaType),
                            content,
                            false);
                }
            }
            return FetchResult.failure("Too many image redirects");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return FetchResult.failure("Image download was interrupted");
        } catch (Exception e) {
            log.warn("Could not store question image from {}: {}",
                    safeLogUrl(sourceUrl), e.getMessage());
            return FetchResult.failure(shortMessage(e));
        }
    }

    private FetchResult decodeDataUri(String sourceUrl) {
        try {
            int comma = sourceUrl.indexOf(',');
            if (comma < 0) {
                throw new IllegalArgumentException("Malformed data image");
            }
            String metadata = sourceUrl.substring(5, comma);
            String mediaType = normalizedMediaType(metadata.split(";", 2)[0]);
            validateImageMediaType(mediaType);
            boolean base64 = metadata.toLowerCase(Locale.ROOT).contains(";base64");
            byte[] content = base64
                    ? Base64.getDecoder().decode(sourceUrl.substring(comma + 1))
                    : java.net.URLDecoder.decode(
                            sourceUrl.substring(comma + 1), StandardCharsets.UTF_8)
                            .getBytes(StandardCharsets.UTF_8);
            validateContentLength(content);
            return FetchResult.stored(
                    sha256(content),
                    mediaType,
                    "embedded." + extension(mediaType),
                    content,
                    true);
        } catch (Exception e) {
            return FetchResult.failure(shortMessage(e));
        }
    }

    private void apply(ParsedQuestionAssetDto asset, FetchResult result) {
        boolean embeddedSource = safe(asset.getSourceUrl()).startsWith("data:");
        asset.setFetchStatus(result.status());
        asset.setFailureReason(result.failureReason());
        asset.setContentHash(result.contentHash());
        asset.setMediaType(result.mediaType());
        asset.setOriginalFileName(result.fileName());
        asset.setContent(result.content());
        if (result.embedded() && result.contentHash() != null) {
            asset.setSourceUrl("embedded:" + result.contentHash());
        } else if (embeddedSource && result.status() == AssetFetchStatus.FAILED) {
            asset.setSourceUrl("embedded:unavailable");
        }
    }

    private void rewriteHtml(
            ParsedTestQuestionDto question, List<ParsedQuestionAssetDto> assets) {
        question.setContextHtml(rewriteFragment(question.getContextHtml(), assets));
        question.setQuestionHtml(rewriteFragment(question.getQuestionHtml(), assets));
        question.setAnswerHtml(Optional.ofNullable(question.getAnswerHtml())
                .orElseGet(List::of)
                .stream()
                .map(html -> rewriteFragment(html, assets))
                .toList());
    }

    private String rewriteFragment(
            String html, List<ParsedQuestionAssetDto> assets) {
        if (html == null || html.isBlank()) {
            return html;
        }
        Document document = Jsoup.parseBodyFragment(html);
        document.outputSettings().prettyPrint(false);
        for (ParsedQuestionAssetDto asset : assets) {
            Element image = document.selectFirst(
                    "img[data-kpok2-ref='" + asset.getReferenceId() + "']");
            if (image == null) {
                continue;
            }
            if (asset.getFetchStatus() == AssetFetchStatus.STORED
                    && asset.getContentHash() != null) {
                image.attr("src", "kpok2-asset:" + asset.getContentHash());
            } else if (safe(asset.getSourceUrl()).startsWith("embedded:")) {
                image.removeAttr("src");
            }
            image.removeAttr("data-kpok2-ref");
        }
        return document.body().html().trim();
    }

    private void enrichCorrectAnswerIdentity(ParsedTestQuestionDto question) {
        List<String> normalizedCorrectAnswers = Optional.ofNullable(
                question.getNormalizedCorrectAnswers()).orElseGet(List::of);
        List<Integer> correctIndices = Optional.ofNullable(
                question.getCorrectAnswerIndices()).orElseGet(List::of);
        if (normalizedCorrectAnswers.isEmpty() || correctIndices.isEmpty()) {
            return;
        }

        List<String> enriched = new ArrayList<>(normalizedCorrectAnswers.size());
        for (int index = 0; index < normalizedCorrectAnswers.size(); index++) {
            String normalizedAnswer = normalizedCorrectAnswers.get(index);
            String contentFingerprint = index < correctIndices.size()
                    ? QuestionContentFingerprintUtils.answerContentFingerprint(
                            question, correctIndices.get(index))
                    : null;
            enriched.add(contentFingerprint == null
                    ? normalizedAnswer
                    : normalizedAnswer + "|content:" + contentFingerprint);
        }
        question.setNormalizedCorrectAnswers(List.copyOf(enriched));
        question.setNormalizedCorrectAnswer(String.join(" | ", enriched));
    }

    private RequestContext requestContext(WebDriver driver) {
        if (driver == null) {
            return new RequestContext("Mozilla/5.0", "", "", "");
        }
        try {
            String cookies = driver.manage().getCookies().stream()
                    .map(cookie -> cookie.getName() + "=" + cookie.getValue())
                    .sorted()
                    .reduce((first, second) -> first + "; " + second)
                    .orElse("");
            String userAgent = driver instanceof JavascriptExecutor javascriptExecutor
                    ? String.valueOf(javascriptExecutor.executeScript("return navigator.userAgent"))
                    : "Mozilla/5.0";
            URI currentUri = URI.create(safe(driver.getCurrentUrl()));
            String browserHost = safe(currentUri.getHost()).toLowerCase(Locale.ROOT);
            return new RequestContext(
                    userAgent == null || userAgent.isBlank() || "null".equals(userAgent)
                            ? "Mozilla/5.0"
                            : userAgent,
                    cookies,
                    safeReferer(currentUri),
                    browserHost);
        } catch (RuntimeException e) {
            log.debug("Could not copy browser request context: {}", e.getMessage());
            return new RequestContext("Mozilla/5.0", "", "", "");
        }
    }

    private String safeReferer(URI uri) {
        try {
            return new URI(
                    uri.getScheme(),
                    null,
                    uri.getHost(),
                    uri.getPort(),
                    uri.getPath(),
                    null,
                    null).toString();
        } catch (Exception e) {
            return "";
        }
    }

    private void validateRemoteUri(URI uri) throws Exception {
        String scheme = uri.getScheme();
        if (!("https".equalsIgnoreCase(scheme) || "http".equalsIgnoreCase(scheme))) {
            throw new IllegalArgumentException("Unsupported image URL scheme");
        }
        if (uri.getHost() == null || uri.getHost().isBlank()) {
            throw new IllegalArgumentException("Image URL has no host");
        }
        for (InetAddress address : InetAddress.getAllByName(uri.getHost())) {
            if (address.isAnyLocalAddress()
                    || address.isLoopbackAddress()
                    || address.isLinkLocalAddress()
                    || address.isSiteLocalAddress()) {
                throw new IllegalArgumentException("Private image hosts are not allowed");
            }
        }
    }

    private void validateContentLength(byte[] content) {
        if (content == null || content.length == 0) {
            throw new IllegalArgumentException("Image is empty");
        }
        if (content.length > MAX_ASSET_BYTES) {
            throw new IllegalArgumentException("Image exceeds the 10 MB limit");
        }
    }

    private void validateImageMediaType(String mediaType) {
        if (mediaType == null || !mediaType.toLowerCase(Locale.ROOT).startsWith("image/")) {
            throw new IllegalArgumentException("Downloaded content is not an image");
        }
    }

    private String normalizedMediaType(String value) {
        if (value == null || value.isBlank()) {
            return "application/octet-stream";
        }
        return value.split(";", 2)[0].trim().toLowerCase(Locale.ROOT);
    }

    private String fileName(URI uri, String mediaType) {
        String path = uri.getPath();
        String candidate = path == null ? "" : path.substring(path.lastIndexOf('/') + 1);
        candidate = candidate.replaceAll("[^\\p{L}\\p{N}._-]+", "_");
        if (candidate.isBlank()) {
            return "image." + extension(mediaType);
        }
        return candidate.length() > 500 ? candidate.substring(candidate.length() - 500) : candidate;
    }

    private String extension(String mediaType) {
        return switch (mediaType) {
            case "image/jpeg" -> "jpg";
            case "image/svg+xml" -> "svg";
            case "image/webp" -> "webp";
            case "image/gif" -> "gif";
            default -> "png";
        };
    }

    private static String sha256(byte[] content) {
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(content));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is unavailable", e);
        }
    }

    private String shortMessage(Exception exception) {
        String message = exception.getMessage();
        if (message == null || message.isBlank()) {
            message = exception.getClass().getSimpleName();
        }
        return message.length() > 500 ? message.substring(0, 500) : message;
    }

    private String safeLogUrl(String sourceUrl) {
        if (sourceUrl.startsWith("data:")) {
            return "embedded image";
        }
        try {
            URI uri = URI.create(sourceUrl);
            return new URI(uri.getScheme(), null, uri.getHost(), uri.getPort(),
                    uri.getPath(), null, null).toString();
        } catch (Exception e) {
            return "invalid image URL";
        }
    }

    private String safe(String value) {
        return value == null ? "" : value.trim();
    }

    private record RequestContext(
            String userAgent,
            String cookieHeader,
            String referer,
            String browserHost) {

        private String cookieHeaderFor(URI uri) {
            return browserHost.equalsIgnoreCase(safeHost(uri)) ? cookieHeader : "";
        }

        private static String safeHost(URI uri) {
            return uri.getHost() == null ? "" : uri.getHost();
        }
    }

    private record FetchResult(
            AssetFetchStatus status,
            String contentHash,
            String mediaType,
            String fileName,
            byte[] content,
            String failureReason,
            boolean embedded) {

        private static FetchResult stored(
                String contentHash,
                String mediaType,
                String fileName,
                byte[] content,
                boolean embedded) {
            return new FetchResult(
                    AssetFetchStatus.STORED,
                    contentHash,
                    mediaType,
                    fileName,
                    content,
                    null,
                    embedded);
        }

        private static FetchResult failure(String reason) {
            return new FetchResult(
                    AssetFetchStatus.FAILED,
                    null,
                    null,
                    null,
                    null,
                    reason,
                    false);
        }
    }
}
