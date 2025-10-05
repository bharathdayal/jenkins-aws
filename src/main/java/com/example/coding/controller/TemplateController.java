package com.example.coding.controller;

import com.example.coding.service.TemplateRunnerService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Controller
@RequestMapping("/")
public class TemplateController {

    private final TemplateRunnerService runner;

    public TemplateController(TemplateRunnerService runner) {
        this.runner = runner;
    }

    @GetMapping
    public String home(Model model) {
        // initial page render — nothing required in model, template will load UI
        return "index";
    }

    // endpoints for AJAX calls
    @PostMapping("/run/dsa/palindrome")
    @ResponseBody
    public String runPalindrome(@RequestParam(defaultValue = "malayalam") String input) {
        return runner.runPalindrome(input);
    }

    @PostMapping("/run/dsa/arrayNonZero")
    @ResponseBody
    public String runArrayNonZero() {
        return runner.runArrayNonZero();
    }

    @PostMapping("/run/dsa/longSubString")
    @ResponseBody
    public String runLongSubString(@RequestParam(defaultValue = "abcabcbb") String input) {
        return runner.runLongSubString(input);
    }

    // Streams
    @PostMapping("/run/streams/evenArray")
    @ResponseBody
    public String runStreamsEvenArray() {
        return runner.runStreamsEvenIntArray();
    }

    @PostMapping("/run/streams/evenList")
    @ResponseBody
    public String runStreamsEvenList() {
        return runner.runStreamsEvenList();
    }

    @PostMapping("/run/streams/squares")
    @ResponseBody
    public String runStreamsSquareList() {
        return runner.runStreamsSquareList();
    }

    // example with JSON body
    @PostMapping("/run/streams/sum")
    @ResponseBody
    public String runStreamsSum(@RequestBody List<Integer> numbers) {
        return runner.runStreamsSum(numbers);
    }
}
