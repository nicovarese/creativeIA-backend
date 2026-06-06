package com.ryn.creativeai.core.ports;

import java.util.UUID;

/**
 * Punto de extensión para conectar un entrenador real (ai-toolkit, Replicate, etc.).
 * Implementación actual: {@code StubTrainingExecutor} simula el ciclo y deja un placeholder.
 * Para producción real, reemplazar/agregar una implementación que ejecute el training real
 * (subprocess Python, API externa, etc.) y avance los estados en DB.
 */
public interface TrainingExecutorPort {
    /** Encola el training. La ejecución debe ser async; no bloquear al caller. */
    void trainAsync(UUID brandLoraId);
}
