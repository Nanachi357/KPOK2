package com.myprojects.kpok2.service.update;

import com.vdurmont.semver4j.Semver;
import org.kohsuke.github.GHRelease;
import org.kohsuke.github.GHRepository;
import org.kohsuke.github.GitHub;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.Optional;

@Service
public class UpdateChecker {
    private static final String REPOSITORY = "Nanachi357/KPOK2";
    private final String currentVersion;

    public record ReleaseInfo(
        String version,
        String changelog,
        String releaseUrl,
        String downloadUrl
    ) {}

    public UpdateChecker(@Value("${application.version}") String currentVersion) {
        this.currentVersion = currentVersion;
    }

    public Optional<ReleaseInfo> checkForUpdates() {
        try {
            GitHub github = GitHub.connectAnonymously();
            GHRepository repository = github.getRepository(REPOSITORY);
            GHRelease latestRelease = repository.getLatestRelease();

            if (latestRelease == null) {
                return Optional.empty();
            }

            String latestVersion = latestRelease.getTagName().replace("v", "");
            Semver current = new Semver(currentVersion);
            Semver latest = new Semver(latestVersion);

            if (latest.isGreaterThan(current)) {
                return Optional.of(new ReleaseInfo(
                    latestVersion,
                    latestRelease.getBody(),
                    latestRelease.getHtmlUrl().toString(),
                    latestRelease.getAssets().isEmpty() 
                        ? null 
                        : latestRelease.getAssets().get(0).getBrowserDownloadUrl()
                ));
            }

            return Optional.empty();
        } catch (IOException e) {
            // Log error if needed
            return Optional.empty();
        }
    }
} 