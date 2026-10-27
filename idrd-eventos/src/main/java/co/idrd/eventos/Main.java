package co.idrd.eventos;

import javax.swing.*;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;

/**
 * Punto de entrada.
 *   Sin argumentos  -> abre la ventana.
 *   --notificar     -> solo revisa y envia los avisos pendientes y termina
 *                      (util para programarlo con el Programador de tareas / cron).
 */
public class Main {
    public static void main(String[] args) throws Exception {
        Config cfg = new Config(Path.of("config.properties"));

        if (args.length > 0 && args[0].equals("--notificar")) {
            List<Evento> eventos = ExcelRepository.cargar(Path.of(cfg.get("excel.ruta", "data/Eventos_IDRD.xlsx")),
                    cfg.getInt("anio.por.defecto", LocalDate.now().getYear()));
            var repo = new SuscripcionRepository(Path.of(cfg.get("suscripciones.ruta", "data/suscripciones.csv")));
            int n = new Notificador(() -> eventos, repo, new EmailService(cfg), System.out::println).revisar();
            System.out.println("Avisos enviados: " + n);
            return;
        }

        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception ignorada) { }
        SwingUtilities.invokeLater(() -> new VentanaPrincipal(cfg).setVisible(true));
    }
}
