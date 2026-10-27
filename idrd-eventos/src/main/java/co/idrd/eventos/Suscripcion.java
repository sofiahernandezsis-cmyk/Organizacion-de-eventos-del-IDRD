package co.idrd.eventos;

/** Un usuario que quiere que le avisen de un evento X dias antes. */
public class Suscripcion {
    public final String correo;
    public final int eventoId;
    public int diasAntes;
    public boolean enviado;

    public Suscripcion(String correo, int eventoId, int diasAntes, boolean enviado) {
        this.correo = correo;
        this.eventoId = eventoId;
        this.diasAntes = diasAntes;
        this.enviado = enviado;
    }
}
