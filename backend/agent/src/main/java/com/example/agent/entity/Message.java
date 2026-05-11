package com.example.agent.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Getter
@Setter
@NoArgsConstructor
public class Message {
    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE)
    private Long id;
    private String text;
    @Enumerated(EnumType.STRING)
    private SenderType sender;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "chat_id")
    private Chat chat;


}
