package com.ryn.creativeai.core.application.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

/**
 * Serializa el acceso a la GPU local entre dos consumidores:
 *  - generación de imágenes (ComfyUI vía ComfyUIAdapter)
 *  - entrenamiento de LoRA (AiToolkitTrainingExecutor)
 *
 * Solo 1 puede tener la GPU a la vez. La generación cede al training (ambos
 * compiten por el mismo permiso). Si querés despachar gen mientras entrenás,
 * te conviene una segunda GPU o burst a Replicate; ver TrainingExecutorPort.
 */
@Slf4j
@Component
public class GpuMutex {

    private final Semaphore semaphore = new Semaphore(1, true);

    public boolean tryAcquire(String holder, long timeout, TimeUnit unit) throws InterruptedException {
        log.debug("GPU lock requested by {}", holder);
        boolean got = semaphore.tryAcquire(timeout, unit);
        if (got) log.info("GPU lock acquired by {}", holder);
        else      log.warn("GPU lock timeout by {} after {} {}", holder, timeout, unit);
        return got;
    }

    public void acquire(String holder) throws InterruptedException {
        log.debug("GPU lock requested by {} (blocking)", holder);
        semaphore.acquire();
        log.info("GPU lock acquired by {}", holder);
    }

    public void release(String holder) {
        semaphore.release();
        log.info("GPU lock released by {}", holder);
    }

    public boolean isBusy() {
        return semaphore.availablePermits() == 0;
    }
}
