package com.example.coding.DSA;

import com.example.coding.design.DsaCode;
import com.example.coding.design.SingletonLogger;

import java.util.HashSet;
import java.util.Set;

public class LongSubString implements DsaCode {

    SingletonLogger log= SingletonLogger.getInstance();

    @Override
    public void dsaChallenge() {

    }

    @Override
    public void dsaChallenge(String str) {

        int left=0;
        int maxLength=0;
        Set<Character> set=new HashSet<>();

        for(int right=0;right<str.length();right++) {
            char c = str.charAt(right);

            while(set.contains(c)) {
                set.remove(str.charAt(left));
                left++;
            }
            set.add(c);
            maxLength=Math.max(maxLength,right-left+1);
        }
        log.message(maxLength);
    }
}
