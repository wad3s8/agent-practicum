package com.example.agent.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.List;

@Entity
@Getter
@Setter
@NoArgsConstructor
public class Chat {
    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User user;

    @OneToMany(fetch = FetchType.LAZY, mappedBy = "chat", cascade = CascadeType.ALL)
    private List<Message> messages;

    private Instant createdAt;

    public void addMessage(Message message){
        messages.add(message);
        message.setChat(this);
    }

    public void removeMessage(Message message){
        messages.remove(message);
        message.setChat(null);
    }
}
