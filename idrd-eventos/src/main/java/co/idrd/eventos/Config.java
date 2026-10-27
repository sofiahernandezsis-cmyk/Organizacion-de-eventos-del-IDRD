package co.idrd.eventos;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

/** Lee config.properties (si no existe, usa valores por defecto). */
public class Config {
    private final Properties p = new Properties();

    public Config(Path archivo) {
        if (Files.exists(archivo)) {
            try (InputStream in = Files.newInputStream(archivo)) {
                p.load(new java.io.InputStreamReader(in, java.nio.charset.StandardCharsets.UTF_8));
            } catch (IOException e) {
                System.err.println("No se pudo leer " + archivo + ": " + e.getMessage());
            }
        }
    }

    public String get(String clave, String defecto) {
        String v = p.getProperty(clave);
        return (v == null || v.isBlank()) ? defecto : v.trim();
    }

    public int getInt(String clave, int defecto) {
        try {
            return Integer.parseInt(get(clave, String.valueOf(defecto)));
        } catch (NumberFormatException e) {
            return defecto;
        }
    }

    public boolean smtpConfigurado() {
        return !get("smtp.usuario", "").isEmpty() && !get("smtp.clave", "").isEmpty();
    }
}
