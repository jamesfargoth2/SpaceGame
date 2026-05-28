package com.galacticodyssey.common.protocol;

public class LoginRequest extends NetworkMessage {
    public String username;
    public String clientVersion;

    public LoginRequest() {}
}
