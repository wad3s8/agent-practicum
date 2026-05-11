package com.example.agent.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import java.util.List;

@Table(name = "users")
@Entity
@NoArgsConstructor
@Setter
@Getter
public class User {
    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE)
    private Long id;
    private String login;
    private String password;

    @OneToMany(mappedBy = "user", cascade = CascadeType.ALL,  fetch = FetchType.LAZY)
    private List<Chat> chats;

    public void addChat(Chat chat){
        chats.add(chat);
        chat.setUser(this);
    }

    public void removeChat(Chat chat){
        chats.remove(chat);
        chat.setUser(null);
    }
}
