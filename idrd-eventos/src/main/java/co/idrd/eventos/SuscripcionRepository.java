package co.idrd.eventos;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/** Guarda los correos registrados en un archivo CSV (correo;eventoId;diasAntes;enviado). */
public class SuscripcionRepository {
    private static final Pattern CORREO = Pattern.compile("^[\\w.+-]+@[\\w-]+(\\.[\\w-]+)+$");

    private final Path archivo;
    private final List<Suscripcion> lista = new ArrayList<>();

    public SuscripcionRepository(Path archivo) {
        this.archivo = archivo;
        cargar();
    }

    public static boolean correoValido(String c) {
        return c != null && CORREO.matcher(c.trim()).matches();
    }

    private void cargar() {
        if (!Files.exists(archivo)) return;
        try {
            for (String linea : Files.readAllLines(archivo, StandardCharsets.UTF_8)) {
                String[] p = linea.split(";");
                if (p.length < 4) continue;
                try {
                    lista.add(new Suscripcion(p[0], Integer.parseInt(p[1]), Integer.parseInt(p[2]), Boolean.parseBoolean(p[3])));
                } catch (NumberFormatException ignorada) { }
            }
        } catch (IOException e) {
            System.err.println("No se pudo leer " + archivo + ": " + e.getMessage());
        }
    }

    private void guardar() {
        try {
            if (archivo.getParent() != null) Files.createDirectories(archivo.getParent());
            List<String> lineas = new ArrayList<>();
            for (Suscripcion s : lista) lineas.add(s.correo + ";" + s.eventoId + ";" + s.diasAntes + ";" + s.enviado);
            Files.write(archivo, lineas, StandardCharsets.UTF_8);
        } catch (IOException e) {
            System.err.println("No se pudo guardar " + archivo + ": " + e.getMessage());
        }
    }

    /** Registra (o actualiza) el aviso. Si cambia, se vuelve a permitir el envio. */
    public synchronized void agregar(String correo, int eventoId, int diasAntes) {
        String c = correo.trim().toLowerCase();
        for (Suscripcion s : lista) {
            if (s.correo.equals(c) && s.eventoId == eventoId) {
                s.diasAntes = diasAntes;
                s.enviado = false;
                guardar();
                return;
            }
        }
        lista.add(new Suscripcion(c, eventoId, diasAntes, false));
        guardar();
    }

    public synchronized void eliminar(String correo, int eventoId) {
        lista.removeIf(s -> s.correo.equals(correo) && s.eventoId == eventoId);
        guardar();
    }

    public synchronized void marcarEnviado(Suscripcion s) {
        s.enviado = true;
        guardar();
    }

    /** Copia de la lista (opcionalmente solo de un correo). */
    public synchronized List<Suscripcion> listar(String correo) {
        List<Suscripcion> r = new ArrayList<>();
        for (Suscripcion s : lista) if (correo == null || correo.isBlank() || s.correo.equals(correo.trim().toLowerCase())) r.add(s);
        return r;
    }
}
