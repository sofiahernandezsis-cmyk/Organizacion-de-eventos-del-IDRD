package co.idrd.eventos;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/** Revisa las suscripciones y avisa por correo cuando el evento esta proximo. */
public class Notificador {
    public static final DateTimeFormatter FECHA = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private final Supplier<List<Evento>> eventos;
    private final SuscripcionRepository repo;
    private final EmailService email;
    private final Consumer<String> log;

    public Notificador(Supplier<List<Evento>> eventos, SuscripcionRepository repo, EmailService email, Consumer<String> log) {
        this.eventos = eventos;
        this.repo = repo;
        this.email = email;
        this.log = log;
    }

    /** Envia los avisos pendientes. Devuelve cuantos se enviaron. */
    public synchronized int revisar() {
        Map<Integer, Evento> porId = eventos.get().stream()
                .collect(Collectors.toMap(Evento::id, e -> e, (a, b) -> a));
        LocalDate hoy = LocalDate.now();
        int enviados = 0;
        for (Suscripcion s : repo.listar(null)) {
            if (s.enviado) continue;
            Evento e = porId.get(s.eventoId);
            if (e == null) continue;
            long dias = ChronoUnit.DAYS.between(hoy, e.fecha());
            if (dias < 0 || dias > s.diasAntes) continue; // aun no toca, o el evento ya paso

            String asunto = "Recordatorio IDRD: " + e.disciplina() + " en " + e.localidad() + " - " + e.fecha().format(FECHA);
            String cuando = dias == 0 ? "HOY" : dias == 1 ? "manana" : "en " + dias + " dias";
            String cuerpo = "Hola,\n\n"
                    + "Te recordamos que el siguiente evento deportivo del IDRD empieza " + cuando + ":\n\n"
                    + "  Disciplina: " + e.disciplina() + "\n"
                    + "  Tipo:       " + e.categoria() + "\n"
                    + "  Localidad:  " + e.localidad() + "\n"
                    + "  Fecha:      " + e.fecha().format(FECHA) + "\n\n"
                    + "Gracias por usar el buscador de eventos del IDRD.\n";
            try {
                email.enviar(s.correo, asunto, cuerpo);
                repo.marcarEnviado(s);
                enviados++;
                log.accept((email.esSimulacion() ? "[SIMULACION] " : "") + "Aviso para " + s.correo + ": " + asunto);
            } catch (Exception ex) {
                log.accept("ERROR enviando a " + s.correo + ": " + ex.getMessage());
            }
        }
        return enviados;
    }
}
