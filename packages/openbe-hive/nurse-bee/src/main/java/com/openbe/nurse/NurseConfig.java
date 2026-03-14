package com.openbe.nurse;

import com.openbe.memory.SoulStorage;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class NurseConfig {

    @Bean
    public SoulStorage soulStorage() {
        return new SoulStorage();
    }
}
