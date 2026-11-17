package com.myprojects.kpok2.repository;

import com.myprojects.kpok2.model.QuestionAsset;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface QuestionAssetRepository extends JpaRepository<QuestionAsset, Long> {
    Optional<QuestionAsset> findByContentHash(String contentHash);
}
