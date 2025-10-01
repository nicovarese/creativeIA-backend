package com.ryn.creativeai.core.ports;

public interface TemplateRegistryPort {
    /** Lee un recurso del classpath (relativo a la base configurada) y devuelve su contenido. */
    String readClasspath(String path);

    /** Igual que readClasspath, pero puede devolver null si está configurado así. */
    String readClasspathOrNull(String path);
}
