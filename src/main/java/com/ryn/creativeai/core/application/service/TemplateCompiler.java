package com.ryn.creativeai.core.application.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class TemplateCompiler {

    private static final ObjectMapper M = new ObjectMapper();

    // Coincide con {{ key }}  o  ${ key }  (sin comillas alrededor)
    private static final Pattern PLACEHOLDER =
            Pattern.compile("\\{\\{\\s*([a-zA-Z0-9_]+)\\s*\\}\\}|\\$\\{\\s*([a-zA-Z0-9_]+)\\s*\\}");

    public String compile(String template, Map<String, Object> params) {
        if (template == null) throw new IllegalArgumentException("template is null");
        if (params == null) throw new IllegalArgumentException("params is null");

        String out = template;

        // ---------- Paso 1: reemplazo de formas ENTRE COMILLAS ----------
        // Si el template tiene: "text": "${prompt}"
        // Reemplazamos el bloque completo incluyendo las comillas
        for (var e : params.entrySet()) {
            String key = e.getKey();
            String jsonValue = toJson(e.getValue());

            // "${key}"
            out = out.replace("\"${" + key + "}\"", jsonValue);
            // "{{key}}"
            out = out.replace("\"{{" + key + "}}\"", jsonValue);
        }

        // ---------- Paso 2: reemplazo genérico de formas SIN COMILLAS ----------
        Matcher m = PLACEHOLDER.matcher(out);
        StringBuffer sb = new StringBuffer();

        while (m.find()) {
            String key = m.group(1) != null ? m.group(1) : m.group(2);

            if (!params.containsKey(key)) {
                throw new IllegalArgumentException("Falta valor para placeholder: " + key);
            }

            String jsonValue = toJson(params.get(key));
            m.appendReplacement(sb, Matcher.quoteReplacement(jsonValue));
        }
        m.appendTail(sb);
        out = sb.toString();

        // ---------- Validación final ----------
        try {
            M.readTree(out); // si no es JSON válido, lanza
            return out;
        } catch (Exception e) {
            // Ayuda para depurar si pasa de nuevo
            throw new IllegalStateException("El resultado del template no es JSON válido", e);
        }
    }

    private static String toJson(Object value) {
        try {
            // Serializa con el tipo correcto: números/boolean/objetos sin comillas;
            // strings con comillas y escapado correcto.
            return M.writeValueAsString(value);
        } catch (Exception e) {
            return "\"" + String.valueOf(value) + "\"";
        }
    }
}
