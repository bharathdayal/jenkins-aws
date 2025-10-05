package com.example.coding.design;

import com.example.coding.DSA.ArrayNonZero;
import com.example.coding.DSA.LongSubString;
import com.example.coding.DSA.Palindrome;

public class FactoryDSA {

    public static DsaCode getMethod(String type) {

        if("Palindrome".equalsIgnoreCase(type)) {
            return new Palindrome();
        }
        if("ArrayNonZero".equalsIgnoreCase(type)){
            return new ArrayNonZero();
        }
        if("LongSubString".equalsIgnoreCase(type)) {
            return new LongSubString();
        }
        return null;
    }
}
