package co.idrd.eventos;

import java.time.LocalDate;

/** Un evento deportivo del IDRD (una fila de la hoja "Datos"). */
public record Evento(int id, String categoria, String disciplina, String localidad,
                     int asistentes, LocalDate fecha) {

    /** Nombre corto para mostrar en mensajes. */
    public String titulo() {
        return disciplina + " - " + localidad;
    }
}
