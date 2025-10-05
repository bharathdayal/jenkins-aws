package com.example.coding.DSA;

import com.example.coding.design.DsaCode;
import com.example.coding.design.SingletonLogger;

import java.util.Arrays;
import java.util.List;

public class ArrayNonZero implements DsaCode {

    SingletonLogger log= SingletonLogger.getInstance();

    @Override
    public void dsaChallenge() {
        int[] arr = {0, 3, 0, 1, 0, 5, 0, 2,9,0,55};

        int left =0;
        for(int right=0;right<arr.length;right++) {
            if(arr[right] !=0) {
                arr[left]=arr[right];
                left++;
            }
        }
        while(left<arr.length) {
            arr[left]=0;
            left++;
        }
        List<Integer> result = Arrays.stream(arr).boxed().toList();
        log.message(result.toString());

    }
    @Override
    public void dsaChallenge(String str) {
        log.message("dsaChallenge");
    }
}
