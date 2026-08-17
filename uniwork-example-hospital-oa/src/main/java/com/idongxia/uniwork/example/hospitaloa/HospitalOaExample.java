package com.idongxia.uniwork.example.hospitaloa;

import com.idongxia.uniwork.UniWork;

/** 不依赖 Spring 的最小 Java 8 调用示例。Minimal Java 8 usage without Spring. */
public final class HospitalOaExample {

    private HospitalOaExample() {
    }

    public static void main(String[] args) {
        UniWork uniWork = UniWork.load("hospital-oa-example.yml");
        try {
            uniWork.platform(HospitalOaChannel.class)
                    .sendContent("EMP10086", "采购项目等待审批");
        } finally {
            uniWork.close();
        }
    }
}
