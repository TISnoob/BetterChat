package com.tis199.betterchat.common.update;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.math.BigInteger;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletionException;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class ReleaseUpdateChecker {
    private static final URI RELEASE_URI = URI.create(
            "https://api.github.com/repos/TISnoob/BetterChat/releases/latest");
    private static final String RELEASES_PAGE = "https://github.com/TISnoob/BetterChat/releases/latest";
    private static final Pattern VERSION_PATTERN = Pattern.compile(
            "^v?(\\d+(?:\\.\\d+)*)(?:-([0-9A-Za-z-]+(?:\\.[0-9A-Za-z-]+)*))?(?:\\+[0-9A-Za-z-]+(?:\\.[0-9A-Za-z-]+)*)?$");
    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();

    private ReleaseUpdateChecker() { }

    public static void check(String installedVersion, Consumer<String> onUpdateAvailable,
                             Consumer<String> onFailure) {
        HttpRequest request = HttpRequest.newBuilder(RELEASE_URI)
                .timeout(Duration.ofSeconds(8))
                .header("Accept", "application/vnd.github+json")
                .header("User-Agent", "BetterChat-UpdateChecker")
                .GET()
                .build();

        HTTP_CLIENT.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenAccept(response -> {
                    // GitHub returns 404 when the repository has no published stable release.
                    if (response.statusCode() == 404) return;
                    if (response.statusCode() != 200) {
                        throw new IllegalStateException("GitHub returned HTTP " + response.statusCode());
                    }
                    JsonObject release = JsonParser.parseString(response.body()).getAsJsonObject();
                    String latestVersion = release.get("tag_name").getAsString();
                    if (compareVersions(latestVersion, installedVersion) > 0) {
                        onUpdateAvailable.accept("A new BetterChat release is available: " + latestVersion
                                + " (installed: " + installedVersion + "). Download: " + RELEASES_PAGE);
                    }
                })
                .exceptionally(error -> {
                    onFailure.accept("Could not check BetterChat releases on GitHub: " + failureMessage(error));
                    return null;
                });
    }

    private static int compareVersions(String left, String right) {
        Version first = parseVersion(left);
        Version second = parseVersion(right);
        int components = Math.max(first.components().size(), second.components().size());
        for (int index = 0; index < components; index++) {
            BigInteger firstPart = componentAt(first, index);
            BigInteger secondPart = componentAt(second, index);
            int comparison = firstPart.compareTo(secondPart);
            if (comparison != 0) return comparison;
        }

        if (first.preRelease().isEmpty()) return second.preRelease().isEmpty() ? 0 : 1;
        if (second.preRelease().isEmpty()) return -1;
        int identifiers = Math.min(first.preRelease().size(), second.preRelease().size());
        for (int index = 0; index < identifiers; index++) {
            String firstIdentifier = first.preRelease().get(index);
            String secondIdentifier = second.preRelease().get(index);
            boolean firstNumeric = firstIdentifier.matches("\\d+");
            boolean secondNumeric = secondIdentifier.matches("\\d+");
            int comparison;
            if (firstNumeric && secondNumeric) {
                comparison = new BigInteger(firstIdentifier).compareTo(new BigInteger(secondIdentifier));
            } else if (firstNumeric != secondNumeric) {
                comparison = firstNumeric ? -1 : 1;
            } else {
                comparison = firstIdentifier.compareTo(secondIdentifier);
            }
            if (comparison != 0) return comparison;
        }
        return Integer.compare(first.preRelease().size(), second.preRelease().size());
    }

    private static BigInteger componentAt(Version version, int index) {
        return index < version.components().size() ? version.components().get(index) : BigInteger.ZERO;
    }

    private static Version parseVersion(String value) {
        Matcher matcher = VERSION_PATTERN.matcher(value);
        if (!matcher.matches()) {
            throw new IllegalArgumentException("Unrecognized version: " + value);
        }
        List<BigInteger> components = new ArrayList<>();
        for (String component : matcher.group(1).split("\\.")) {
            components.add(new BigInteger(component));
        }
        String preRelease = matcher.group(2);
        return new Version(components, preRelease == null ? List.of() : List.of(preRelease.split("\\.")));
    }

    private static String failureMessage(Throwable error) {
        Throwable cause = error instanceof CompletionException && error.getCause() != null
                ? error.getCause() : error;
        String message = cause.getMessage();
        return message == null || message.isBlank() ? cause.getClass().getSimpleName() : message;
    }

    private record Version(List<BigInteger> components, List<String> preRelease) { }
}
