package com.myprojects.kpok2.controller;

import com.myprojects.kpok2.service.parser.TestParser;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class ParserController {
    private final TestParser testParser;

    @PostMapping("/parse")
    public String parseTest(@RequestParam String url) {
        testParser.parseTest(url);
        return "Parsing started";
    }
}