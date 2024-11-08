package bibid.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;

import java.util.Properties;

@Configuration
public class MailConfig {

    @Value("${send.mail.address}")
    private String sendMailAddress;

    @Value("${send.mail.passwd}")
    private String sendMailPasswd;

    // SMTP 설정
    @Bean
    public JavaMailSender MailSender() {
        JavaMailSenderImpl mailSender = new JavaMailSenderImpl();
        mailSender.setHost("smtp.gmail.com");
        mailSender.setPort(587);

        // 고정된 이메일 주소와 비밀번호 사용
        mailSender.setUsername(sendMailAddress); // SMTP 인증용 이메일
        mailSender.setPassword(sendMailPasswd); // SMTP 인증용 비밀번호

        Properties props = mailSender.getJavaMailProperties();
        props.put("mail.transport.protocol", "smtp");
        props.put("mail.smtp.auth", "true");
        props.put("mail.smtp.starttls.enable", "true");
        props.put("mail.debug", "true");

        return mailSender;
    }

}
