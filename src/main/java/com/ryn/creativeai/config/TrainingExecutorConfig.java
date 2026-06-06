package com.ryn.creativeai.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

/**
 * Worker pool dedicado para entrenamientos de LoRA.
 *
 * Es de UN SOLO hilo a propósito: cada training ocupa la GPU completa, así que
 * lanzar dos en paralelo solo causa OOM. La cola crece hasta 10 trainings; si
 * llegás a ese límite probablemente quieras burst a Replicate o una segunda GPU.
 */
@Configuration
public class TrainingExecutorConfig {

    @Bean(name = "trainingExecutor")
    public Executor trainingExecutor() {
        ThreadPoolTaskExecutor ex = new ThreadPoolTaskExecutor();
        ex.setCorePoolSize(1);
        ex.setMaxPoolSize(1);
        ex.setQueueCapacity(10);
        ex.setThreadNamePrefix("training-");
        ex.setWaitForTasksToCompleteOnShutdown(false);
        ex.initialize();
        return ex;
    }
}
