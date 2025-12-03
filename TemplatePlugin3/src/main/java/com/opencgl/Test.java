package com.opencgl;

import com.alibaba.fastjson2.JSON;

public class Test {
    public static void main(String[] args) {
        System.out.println(JSON.parseObject("{\n" +
            "  \"paymentReqId\": \"POS2024011256931905\",\n" +
            "  \"custId\": 2,\n" +
            "  \"extAttrList\": [\n" +
            "    {\n" +
            "      \"attrCode\": \"\",\n" +
            "      \"attrValue\": \"\"\n" +
            "    }\n" +
            "  ]\n" +
            "}"));
    }
}
