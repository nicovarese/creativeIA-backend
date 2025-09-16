package com.ryn.creativeai.core.application.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Objects;

/**
 * Resuelve parámetros de un template a partir de su schema:
 * - Merge defaults <- input
 * - Valida required, additionalProperties=false, type (string/integer),
 * enum, minimum/maximum (para integer).
 * - Coercea enteros desde string si es posible.
 * Lanza IllegalArgumentException con mensajes claros ante errores.
 */
@Service
public class ParamResolver {

    private static final ObjectMapper M = new ObjectMapper();

    public Map<String, Object> resolve(String schemaJson, Map<String, Object> inputParams) {
        try {
            JsonNode schema = (schemaJson == null || schemaJson.isBlank())
                    ? M.readTree("{}")
                    : M.readTree(schemaJson);

            Map<String, Object> out = new HashMap<>();

            // 1) defaults
            if (schema.has("defaults")) {
                Map<String, Object> defaults = M.convertValue(
                        schema.get("defaults"),
                        new TypeReference<Map<String, Object>>() {
                        });
                if (defaults != null) out.putAll(defaults);
            }

            // 2) merge input
            if (inputParams != null) out.putAll(inputParams);

            // 3) validation (lanza IllegalArgumentException con mensajes claros)
            validate(schema, out);   // <-- ACÁ se disparan todas las IllegalArgumentException

            // 4) normalize types (coerción final según "type")
            normalizeTypes(schema, out);

            return out;
        } catch (RuntimeException re) {
            throw re;
        } catch (Exception e) {
            throw new RuntimeException("Error resolviendo parámetros del template", e);
        }
    }

    private void validate(JsonNode schema, Map<String, Object> params) {
        JsonNode props = schema.get("properties");

        // required
        if (schema.has("required")) {
            for (JsonNode req : schema.get("required")) {
                String key = req.asText();
                if (!params.containsKey(key)) {
                    throw new IllegalArgumentException("Falta parámetro requerido: " + key); // <-- ACÁ
                }
            }
        }

        // additionalProperties:false => no permitir claves no definidas
        boolean additionalPropsAllowed = !schema.has("additionalProperties")
                || !schema.get("additionalProperties").isBoolean()
                || schema.get("additionalProperties").asBoolean(true);

        if (!additionalPropsAllowed && props != null && props.isObject()) {
            for (String k : params.keySet()) {
                if (!props.has(k)) {
                    throw new IllegalArgumentException("Parámetro no permitido: " + k); // <-- ACÁ
                }
            }
        }

        // Validaciones por propiedad (enum, minimum/maximum, type básico)
        if (props != null && props.isObject()) {
            for (Iterator<String> it = props.fieldNames(); it.hasNext(); ) {
                String name = it.next();
                if (!params.containsKey(name)) continue;

                Object val = params.get(name);
                JsonNode def = props.get(name);

                // type (básico) — validación previa antes de enum/min/max
                if (def.has("type")) {
                    String t = def.get("type").asText();
                    switch (t) {
                        case "string" -> {
                            if (!(val instanceof String)) {
                                // Permitimos coerción posterior, pero si querés fallar aquí, descomentá:
                                // throw new IllegalArgumentException("El parámetro " + name + " debe ser string");
                            }
                        }
                        case "integer" -> {
                            if (tryToLong(val) == null) {
                                throw new IllegalArgumentException("El parámetro " + name + " debe ser integer"); // <-- ACÁ
                            }
                        }
                        // otros tipos: ignoramos en este MVP
                    }
                }

                // enum
                if (def.has("enum")) {
                    boolean ok = false;
                    for (JsonNode n : def.get("enum")) {
                        if (n.isNumber() && val instanceof Number) {
                            if (n.asLong() == ((Number) val).longValue()) {
                                ok = true;
                                break;
                            }
                        } else if (Objects.equals(n.asText(), String.valueOf(val))) {
                            ok = true;
                            break;
                        }
                    }
                    if (!ok) {
                        throw new IllegalArgumentException("Valor de " + name + " fuera de enum"); // <-- ACÁ
                    }
                }

                // minimum/maximum (aplica si type: integer)
                if (def.has("type") && "integer".equals(def.get("type").asText())) {
                    Long num = tryToLong(val);
                    if (num == null) {
                        throw new IllegalArgumentException("El parámetro " + name + " debe ser integer"); // <-- ACÁ (redundante pero claro)
                    }
                    if (def.has("minimum") && num < def.get("minimum").asLong()) {
                        throw new IllegalArgumentException(name + " < minimum (" + def.get("minimum").asLong() + ")"); // <-- ACÁ
                    }
                    if (def.has("maximum") && num > def.get("maximum").asLong()) {
                        throw new IllegalArgumentException(name + " > maximum (" + def.get("maximum").asLong() + ")"); // <-- ACÁ
                    }
                }
            }
        }
    }

    private void normalizeTypes(JsonNode schema, Map<String, Object> params) {
        JsonNode props = schema.get("properties");
        if (props == null || !props.isObject()) return;

        for (Iterator<String> it = props.fieldNames(); it.hasNext(); ) {
            String name = it.next();
            if (!params.containsKey(name)) continue;

            JsonNode def = props.get(name);
            if (!def.has("type")) continue;

            String type = def.get("type").asText();
            Object val = params.get(name);

            switch (type) {
                case "integer" -> {
                    Long l = tryToLong(val);
                    if (l == null) {
                        throw new IllegalArgumentException("El parámetro " + name + " debe ser integer"); // <-- ACÁ (coerción fallida)
                    }
                    params.put(name, l.intValue()); // o l si querés long
                }
                case "string" -> {
                    if (!(val instanceof String)) params.put(name, String.valueOf(val));
                }
            }
        }
    }

    private Long tryToLong(Object v) {
        if (v == null) return null;
        if (v instanceof Number n) return n.longValue();
        try {
            return Long.parseLong(String.valueOf(v));
        } catch (Exception e) {
            return null;
        }
    }
}


