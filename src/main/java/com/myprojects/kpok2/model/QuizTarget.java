package com.myprojects.kpok2.model;

import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Identifies a Moodle quiz on the supported TestCenter site.
 *
 * @param siteRoot site scheme and host, without a trailing slash
 * @param courseModuleId Moodle course-module id (the {@code id} / {@code cmid} value)
 */
public record QuizTarget(URI siteRoot, long courseModuleId) {

    private static final String SUPPORTED_HOST = "test.testcentr.org.ua";
    private static final String QUIZ_VIEW_PATH = "/mod/quiz/view.php";

    public QuizTarget {
        if (siteRoot == null || siteRoot.getScheme() == null || siteRoot.getHost() == null) {
            throw new IllegalArgumentException("Quiz site URL is incomplete");
        }
        if (!("https".equalsIgnoreCase(siteRoot.getScheme())
                || "http".equalsIgnoreCase(siteRoot.getScheme()))) {
            throw new IllegalArgumentException("Quiz site must use HTTP or HTTPS");
        }
        if (!SUPPORTED_HOST.equalsIgnoreCase(siteRoot.getHost())) {
            throw new IllegalArgumentException("Unsupported quiz site");
        }
        if ((siteRoot.getPath() != null && !siteRoot.getPath().isEmpty())
                || siteRoot.getQuery() != null || siteRoot.getFragment() != null) {
            throw new IllegalArgumentException("Quiz site root cannot contain a path or query");
        }
        if (courseModuleId <= 0) {
            throw new IllegalArgumentException("Quiz id must be a positive number");
        }
    }

    /**
     * Parses the URL copied from a quiz page, for example
     * {@code https://test.testcentr.org.ua/mod/quiz/view.php?id=173}.
     */
    public static QuizTarget fromUrl(String rawUrl) {
        if (rawUrl == null || rawUrl.isBlank()) {
            throw new IllegalArgumentException("Enter the quiz page URL");
        }

        try {
            URI uri = URI.create(rawUrl.trim());
            String scheme = uri.getScheme();
            if (!"https".equalsIgnoreCase(scheme) && !"http".equalsIgnoreCase(scheme)) {
                throw new IllegalArgumentException("Quiz URL must start with http:// or https://");
            }
            if (!SUPPORTED_HOST.equalsIgnoreCase(uri.getHost())) {
                throw new IllegalArgumentException("Use a quiz URL from " + SUPPORTED_HOST);
            }
            if (!QUIZ_VIEW_PATH.equals(uri.getPath())) {
                throw new IllegalArgumentException("URL must point to a Moodle quiz view page");
            }

            long quizId = positiveLong(queryParameters(uri).get("id"), "Quiz URL must contain a numeric id");
            URI siteRoot = new URI(
                    uri.getScheme().toLowerCase(), null, uri.getHost(), uri.getPort(),
                    null, null, null);
            return new QuizTarget(siteRoot, quizId);
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid quiz URL", e);
        }
    }

    public String viewUrl() {
        return siteRoot + QUIZ_VIEW_PATH + "?id=" + courseModuleId;
    }

    public String summaryUrl(String attemptId) {
        return siteRoot + "/mod/quiz/summary.php?attempt=" + validatedAttemptId(attemptId)
                + "&cmid=" + courseModuleId;
    }

    public String reviewUrl(String attemptId) {
        return siteRoot + "/mod/quiz/review.php?attempt=" + validatedAttemptId(attemptId)
                + "&cmid=" + courseModuleId + "&showall=1";
    }

    public static Optional<String> extractAttemptId(String url) {
        if (url == null || url.isBlank()) {
            return Optional.empty();
        }

        try {
            String value = queryParameters(URI.create(url)).get("attempt");
            if (value == null || value.isBlank()) {
                return Optional.empty();
            }
            positiveLong(value, "Attempt id must be numeric");
            return Optional.of(value);
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    public boolean matchesReviewUrl(String url, String attemptId) {
        try {
            URI uri = URI.create(url);
            Map<String, String> query = queryParameters(uri);
            return siteRoot.getHost().equalsIgnoreCase(uri.getHost())
                    && "/mod/quiz/review.php".equals(uri.getPath())
                    && validatedAttemptId(attemptId).equals(query.get("attempt"))
                    && Long.toString(courseModuleId).equals(query.get("cmid"));
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    public static Optional<String> queryParameter(String url, String name) {
        try {
            return Optional.ofNullable(queryParameters(URI.create(url)).get(name));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    private static String validatedAttemptId(String attemptId) {
        positiveLong(attemptId, "Attempt id must be a positive number");
        return attemptId;
    }

    private static long positiveLong(String value, String message) {
        try {
            long parsed = Long.parseLong(value);
            if (parsed <= 0) {
                throw new IllegalArgumentException(message);
            }
            return parsed;
        } catch (NullPointerException | NumberFormatException e) {
            throw new IllegalArgumentException(message, e);
        }
    }

    private static Map<String, String> queryParameters(URI uri) {
        String query = uri.getRawQuery();
        if (query == null || query.isBlank()) {
            return Map.of();
        }

        return Arrays.stream(query.split("&"))
                .map(part -> part.split("=", 2))
                .filter(parts -> parts.length == 2)
                .collect(Collectors.toMap(
                        parts -> decode(parts[0]),
                        parts -> decode(parts[1]),
                        (first, ignored) -> first));
    }

    private static String decode(String value) {
        return URLDecoder.decode(value, StandardCharsets.UTF_8);
    }
}
