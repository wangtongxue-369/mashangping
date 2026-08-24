package com.mashangping.security;

/** JWT 载荷：uid/username/role */
public record TokenPayload(long uid, String username, String role) {}
