package com.ryn.creativeai.core.ports;

import java.util.List;

import java.util.List;

public interface ImageProviderPort {
    /**
     * Ejecuta el workflow compilado y devuelve rutas locales (absolute/relative)
     * de las imágenes generadas.
     *
     * @param compiledWorkflowJson JSON del workflow ya con parámetros
     * @return lista de paths locales a archivos de imagen
     */
    List<String> generate(String compiledWorkflowJson) throws Exception;
}
