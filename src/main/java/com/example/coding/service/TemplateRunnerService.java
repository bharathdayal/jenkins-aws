package com.example.coding.service;

import com.example.coding.design.DsaCode;
import com.example.coding.design.FactoryDSA;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.IntStream;

@Service
public class TemplateRunnerService {

    public String runPalindrome(String input) {
        StringBuilder sb = new StringBuilder();
        // assume FactoryDSA returns a DsaCode with dsaChallenge that prints or returns;
        // if it only prints via SingletonLogger, return that string instead — here's a safe wrapper approach:
        DsaCode palindrome = FactoryDSA.getMethod("Palindrome");
        // If dsaChallenge returns void and logs to SingletonLogger, we can call and return a small note:
        palindrome.dsaChallenge(input);
        sb.append("Ran Palindrome on: ").append(input).append(" (check application logs or logger output).");
        return sb.toString();
    }

    public String runArrayNonZero() {
        DsaCode arrayNonZero = FactoryDSA.getMethod("ArrayNonZero");
        arrayNonZero.dsaChallenge();
        return "Ran ArrayNonZero (check logs or logger output).";
    }

    public String runLongSubString(String input) {
        DsaCode longSubString = FactoryDSA.getMethod("LongSubString");
        longSubString.dsaChallenge(input);
        return "Ran LongSubString on: " + input + " (check logs).";
    }

    // Streams examples returning human readable output
    public String runStreamsEvenIntArray() {
        int[] intArr = {1,2,3,4,5,6,7,8,9,10};
        StringBuilder sb = new StringBuilder();
        sb.append("Even numbers from int[] -> ");
        IntStream.of(intArr)
                .filter(n->n%2==0)
                .forEach(n -> sb.append(n).append(" "));
        return sb.toString();
    }

    public String runStreamsEvenList() {
        List<Integer> intList = List.of(1,2,3,4,5,6,7,8,9,10);
        List<Integer> even = intList.stream().filter(n->n%2==0).toList();
        return "Even numbers from List -> " + even;
    }

    public String runStreamsSquareList() {
        List<Integer> intSquare = List.of(1,2,3,4,5);
        List<Integer> squares = intSquare.stream().map(n->n*n).toList();
        return "Squares -> " + squares;
    }

    public String runStreamsSum(List<Integer> list) {
        // example sum
        var sum = list.stream().reduce(Integer::sum).orElse(0);
        return "Sum of " + list + " = " + sum;
    }
}
