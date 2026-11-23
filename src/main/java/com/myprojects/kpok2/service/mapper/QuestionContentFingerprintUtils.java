package com.myprojects.kpok2.service.mapper;

import com.myprojects.kpok2.model.QuestionContentLocation;
import com.myprojects.kpok2.model.dto.ParsedQuestionAssetDto;
import com.myprojects.kpok2.model.dto.ParsedTestQuestionDto;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;

/** Deterministic non-text identity shared by collection and import validation. */
public final class QuestionContentFingerprintUtils {
    private QuestionContentFingerprintUtils() {
    }

    public static String canonicalFingerprint(ParsedTestQuestionDto question) {
        return fingerprint(
                question,
                List.of(QuestionContentLocation.CONTEXT, QuestionContentLocation.QUESTION));
    }

    public static String revisionFingerprint(ParsedTestQuestionDto question) {
        return answerSetFingerprint(question);
    }

    public static String answerContentFingerprint(
            ParsedTestQuestionDto question, int answerIndex) {
        List<String> tokens = new ArrayList<>();
        for (ParsedQuestionAssetDto asset : assets(question)) {
            if (asset.getContentLocation() != QuestionContentLocation.ANSWER
                    || asset.getAnswerIndex() != answerIndex) {
                continue;
            }
            String identity = asset.getContentHash() == null
                    ? safe(asset.getSourceUrl())
                    : asset.getContentHash();
            if (!identity.isBlank()) {
                tokens.add("image:" + identity);
            }
        }
        List<String> answerHtml = Optional.ofNullable(question.getAnswerHtml())
                .orElseGet(List::of);
        if (answerIndex >= 0 && answerIndex < answerHtml.size()) {
            collectStructuredTokens("answer", answerHtml.get(answerIndex), tokens);
        }
        return finish(tokens);
    }

    private static String fingerprint(
            ParsedTestQuestionDto question,
            List<QuestionContentLocation> includedLocations) {
        List<String> tokens = new ArrayList<>();
        for (ParsedQuestionAssetDto asset : assets(question)) {
            if (!includedLocations.contains(asset.getContentLocation())) {
                continue;
            }
            String identity = asset.getContentHash() == null
                    ? safe(asset.getSourceUrl())
                    : asset.getContentHash();
            if (!identity.isBlank()) {
                tokens.add(asset.getContentLocation() + ":image:" + identity);
            }
        }
        if (includedLocations.contains(QuestionContentLocation.CONTEXT)) {
            collectStructuredTokens("context", question.getContextHtml(), tokens);
        }
        if (includedLocations.contains(QuestionContentLocation.QUESTION)) {
            collectStructuredTokens("question", question.getQuestionHtml(), tokens);
        }
        return finish(tokens);
    }

    private static String answerSetFingerprint(ParsedTestQuestionDto question) {
        List<String> optionTokens = new ArrayList<>();
        List<String> normalizedAnswers = Optional.ofNullable(
                question.getNormalizedAnswers()).orElseGet(List::of);
        List<String> answerHtml = Optional.ofNullable(
                question.getAnswerHtml()).orElseGet(List::of);
        int optionCount = Math.max(normalizedAnswers.size(), answerHtml.size());
        for (ParsedQuestionAssetDto asset : assets(question)) {
            if (asset.getContentLocation() == QuestionContentLocation.ANSWER) {
                optionCount = Math.max(optionCount, asset.getAnswerIndex() + 1);
            }
        }

        for (int index = 0; index < optionCount; index++) {
            String contentFingerprint = answerContentFingerprint(question, index);
            if (contentFingerprint == null) {
                continue;
            }
            String normalizedText = index < normalizedAnswers.size()
                    ? normalizedAnswers.get(index)
                    : "";
            optionTokens.add("answer-option:" + normalizedText
                    + ":" + contentFingerprint);
        }
        return finish(optionTokens);
    }

    private static List<ParsedQuestionAssetDto> assets(
            ParsedTestQuestionDto question) {
        return Optional.ofNullable(question.getAssets()).orElseGet(List::of);
    }

    private static void collectStructuredTokens(
            String prefix, String html, List<String> tokens) {
        if (html == null || html.isBlank()) {
            return;
        }
        Document document = Jsoup.parseBodyFragment(html);
        document.select(".math-tex[data-tex]").forEach(element ->
                tokens.add(prefix + ":math:" + element.attr("data-tex").trim()));
        document.select("table, math, svg").forEach(element ->
                tokens.add(prefix + ":structure:" + compact(element.outerHtml())));
    }

    private static String finish(List<String> tokens) {
        if (tokens.isEmpty()) {
            return null;
        }
        tokens.sort(String::compareTo);
        return sha256(String.join("\n", tokens).getBytes(StandardCharsets.UTF_8));
    }

    private static String sha256(byte[] content) {
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(content));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is unavailable", e);
        }
    }

    private static String compact(String value) {
        return value.replaceAll("\\s+", " ").trim();
    }

    private static String safe(String value) {
        return value == null ? "" : value.trim();
    }
}
