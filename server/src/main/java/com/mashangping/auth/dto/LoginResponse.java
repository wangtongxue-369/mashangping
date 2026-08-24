package com.mashangping.auth.dto;

import com.mashangping.user.dto.UserView;

public record LoginResponse(String token, UserView user) {}
