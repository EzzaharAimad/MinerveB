package com.quizagent.controller;

import com.quizagent.model.Exercise;
import com.quizagent.service.GptService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api")
@CrossOrigin(origins = "http://localhost:4200")
public class ExerciseController {

    @Autowired
    private GptService gptService;

    @PostMapping(value = "/generate", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public List<Exercise> generate(
            @RequestParam("evaluationType") String evaluationType,
            @RequestParam("bloomLevel") String bloomLevel,
            @RequestParam("inputText") String inputText,
            @RequestParam(value = "file", required = false) MultipartFile file) throws IOException {

        String fullInput = inputText;
        if (file != null && !file.isEmpty()) {
            String fileText = new BufferedReader(new InputStreamReader(file.getInputStream(), StandardCharsets.UTF_8))
                    .lines().collect(Collectors.joining("\n"));
            fullInput += "\n" + fileText;
        }

        return gptService.generateFromText(fullInput, evaluationType, bloomLevel);
    }
    @PostMapping("/chat")
    public String chat(@RequestBody String userMessage) {
        return gptService.simpleChat(userMessage);
    }
}