package com.ryn.creativeai.core.application.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import java.util.Map;

/**
 * Compila un JSON-plantilla sustituyendo placeholders {{key}} por literales JSON.
 * - Soporta placeholders entre comillas ("{{key}}") y sin comillas.
 * - Serializa valores con Jackson para que el JSON resultante sea válido.
 * - Lanza RuntimeException si el resultado final no es JSON válido.
 */
@Service
public class TemplateCompiler {
    private static final ObjectMapper M = new ObjectMapper();

    public String compile(String template, Map<String,Object> params) {
        String out = template;
        for (var e : params.entrySet()) {
            String placeholder = "{{" + e.getKey() + "}}";
            String jsonValue;
            try {
                jsonValue = M.writeValueAsString(e.getValue()); // serializa correctamente
            } catch (Exception ex) {
                jsonValue = "\"" + e.getValue().toString() + "\"";
            }

            // 1) Si estaba con comillas en el template → reemplazo quitando comillas
            out = out.replace("\"" + placeholder + "\"", jsonValue);

            // 2) Si estaba sin comillas → reemplazo directo
            out = out.replace(placeholder, jsonValue);
        }

        // Validación final
        try {
            M.readTree(out); // lanza excepción si no es JSON válido
            return out;
        } catch (Exception e) {
            throw new RuntimeException("Resultado del template no es JSON válido", e);
        }
    }
}