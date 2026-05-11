package com.example.agent.service;

import com.example.agent.dto.AuthResponse;
import com.example.agent.dto.LoginRequest;
import com.example.agent.dto.RegisterRequest;
import com.example.agent.entity.User;
import com.example.agent.repository.UserRepository;
import com.example.agent.security.UserDetailsAdapter;
import com.example.agent.security.JwtService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final AuthenticationManager authenticationManager;

    public AuthResponse register(RegisterRequest request) {
        if (userRepository.existsByLogin(request.login())) {
            throw new IllegalArgumentException("Login already taken");
        }
        User user = new User();
        user.setLogin(request.login());
        user.setPassword(passwordEncoder.encode(request.password()));
        userRepository.save(user);
        String token = jwtService.generateToken(new UserDetailsAdapter(user));
        return new AuthResponse(token);
    }

    public AuthResponse login(LoginRequest request) {
        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.login(), request.password())
        );
        User user = userRepository.findByLogin(request.login()).orElseThrow();
        String token = jwtService.generateToken(new UserDetailsAdapter(user));
        return new AuthResponse(token);
    }
}
