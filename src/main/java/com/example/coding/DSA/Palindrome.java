package com.example.coding.DSA;

import com.example.coding.design.DsaCode;
import com.example.coding.design.SingletonLogger;

public class Palindrome implements DsaCode {

    SingletonLogger log= SingletonLogger.getInstance();

    @Override
    public void dsaChallenge() {
        log.message("dsaChallenge");
    }

    @Override
    public void dsaChallenge(String str) {
        int left=0;
        int right = str.length()-1;
        boolean isPalindrome=true;

        while(left<right) {
            if(str.charAt(left)!=str.charAt(right)) {
                isPalindrome=false;
                break;
            }
            left ++;
            right --;
        }
        if(isPalindrome) {
            log.message(str + " is palindrome");
        } else {
            log.message(str + " is not palindrome");
        }
    }
}
