package com.ryn.creativeai;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ryn.creativeai.core.domain.model.Project;
import com.ryn.creativeai.core.domain.model.Role;
import com.ryn.creativeai.core.domain.model.User;
import com.ryn.creativeai.infra.AssetRepository;
import com.ryn.creativeai.infra.JobRepository;
import com.ryn.creativeai.infra.ProjectRepository;
import com.ryn.creativeai.infra.UserRepository;
import com.ryn.creativeai.security.JwtService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ProjectSecurityIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository users;

    @Autowired
    private ProjectRepository projects;

    @Autowired
    private JobRepository jobs;

    @Autowired
    private AssetRepository assets;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtService jwtService;

    @AfterEach
    void cleanup() {
        assets.deleteAll();
        jobs.deleteAll();
        projects.deleteAll();
        users.deleteAll();
    }

    @Test
    void createProjectRejectsDuplicateNameForSameOwner() throws Exception {
        User owner = saveUser("owner@example.com");
        String token = authToken(owner);

        mockMvc.perform(post("/v1/projects")
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("name", "Campania Marzo"))))
                .andExpect(status().isOk());

        mockMvc.perform(post("/v1/projects")
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("name", "campania marzo"))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Ya existe un proyecto con ese nombre"));
    }

    @Test
    void createJobRejectsProjectsFromAnotherOwner() throws Exception {
        User owner = saveUser("owner@example.com");
        User outsider = saveUser("outsider@example.com");

        Project project = new Project();
        project.setName("Privado");
        project.setOwner(owner);
        project = projects.save(project);

        String token = authToken(outsider);
        Map<String, Object> payload = Map.of(
                "projectId", project.getId().toString(),
                "flow", "txt2img",
                "prompt", "studio product shot",
                "width", 768,
                "height", 768,
                "batch", 1
        );

        mockMvc.perform(post("/v1/generate")
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("project not found"));
    }

    private User saveUser(String email) {
        User user = new User();
        user.setEmail(email);
        user.setPasswordHash(passwordEncoder.encode("12345678"));
        user.setRole(Role.USER);
        return users.save(user);
    }

    private String authToken(User user) {
        return jwtService.generate(user.getEmail(), Map.of("role", user.getRole().name()));
    }

    private String bearer(String token) {
        return "Bearer " + token;
    }
}
