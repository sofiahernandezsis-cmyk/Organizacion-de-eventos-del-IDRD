package co.idrd.eventos;

import jakarta.mail.*;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;

import java.util.Properties;

/** Envia correos por SMTP. Sin credenciales funciona en modo simulacion. */
public class EmailService {
    private final Config cfg;

    public EmailService(Config cfg) {
        this.cfg = cfg;
    }

    public boolean esSimulacion() {
        return !cfg.smtpConfigurado();
    }

    public void enviar(String para, String asunto, String cuerpo) throws MessagingException {
        if (esSimulacion()) return; // el notificador ya registra el mensaje en pantalla

        String usuario = cfg.get("smtp.usuario", "");
        String clave = cfg.get("smtp.clave", "");
        Properties props = new Properties();
        props.put("mail.smtp.auth", "true");
        props.put("mail.smtp.starttls.enable", "true");
        props.put("mail.smtp.host", cfg.get("smtp.host", "smtp.gmail.com"));
        props.put("mail.smtp.port", cfg.get("smtp.puerto", "587"));
        props.put("mail.smtp.connectiontimeout", "10000");
        props.put("mail.smtp.timeout", "15000");

        Session sesion = Session.getInstance(props, new Authenticator() {
            @Override
            protected PasswordAuthentication getPasswordAuthentication() {
                return new PasswordAuthentication(usuario, clave);
            }
        });
        MimeMessage msg = new MimeMessage(sesion);
        msg.setFrom(new InternetAddress(cfg.get("smtp.remitente", usuario)));
        msg.setRecipients(Message.RecipientType.TO, InternetAddress.parse(para));
        msg.setSubject(asunto, "UTF-8");
        msg.setText(cuerpo, "UTF-8");
        Transport.send(msg);
    }
}
