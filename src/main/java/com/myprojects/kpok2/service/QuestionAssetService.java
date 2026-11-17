package com.myprojects.kpok2.service;

import com.myprojects.kpok2.model.QuestionAsset;
import com.myprojects.kpok2.repository.QuestionAssetRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/** Provides stored question images to API clients. */
@Service
@RequiredArgsConstructor
public class QuestionAssetService {

    private final QuestionAssetRepository assetRepository;

    @Transactional(readOnly = true)
    public Optional<QuestionAsset> findById(Long id) {
        return assetRepository.findById(id);
    }
}
