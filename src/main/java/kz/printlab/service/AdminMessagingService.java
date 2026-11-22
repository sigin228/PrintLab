package kz.printlab.service;

import java.util.List;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;

/** Sends a single, explicit admin-composed message to the registered order customer. */
@Service
@PreAuthorize("hasRole('ADMIN')")
public class AdminMessagingService {
    private final JdbcTemplate jdbc;
    private final ObjectProvider<JavaMailSender> sender;
    private final String from;
    public AdminMessagingService(JdbcTemplate jdbc, ObjectProvider<JavaMailSender> sender,
                                 @Value("${printlab.mail.from:}") String from) {
        this.jdbc=jdbc; this.sender=sender; this.from=from;
    }
    public void sendOrderMessage(long orderId, String subject, String body) {
        if(subject==null || subject.isBlank() || subject.length()>150 || subject.contains("\r") || subject.contains("\n") ||
           body==null || body.isBlank() || body.length()>5000) throw new IllegalArgumentException("Проверьте тему и текст письма.");
        List<String> emails=jdbc.query("SELECT u.email FROM orders o JOIN users u ON u.id=o.user_id WHERE o.id=?",(rs,n)->rs.getString(1),orderId);
        if(emails.isEmpty()) throw new IllegalArgumentException("Заказ не найден.");
        JavaMailSender mail=sender.getIfAvailable();
        if(mail==null || from.isBlank()) throw new IllegalStateException("Отправка почты ещё не настроена.");
        SimpleMailMessage message=new SimpleMailMessage(); message.setFrom(from); message.setTo(emails.get(0));
        message.setSubject(subject.trim()); message.setText(body.trim());
        try { mail.send(message); } catch(MailException e) { throw new IllegalStateException("Не удалось отправить письмо. Проверьте настройки почты."); }
    }
}
