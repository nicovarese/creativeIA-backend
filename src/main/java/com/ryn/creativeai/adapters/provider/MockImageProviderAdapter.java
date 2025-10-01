package com.ryn.creativeai.adapters.provider;

import com.ryn.creativeai.core.ports.ImageProviderPort;
import org.springframework.stereotype.Component;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.List;
import java.util.UUID;

@Component("mock")
public class MockImageProviderAdapter implements ImageProviderPort {

    @Override
    public List<String> generate(String compiledWorkflowJson) throws Exception {
        // Generamos una imagen PNG simple en /tmp (o carpeta del proyecto)
        String filename = "mock_" + UUID.randomUUID() + ".png";
        File out = new File("./tmp", filename);
        out.getParentFile().mkdirs();

        BufferedImage img = new BufferedImage(768, 512, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        g.setColor(Color.DARK_GRAY);
        g.fillRect(0, 0, img.getWidth(), img.getHeight());
        g.setColor(Color.WHITE);
        g.drawString("MOCK IMAGE", 20, 30);
        g.dispose();

        ImageIO.write(img, "png", out);
        return List.of(out.getPath()); // devolvemos el path local
    }
}
