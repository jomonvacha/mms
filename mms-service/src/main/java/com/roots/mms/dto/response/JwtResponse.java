package com.roots.mms.dto.response;

import java.util.List;

public class JwtResponse {

    private String token;
    private String type = "Bearer";
    private String refreshToken;
    private String id;
    private String username;
    private String email;
    private List<String> roles;

    public JwtResponse(String accessToken, String refreshToken, String id,
                       String username, String email, List<String> roles) {
        this.token = accessToken;
        this.refreshToken = refreshToken;
        this.id = id;
        this.username = username;
        this.email = email;
        this.roles = roles;
    }

    public String getAccessToken()   { return token; }
    public void setAccessToken(String v) { this.token = v; }
    public String getTokenType()     { return type; }
    public void setTokenType(String v)   { this.type = v; }
    public String getRefreshToken()  { return refreshToken; }
    public void setRefreshToken(String v) { this.refreshToken = v; }
    public String getId()            { return id; }
    public void setId(String v)      { this.id = v; }
    public String getEmail()         { return email; }
    public void setEmail(String v)   { this.email = v; }
    public String getUsername()      { return username; }
    public void setUsername(String v){ this.username = v; }
    public List<String> getRoles()   { return roles; }
    public void setRoles(List<String> v) { this.roles = v; }
}
