package com.axedu;

import java.util.TimeZone;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class AxeduApplication {

    public static void main(String[] args) {
        // 모든 시각은 KST(+09:00) 기준으로 다룬다 (계약서 0장 14번)
        TimeZone.setDefault(TimeZone.getTimeZone("Asia/Seoul"));
        SpringApplication.run(AxeduApplication.class, args);
    }
}
