package bibid.dto;

import bibid.entity.Chat;
import bibid.entity.ChatRoom;
import bibid.entity.Member;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import lombok.*;

import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ChatDto {
    private Long chatIndex;

    private Long chatRoomIndex;

    private String chatMessage;
    private int participantCount;
    private String participantStatus;
    private Long senderIndex;
    private String senderNickname;
    private LocalDateTime sendTime;

    public Chat toEntity(ChatRoom chatRoom, Member sender) {
        return Chat.builder()
                .ChatIndex(this.chatIndex)
                .chatRoom(chatRoom)
                .chatMessage(this.chatMessage)
                .sender(sender)
                .sendTime(this.sendTime)
                .build();
    }
}
